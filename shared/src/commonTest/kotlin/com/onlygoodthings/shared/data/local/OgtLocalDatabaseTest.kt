package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.AnimalListingDto
import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.OgtCrmDefaults
import com.onlygoodthings.shared.domain.FeedMode
import com.onlygoodthings.shared.domain.HonorChannel
import com.onlygoodthings.shared.domain.HonorMentionView
import com.onlygoodthings.shared.domain.HonorStatus
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostMediaRules
import com.onlygoodthings.shared.domain.PostPersonRole
import com.onlygoodthings.shared.domain.SocialComment
import com.onlygoodthings.shared.domain.SocialLiveCounters
import com.onlygoodthings.shared.realtime.gatewayEndpointFromApiBase
import com.onlygoodthings.shared.realtime.socialCommentFrom
import com.onlygoodthings.shared.realtime.socialLiveCountersFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OgtLocalDatabaseTest {
    @Test
    fun seedMantieneIntegridadYPersonajesStitch() {
        val db = OgtLocalDatabase.seeded()
        val me = db.user(OgtIds.Mariana)
        assertEquals(2450, me.communityPoints)
        assertEquals("BUENOSVECINOS-MC", me.inviteCode)
        assertTrue(db.feedPosts().isNotEmpty())
        assertEquals(14, db.post(OgtIds.PostHomenaje).heartCount)
        assertTrue(db.commentsOf(OgtIds.PostTaller).size >= 2)
        assertEquals(OgtIds.Lucas, db.parkingSpots.first { it.id == OgtIds.SpotCorrientes }.ownerUserId)
        assertEquals(98, db.match(OgtIds.MatchSofia).matchPercent)
        db.posts.forEach { post ->
            when {
                post.authorUserId != null -> db.user(post.authorUserId)
                post.authorCompanyId != null -> db.company(post.authorCompanyId)
            }
        }
        db.comments.forEach { db.user(it.authorUserId) }
        assertEquals(1, db.rankOf(OgtIds.Sofia))
        assertTrue(db.rankOf(OgtIds.Mariana) > 1)
    }

    @Test
    fun homeIncluyeDescubrimientoYFollowingSoloGrafo() {
        val db = OgtLocalDatabase.seeded()
        val home = db.rankedFeed(OgtIds.Mariana, FeedMode.HOME, limit = 200)
        val following = db.rankedFeed(OgtIds.Mariana, FeedMode.FOLLOWING, limit = 200)
        assertTrue(home.any { it.id == OgtIds.PostAnaComedor }, "HOME debe difundir a Ana aunque Mariana no la siga")
        assertTrue(home.any { it.id == OgtIds.PostAcmeRse })
        assertTrue(following.none { it.id == OgtIds.PostAnaComedor })
        assertTrue(following.none { it.id == OgtIds.PostAcmeRse })
        assertTrue(following.any { it.id == OgtIds.PostPlaya })
        val onlyFood = db.rankedFeed(
            OgtIds.Mariana,
            FeedMode.HOME,
            limit = 80,
            family = com.onlygoodthings.shared.domain.FeedTopicFamily.FOOD,
        )
        assertTrue(onlyFood.any { it.id == OgtIds.PostAnaComedor })
        assertTrue(onlyFood.none { it.tag.contains("Adopción", ignoreCase = true) })
        assertTrue(db.isDiscovery(OgtIds.Mariana, db.post(OgtIds.PostAnaComedor)))
        assertTrue(!db.isDiscovery(OgtIds.Mariana, db.post(OgtIds.PostPlaya)))
    }

    @Test
    fun cadaPostTieneCarruselInstagramYHayCuarentaNoticias() {
        val db = OgtLocalDatabase.seeded()
        assertEquals(40, db.posts.count { it.id.startsWith("news-") })
        assertEquals(100, db.posts.count { it.id.startsWith("pet-") })
        assertTrue(db.posts.filter { it.id.startsWith("pet-") }.count { !it.sourceUrl.isNullOrBlank() } >= 5)
        assertTrue(db.posts.filter { it.id.startsWith("pet-") }.count { it.sourceUrl.isNullOrBlank() } >= 80)
        assertEquals(OgtIds.AnimalLuna, db.post(OgtIds.PostLuna).listingId)
        assertEquals(OgtIds.AnimalOliver, db.post(OgtIds.PostOliver).listingId)
        assertEquals(OgtIds.AnimalGrisu, db.post(OgtIds.PostGrisu).listingId)
        assertEquals(OgtIds.AnimalCoco, db.post(OgtIds.PostCoco).listingId)
        assertTrue(
            db.animals.map { it.species }.containsAll(
                listOf("DOG", "CAT", "BIRD", "RABBIT", "HAMSTER", "TURTLE"),
            ),
        )
        db.feedPosts().forEach { post ->
            val media = db.mediaOf(post.id)
            assertTrue(PostMediaRules.isValid(media.size), "Post ${post.id} debe tener 1..10 media")
        }
        assertTrue(db.postMedia.any { it.kind == MediaKind.VIDEO })
        assertTrue(db.feedPosts().any { db.mediaOf(it.id).size >= 2 })
        assertTrue(db.posts.any { !it.sourceUrl.isNullOrBlank() })
    }

    @Test
    fun seguirYDejarDeSeguirActualizaElGrafo() {
        val db = OgtLocalDatabase.seeded()
        assertTrue(db.isFollowing(OgtIds.Mariana, OgtIds.ValeriaP))
        db.toggleFollow(OgtIds.Mariana, OgtIds.ValeriaP)
        assertTrue(!db.isFollowing(OgtIds.Mariana, OgtIds.ValeriaP))
        db.toggleFollow(OgtIds.Mariana, OgtIds.ValeriaP)
        assertTrue(db.isFollowing(OgtIds.Mariana, OgtIds.ValeriaP))
        assertTrue(db.postsOfAuthor(OgtIds.ValeriaP).isNotEmpty())
    }

    @Test
    fun honorSeEmiteYSeReivindicaSinCrearUsuarioFantasma() {
        val db = OgtLocalDatabase.seeded()
        val before = db.users.size
        val honor = db.issueHonor(OgtIds.Mariana, "Ana Pérez", HonorChannel.EMAIL, "ana@casa.org")
        assertEquals(before, db.users.size)
        assertEquals(HonorStatus.PENDING, honor.status)
        db.attachHonorToPost("post-honor-1", honor.id, PostPersonRole.PROTAGONIST)
        val claimed = db.claimHonor(honor.claimToken, OgtIds.Sofia)
        assertEquals(HonorStatus.CLAIMED, claimed?.status)
        assertEquals(OgtIds.Sofia, claimed?.claimedUserId)
        assertTrue(db.postPeople.any { it.postId == "post-honor-1" && it.userId == OgtIds.Sofia && it.role == PostPersonRole.PROTAGONIST })
        assertNull(db.claimHonor(honor.claimToken, OgtIds.Camila))
        val match = db.pendingHonorForUser(db.user(OgtIds.Sofia).copy(email = "ana@casa.org"))
        assertNull(match)
    }

    @Test
    fun honorPendienteMatcheaContactoDeQuienEntra() {
        val db = OgtLocalDatabase.seeded()
        db.issueHonor(OgtIds.Mariana, "Sofía", HonorChannel.EMAIL, "sofia.m@email.com")
        val pending = db.pendingHonorForUser(db.user(OgtIds.Sofia))
        assertEquals("Sofía", pending?.givenName)
        assertNull(db.pendingHonorForUser(db.user(OgtIds.Mariana).copy(email = "sofia.m@email.com")))
    }

    @Test
    fun honorSeExportaYSeReimporta() {
        val db = OgtLocalDatabase.seeded()
        val honor = db.issueHonor(OgtIds.Mariana, "Ana Pérez", HonorChannel.EMAIL, "ana@casa.org")
        db.attachHonorToPost(OgtIds.PostTaller, honor.id, PostPersonRole.PROTAGONIST)
        val other = OgtLocalDatabase.seeded()
        other.importHonors(db.exportHonors())
        assertEquals(honor.claimToken, other.honorByToken(honor.claimToken)?.claimToken)
        assertEquals("Ana Pérez", other.creditName(other.post(OgtIds.PostTaller)))
    }

    @Test
    fun mencionCreaAvisoSoloAlReferenciado() {
        val db = OgtLocalDatabase.seeded()
        val first = db.notifyMentionedUsers(OgtIds.Mariana, "post-mention-1", listOf(OgtIds.Camila, OgtIds.Mariana, OgtIds.Camila))
        assertEquals(1, first.size)
        assertEquals(OgtIds.Camila, first.single().recipientUserId)
        assertEquals("MENTION", first.single().kind)
        assertEquals("ogt://p/post-mention-1", first.single().deepLink)
        assertTrue(db.inboxOf(OgtIds.Camila).any { it.id == first.single().id })
        assertTrue(db.inboxOf(OgtIds.Mariana).none { it.kind == "MENTION" })
        val again = db.notifyMentionedUsers(OgtIds.Mariana, "post-mention-1", listOf(OgtIds.Camila))
        assertTrue(again.isEmpty())
        db.markNoticeRead(first.single().id)
        assertTrue(db.inboxOf(OgtIds.Camila).first { it.id == first.single().id }.read)
    }

    @Test
    fun elRioAnteponeLoQueAcaboDePublicar() {
        val db = OgtLocalDatabase.seeded()
        val now = 9_000_000_000_001L
        db.ingestPublishedPost(
            LocalSocialPost(
                id = "post-pub-$now",
                authorKind = AuthorKind.USER,
                authorUserId = OgtIds.Mariana,
                authorCompanyId = null,
                place = "Palermo",
                timeLabel = "Ahora",
                tag = "Gracias",
                body = "Gracias, Don Héctor.",
                impactCount = 0,
                commentCount = 0,
                isStory = false,
                storyLabel = null,
                createdAtEpochMs = now,
            ),
        )
        assertEquals("post-pub-$now", db.visibleFeed(OgtIds.Mariana, nowEpochMs = now).first().id)
        assertTrue(db.feedTick.value > 0)
    }

    @Test
    fun followingNoMuestraUnPostAjenoFueraDelGrafo() {
        val db = OgtLocalDatabase.seeded()
        val stranger = db.user(OgtIds.Mariana).copy(
            id = "user-extraña",
            firebaseUid = "user-extraña",
            displayName = "Nerea Extraña",
        )
        db.users += stranger
        val now = 9_000_000_000_002L
        val post = LocalSocialPost(
            id = "post-pub-extraño",
            authorKind = AuthorKind.USER,
            authorUserId = stranger.id,
            authorCompanyId = null,
            place = "Palermo",
            timeLabel = "Ahora",
            tag = "Huerta",
            body = "Plantamos tipas.",
            impactCount = 0,
            commentCount = 0,
            isStory = false,
            storyLabel = null,
            createdAtEpochMs = now,
        )
        db.ingestPublishedPost(post)
        assertTrue(!db.shouldShowInFeed(OgtIds.Mariana, post, FeedMode.FOLLOWING, nowEpochMs = now))
        assertTrue(db.visibleFeed(OgtIds.Mariana, FeedMode.FOLLOWING, nowEpochMs = now).none { it.id == post.id })
    }

    @Test
    fun socketIngresaUnPostNuevoSiTraeCuerpo() {
        val db = OgtLocalDatabase.seeded()
        val live = SocialLiveCounters(
            id = "post-live-nuevo",
            commentCount = 0,
            impactCount = 1,
            authorUserId = OgtIds.Sofia,
            body = "Vimos a Luna en la plaza.",
            tag = "Ternura",
            place = "Palermo",
            createdAtEpochMs = 9_000_000_000_003L,
        )
        assertTrue(db.applySocialLive(live))
        assertEquals("Vimos a Luna en la plaza.", db.post("post-live-nuevo").body)
        assertTrue(db.shouldShowInFeed(OgtIds.Sofia, db.post("post-live-nuevo"), nowEpochMs = live.createdAtEpochMs))
    }

    @Test
    fun homenajeConSoloElNombreNoQuedaComoInvitacionPendiente() {
        val db = OgtLocalDatabase.seeded()
        val post = LocalSocialPost(
            id = "post-pub-memoria",
            authorKind = AuthorKind.USER,
            authorUserId = OgtIds.Mariana,
            authorCompanyId = null,
            place = "Palermo",
            timeLabel = "Ahora",
            tag = "Post mortem",
            body = "Se extraña su risa en el patio.",
            impactCount = 0,
            commentCount = 0,
            isStory = false,
            storyLabel = null,
            honoreeName = "Don Héctor",
        )
        db.posts += post
        assertEquals("Don Héctor", db.creditName(post))
        assertTrue(!db.creditPending(post))
    }

    @Test
    fun honorRemotoSeHidrataPorToken() {
        val db = OgtLocalDatabase.seeded()
        val view = HonorMentionView(
            id = "honor-remote-1",
            givenName = "Ana Pérez",
            issuerName = "Mariana Cordero",
            status = HonorStatus.PENDING,
            claimToken = "ana.remoto1",
        )
        val row = db.upsertHonorFromRemote(view)
        assertEquals("Ana Pérez", row.givenName)
        assertEquals("Mariana Cordero", db.honorByToken("ana.remoto1")?.issuerName)
    }

    @Test
    fun intercambioDeAyudaPideYOfreceSinHorario() {
        val db = OgtLocalDatabase.seeded()
        assertNull(db.publishHelpExchange(OgtIds.Mariana, "  ", "Arreglar la bici"))
        assertNull(db.publishHelpExchange(OgtIds.Mariana, "Bajar un mueble", " "))
        val row = db.publishHelpExchange(
            OgtIds.Mariana,
            "Bajar un mueble",
            "Arreglar la bici",
            "Cuando puedan, lo hablamos en el chat.",
        )
        assertEquals("Bajar un mueble", row?.need)
        assertEquals("Arreglar la bici", row?.give)
        assertEquals(OgtIds.Mariana, db.helpExchangesOf(OgtIds.Mariana).first().authorUserId)
        val feedPost = db.visibleFeed(OgtIds.Mariana, nowEpochMs = row!!.createdAtEpochMs).first()
        assertEquals("Intercambio de Ayuda", feedPost.tag)
        assertTrue(feedPost.body.contains("Bajar un mueble"))
    }

    @Test
    fun avisoPerdidoPideSenasYUltimaVista() {
        val db = OgtLocalDatabase.seeded()
        val me = db.user(OgtIds.Mariana)
        assertNull(
            db.publishAnimalListing(me, "LOST", "Oliver", "DOG", "LARGE", "Mestizo grande", "Palermo"),
        )
        val post = db.publishAnimalListing(
            author = me,
            kind = "LOST",
            petName = "Oliver",
            species = "DOG",
            size = "LARGE",
            description = "Mestizo grande. Responde a su nombre.",
            place = "Palermo",
            marks = "Collar rojo, oreja caída",
            lastSeenPlace = "Plaza Serrano",
        )
        assertEquals("Mascota perdida", post?.tag)
        val listing = db.animals.first { it.id == post!!.listingId }
        assertEquals("Oliver", listing.petName)
        assertEquals("LOST", listing.kind)
        assertEquals("Plaza Serrano", listing.lastSeenPlace)
        assertEquals(OgtCrmDefaults.LOST_ALERT_RADIUS_M, listing.alertRadiusM)
    }

    @Test
    fun avisoAdopcionPideEdadCaracterYBarrio() {
        val db = OgtLocalDatabase.seeded()
        val me = db.user(OgtIds.Mariana)
        assertNull(
            db.publishAnimalListing(me, "ADOPTION", "Luna", "DOG", "SMALL", "Cariñosa", ""),
        )
        val post = db.publishAnimalListing(
            author = me,
            kind = "ADOPTION",
            petName = "Luna",
            species = "DOG",
            size = "SMALL",
            description = "Cachorra mestiza rescatada.",
            place = "Parque Centenario",
            ageLabel = "6 meses",
            sex = "HEMBRA",
            temperament = "Cariñosa y sociable",
            vaccinated = true,
            homeNeeds = "Alguien en casa varias horas",
        )
        assertEquals("Adopción", post?.tag)
        val listing = db.animals.first { it.id == post!!.listingId }
        assertEquals("ADOPTION", listing.kind)
        assertEquals("6 meses", listing.ageLabel)
        assertEquals("Cariñosa y sociable", listing.temperament)
        assertTrue(listing.vaccinated)
        assertEquals(0, listing.alertRadiusM)
    }

    @Test
    fun elAutorPuedeEditarSuAdopcionYOtroNo() {
        val db = OgtLocalDatabase.seeded()
        val me = db.user(OgtIds.Mariana)
        val post = db.publishAnimalListing(
            author = me,
            kind = "ADOPTION",
            petName = "Luna",
            species = "DOG",
            size = "SMALL",
            description = "Cachorra mestiza rescatada.",
            place = "Parque Centenario",
            ageLabel = "6 meses",
            temperament = "Cariñosa y sociable",
        )!!
        val otro = db.users.first { it.id != me.id }
        assertNull(
            db.updateAnimalListing(
                author = otro,
                postId = post.id,
                petName = "Luna",
                species = "DOG",
                size = "SMALL",
                description = "No debería guardar.",
                place = "Palermo",
                ageLabel = "1 año",
                temperament = "Otro carácter",
            ),
        )
        val updated = db.updateAnimalListing(
            author = me,
            postId = post.id,
            petName = "Luna",
            species = "DOG",
            size = "MEDIUM",
            description = "Ya está más grande y sigue cariñosa.",
            place = "Villa Crespo",
            ageLabel = "1 año",
            sex = "HEMBRA",
            temperament = "Cariñosa, ya no miedosa",
            vaccinated = true,
            sterilized = true,
            homeNeeds = "Patio",
        )
        assertEquals("Ya está más grande y sigue cariñosa.", updated?.body)
        val listing = db.animals.first { it.id == post.listingId }
        assertEquals("Luna · 1 año", listing.title)
        assertEquals("MEDIUM", listing.size)
        assertEquals("Villa Crespo", listing.place)
        assertEquals("Patio", listing.homeNeeds)
        assertTrue(listing.sterilized)
    }

    @Test
    fun bindServerAnimalCambiaIdsYMantieneAlAutorLocal() {
        val db = OgtLocalDatabase.seeded()
        val me = db.user(OgtIds.Mariana)
        val local = db.publishAnimalListing(
            author = me,
            kind = "ADOPTION",
            petName = "Luna",
            species = "DOG",
            size = "SMALL",
            description = "Cachorra mestiza rescatada.",
            place = "Parque Centenario",
            ageLabel = "6 meses",
            temperament = "Cariñosa y sociable",
        )!!
        val remote = AnimalListingDto(
            listingId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            postId = "ffffffff-1111-2222-3333-444444444444",
            reporterUserId = "99999999-9999-9999-9999-999999999999",
            reporterFirebaseUid = me.firebaseUid,
            kind = "ADOPTION",
            species = "DOG",
            size = "SMALL",
            urgency = "LOW",
            title = "Luna · 6 meses",
            description = "Cachorra mestiza rescatada.",
            place = "Parque Centenario",
            alertRadiusM = 0,
            resolved = false,
            petName = "Luna",
            ageLabel = "6 meses",
            temperament = "Cariñosa y sociable",
            createdAtEpochMs = local.createdAtEpochMs,
        )
        db.bindServerAnimal(local.id, remote, me.id)
        assertTrue(db.posts.none { it.id == local.id })
        val bound = db.post(remote.postId)
        assertEquals(me.id, bound.authorUserId)
        assertEquals(remote.listingId, bound.listingId)
        assertEquals(me.id, db.animals.first { it.id == remote.listingId }.reporterUserId)
    }

    @Test
    fun exportaEImportaAdopcionPublicadaAlReabrir() {
        val db = OgtLocalDatabase.seeded()
        val me = db.user(OgtIds.Mariana)
        val post = db.publishAnimalListing(
            author = me,
            kind = "ADOPTION",
            petName = "Canela",
            species = "DOG",
            size = "SMALL",
            description = "Cachorra mestiza rescatada.",
            place = "Parque Centenario",
            ageLabel = "6 meses",
            temperament = "Cariñosa y sociable",
        )!!
        val raw = db.exportPublishedAnimals()
        val other = OgtLocalDatabase.seeded()
        other.importPublishedAnimals(raw)
        assertTrue(other.animals.any { it.id == post.listingId && it.petName == "Canela" })
        assertTrue(other.posts.any { it.id == post.id && it.listingId == post.listingId })
        val remote = AnimalListingDto(
            listingId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            postId = "ffffffff-1111-2222-3333-444444444444",
            reporterUserId = "99999999-9999-9999-9999-999999999999",
            reporterFirebaseUid = me.firebaseUid,
            kind = "ADOPTION",
            species = "DOG",
            size = "SMALL",
            urgency = "LOW",
            title = "Canela · 6 meses",
            description = "Cachorra mestiza rescatada.",
            place = "Parque Centenario",
            alertRadiusM = 0,
            resolved = false,
            petName = "Canela",
            ageLabel = "6 meses",
            temperament = "Cariñosa y sociable",
            createdAtEpochMs = post.createdAtEpochMs,
        )
        other.upsertRemoteAnimal(remote)
        assertTrue(other.animals.none { it.id == post.listingId })
        assertTrue(other.animals.any { it.id == remote.listingId && it.petName == "Canela" })
    }

    @Test
    fun adopcionPublicadaSigueSiendoMiaSiElIdLocalCambia() {
        val db = OgtLocalDatabase.seeded()
        val firebaseUid = "H3wjp0Ok7Fcyg0SBioiWEyhTjTY2"
        val signedIn = db.user(OgtIds.Mariana).copy(id = firebaseUid, firebaseUid = firebaseUid)
        db.users.add(signedIn)
        val post = db.publishAnimalListing(
            author = signedIn,
            kind = "ADOPTION",
            petName = "Lenteja",
            species = "TURTLE",
            size = "SMALL",
            description = "Tortuga de la comunidad.",
            place = "Palermo",
            ageLabel = "3 años",
            temperament = "Calma",
        )!!
        val raw = db.exportPublishedAnimals()
        val reopened = OgtLocalDatabase.seeded()
        reopened.importPublishedAnimals(raw)
        val sessionUser = signedIn.copy(id = "uuid-de-postgres")
        reopened.users.add(sessionUser)
        reopened.claimPublishedAnimals(setOf(firebaseUid, sessionUser.id), sessionUser.id)
        assertTrue(sessionUser.owns(reopened.animals.first { it.petName == "Lenteja" }.reporterUserId))
        val feed = reopened.visibleFeed(sessionUser.id)
        assertTrue(feed.any { it.id == post.id && it.listingId == post.listingId })
    }

    @Test
    fun comentarioNuevoSubeElContadorYElTickSocial() {
        val db = OgtLocalDatabase.seeded()
        val post = db.post(OgtIds.PostTaller)
        val before = post.commentCount
        val tick = db.socialTick.value
        val row = db.addComment(db.user(OgtIds.Mariana), post.id, "Gracias")
        assertTrue(row != null)
        assertEquals(before + 1, db.post(post.id).commentCount)
        assertEquals(tick + 1, db.socialTick.value)
    }

    @Test
    fun socketSocialActualizaContadoresSinDuplicar() {
        val db = OgtLocalDatabase.seeded()
        val post = db.post(OgtIds.PostTaller)
        val live = SocialLiveCounters(
            id = post.id,
            commentCount = post.commentCount + 3,
            impactCount = post.impactCount + 1,
            heartCount = post.heartCount + 2,
        )
        assertTrue(db.applySocialLive(live))
        assertEquals(post.commentCount + 3, db.post(post.id).commentCount)
        assertTrue(!db.applySocialLive(live))
        val remote = SocialComment(
            id = "c-live-1",
            postId = post.id,
            authorUserId = OgtIds.Sofia,
            authorName = "Sofía",
            parentCommentId = null,
            body = "Sumo una mano",
            createdAtEpochMs = 1L,
        )
        assertTrue(db.applySocialComment(remote))
        assertTrue(!db.applySocialComment(remote))
        assertTrue(db.commentsOf(post.id).any { it.id == "c-live-1" })
    }

    @Test
    fun parseaContadoresYComentariosDelArbolSocial() {
        val live = socialLiveCountersFrom(
            mapOf("id" to "p1", "commentCount" to 4, "impactCount" to 2, "heartCount" to 1),
            key = "p1",
        )
        assertEquals(4, live?.commentCount)
        assertEquals(2, live?.impactCount)
        val comment = socialCommentFrom(
            mapOf("id" to "c1", "authorUserId" to "u1", "body" to "hola", "postId" to "p1"),
            key = "c1",
            fallbackPostId = "p1",
        )
        assertEquals("hola", comment?.body)
        assertEquals("ws://127.0.0.1:8080/db", gatewayEndpointFromApiBase("http://127.0.0.1:8080"))
        assertEquals("wss://api.onlygoodthings.lat/db", gatewayEndpointFromApiBase("https://api.onlygoodthings.lat"))
    }
}
