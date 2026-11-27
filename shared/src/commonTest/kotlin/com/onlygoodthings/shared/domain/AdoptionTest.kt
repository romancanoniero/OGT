package com.onlygoodthings.shared.domain

import com.onlygoodthings.shared.data.local.*
import com.onlygoodthings.shared.protocol.WsCodec
import kotlin.test.*

class AdoptionTest {
    private val cat = AdoptionTag("cat", "Gatos", "gatos", "SPECIES", "CAT")
    private val female = AdoptionTag("female", "Hembra", "hembra", "SEX", "HEMBRA")
    private val male = AdoptionTag("male", "Macho", "macho", "SEX", "MACHO")
    private val all = AdoptionTag("all", "Vacunados", "vacunados", "VACCINATION", "ALL")
    private val some = AdoptionTag("some", "Algunos vacunados", "algunosvacunados", "VACCINATION", "SOME")
    private val catalog = listOf(cat, female, male, all, some)

    @Test fun groupSupportsSeveralSexesAndHealthOnlyAppliesToTheChosenScope() {
        val draft = AdoptionDetails(tags = listOf(cat, female, male, some), freeHashtags = listOf("#HogarResponsable"), adoptionMode = "SEPARATE", availableCount = 3)
        val saved = AdoptionRules.normalize(draft, catalog)
        assertEquals(2, saved.tags.count { it.group == "SEX" })
        assertEquals("", AdoptionRules.singleValue(saved, "SEX"))
        assertEquals("SOME", AdoptionRules.singleValue(saved, "VACCINATION"))
        assertEquals(listOf(cat, female, male, some), AdoptionRules.toggle(listOf(cat, female, male, all), some))
        assertTrue(AdoptionRules.hashtags(saved).any { it.key == "hogarresponsable" })
        assertEquals("", saved.historyAndCare)
    }

    @Test fun catalogControlsMeaningAndInactiveTraitsCanOnlyBeRetainedByTheirExistingListing() {
        val disabled = some.copy(active = false)
        val forged = disabled.copy(label = "Vacunados", legacyValue = "ALL", active = true)
        assertFailsWith<IllegalArgumentException> { AdoptionRules.normalize(AdoptionDetails(tags = listOf(cat, forged)), listOf(cat, disabled)) }
        val kept = AdoptionRules.normalize(AdoptionDetails(tags = listOf(cat, forged)), listOf(cat, disabled), setOf(disabled.id))
        assertEquals(disabled, kept.tags.last())
        assertFailsWith<IllegalArgumentException> { AdoptionRules.normalize(AdoptionDetails(tags = listOf(cat), freeHashtags = listOf(disabled.label)), listOf(cat, disabled)) }
        assertFailsWith<IllegalArgumentException> { AdoptionRules.normalize(AdoptionDetails(tags = listOf(cat, all, some)), catalog) }
    }

    @Test fun freeTagsDeduplicateAgainstOfficialOnesAndInvalidCountsDoNotReachPublication() {
        val result = AdoptionRules.normalize(AdoptionDetails(tags = listOf(cat), freeHashtags = listOf("#Vacunados", "Hogar", "#hogar")), catalog)
        assertEquals(listOf(cat, all), result.tags)
        assertEquals(listOf("Hogar"), result.freeHashtags)
        for (count in listOf(0, -1, 101)) assertFailsWith<IllegalArgumentException> { AdoptionRules.normalize(result.copy(availableCount = count), catalog) }
        assertFailsWith<IllegalArgumentException> { AdoptionRules.normalize(result.copy(tags = emptyList()), catalog) }
        assertFailsWith<IllegalArgumentException> { AdoptionRules.normalize(result.copy(historyAndCare = "x".repeat(2001)), catalog) }
    }

    @Test fun historicalUncheckedHealthIsNotReinterpretedAsUnvaccinatedOrUnsterilized() {
        assertEquals(listOf(cat, female), AdoptionRules.legacyTags(catalog, "CAT", null, "HEMBRA", false, false))
        assertEquals(listOf(cat, all), AdoptionRules.legacyTags(catalog, "CAT", "", "", true, false))
    }

    @Test fun optionalFieldsAndFullTitleSurviveDeviceStorageHydrationAndEditingWithoutResettingCounters() {
        val db = OgtLocalDatabase()
        val me = LocalUser("owner", "firebase-owner", null, null, "Persona QA", null, UserRole.USER, 0, "QA", "Barrio", "", "", -34.6, -58.4)
        db.users += me
        val details = AdoptionDetails(tags = listOf(cat, female, male, some), availableCount = 3)
        val title = "Tres HERMANITOS buscan hogar 🐾"
        val post = db.publishAnimalListing(me, "ADOPTION", "Hermanitos", "CAT", "", "Presentación", "Barrio",
            title = title, adoptionDetails = details)!!
        val original = db.animals.single()
        assertEquals(title, original.displayPetName());assertEquals("", original.ageLabel);assertEquals("", original.temperament)
        assertEquals(original, WsCodec.json.decodeFromString(LocalAnimalListing.serializer(), WsCodec.json.encodeToString(LocalAnimalListing.serializer(), original)))
        db.posts[0] = db.posts[0].copy(impactCount = 4, commentCount = 2)
        val edited = db.updateAnimalListing(me, post.id, "Hermanitos", "CAT", "", "Presentación editada", "Otro barrio",
            title = title, adoptionDetails = details.copy(availableCount = 2))!!
        assertEquals(4, edited.impactCount);assertEquals(2, edited.commentCount)
        assertEquals(original.createdAtEpochMs, db.animals.single().createdAtEpochMs)
        assertEquals(original.description, db.animals.single().description)
        assertEquals("Presentación editada",db.animals.single().adoptionDetails!!.presentation)
        assertEquals("Presentación editada",edited.body)
        val remote = AnimalListingDto("listing", "remote-post", me.id, me.firebaseUid, "ADOPTION", "CAT", "", "LOW", title,
            "Presentación remota", "Barrio remoto", 0, false, adoptionDetails = details)
        db.upsertRemoteSocial(SocialPost("remote-post", AuthorKind.USER, me.id, me.displayName, body = remote.description, mediaUrls = emptyList(), impactCount = 6,
            commentCount = 3, isStory = false, createdAtEpochMs = 123, topic = "Adopción", animalListing = remote))
        assertEquals(title, db.animals.first { it.id == remote.listingId }.displayPetName())
        assertEquals("Barrio remoto", db.post(remote.postId)!!.place)
        val socialBefore=db.socialTick.value
        db.upsertRemoteSocial(SocialPost("remote-post", AuthorKind.USER, me.id, me.displayName, body = remote.description, mediaUrls = emptyList(), impactCount = 6,
            commentCount = 3, isStory = false, createdAtEpochMs = 123, topic = "Adopción", animalListing = remote),silent=true)
        assertEquals(socialBefore,db.socialTick.value,"Identical metadata must not trigger new social recompositions")
        val remoteIndex=db.posts.indexOfFirst { it.id==remote.postId }
        db.posts[remoteIndex]=db.posts[remoteIndex].copy(viewerHasImpacted=true,viewerHasHearted=true,heartCount=1)
        db.upsertRemoteAnimal(remote.copy(resolved=true))
        val closed=db.post(remote.postId)
        assertTrue(closed.viewerHasImpacted);assertTrue(closed.viewerHasHearted)
        assertEquals(6,closed.impactCount);assertEquals(3,closed.commentCount);assertEquals(1,closed.heartCount)
    }
}
