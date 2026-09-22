package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.FeedEventKind
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.ParkingStatus
import com.onlygoodthings.shared.domain.PostPersonRole
import com.onlygoodthings.shared.domain.UserRole
import com.onlygoodthings.shared.realtime.currentEpochMs

/**
 * Mezcla el seed SQL (`02_demo_seed.sql`) con los personajes de Stitch
 * para poder recorrer la app sin backend.
 */
object OgtMockSeed {
    fun populate(db: OgtLocalDatabase): OgtLocalDatabase {
        db.users += listOf(
            user(OgtIds.Mariana, "dev-user-mariana", "mariana.c@email.com", "Mariana Cordero", 2450, "BUENOSVECINOS-MC", "Palermo Soho", "Nivel 4 · Guardián Comunitario", "7 árboles donados", -34.5888, -58.4302),
            user(OgtIds.Ana, "dev-user-ana", "ana@onlygoodthings.test", "Ana Pérez", 120, "ANA-GOOD-01", "Microcentro", "Nivel 1 · Comunidad", "Comedor Balvanera", -34.6037, -58.3816),
            user(OgtIds.Bruno, "dev-user-bruno", "bruno@onlygoodthings.test", "Bruno Díaz", 40, "BRU-GOOD-02", "Congreso", "Nivel 1 · Comunidad", "Parking 9 de Julio", -34.6090, -58.3920),
            user(OgtIds.Carla, "dev-mod-carla", "carla@onlygoodthings.test", "Carla Gómez", 300, "CAR-MOD-03", "Palermo", "Moderadora", "Techo del refugio", -34.5880, -58.4000, UserRole.COMMUNITY_MODERATOR),
            user(OgtIds.Diego, "dev-admin-diego", "diego@acme-rse.test", "Diego RSE", 10, "DIE-RSE-04", "Puerto Madero", "Admin RSE", "Acme Solidaridad", -34.6010, -58.3700, UserRole.COMPANY_ADMIN),
            user(OgtIds.Lucas, "dev-user-lucas", "lucas.v@email.com", "Lucas V.", 890, "LUCAS-V", "Microcentro", "Nivel 3 · Comunidad", "4.9 · 42 cesiones", -34.6039, -58.3810),
            user(OgtIds.Sofia, "dev-user-sofia", "sofia.m@email.com", "Sofía M.", 4820, "SOFIA-M", "Palermo Soho", "Líder Comunitaria", "32 buenas acciones", -34.5892, -58.4280),
            user(OgtIds.CarlosG, "dev-user-carlos-g", "carlos.g@email.com", "Carlos Gutiérrez", 1680, "CARLOS-G", "Distrito Norte", "Nivel 3 · Comunidad", "8 bebederos solares", -34.5550, -58.4500),
            user(OgtIds.Camila, "dev-user-camila", "camila.t@email.com", "Camila T.", 2760, "CAMILA-T", "Palermo Soho", "Nivel 3 · Comunidad", "45 kg compostados", -34.5895, -58.4290),
            user(OgtIds.Mateo, "dev-user-mateo", "mateo.r@email.com", "Mateo R.", 2100, "MATEO-R", "Palermo", "Nivel 3 · Comunidad", "Cerámica y bicis", -34.5870, -58.4270),
            user(OgtIds.Nicolas, "dev-user-nicolas", "nicolas.b@email.com", "Nicolás B.", 2850, "NICO-B", "Palermo", "Nivel 3 · Comunidad", "8 tutorías gratuitas", -34.5860, -58.4310),
            user(OgtIds.Valeria, "dev-user-valeria", "valeria.g@email.com", "Valeria Gómez", 1980, "VALE-G", "Palermo", "Nivel 3 · Comunidad", "Huertas activas", -34.5900, -58.4265),
            user(OgtIds.CarlosR, "dev-user-carlos-r", "carlos.r@email.com", "Carlos R.", 3910, "CARLOS-R", "Palermo", "Rescate Animal", "Milo y Rocco", -34.5910, -58.4320),
            user(OgtIds.Lucia, "dev-user-lucia", "lucia.v@email.com", "Lucía V.", 3450, "LUCIA-V", "Palermo", "Huertas y Trueque", "Semillas de albahaca", -34.5882, -58.4240),
            user(OgtIds.MateoG, "dev-user-mateo-g", "mateo.g@email.com", "Mateo G.", 3120, "MATEO-G", "Palermo", "Nivel 3 · Comunidad", "12 árboles plantados", -34.5850, -58.4295),
            user(OgtIds.ValeriaP, "dev-user-valeria-p", "valeria.p@email.com", "Valeria P.", 2980, "VALE-P", "Palermo", "Nivel 4 · Comunidad", "15 parkings liberados", -34.5898, -58.4278),
            user(OgtIds.Joaquin, "dev-user-joaquin", "joaquin.s@email.com", "Joaquín S.", 2690, "JOA-S", "Palermo", "Nivel 3 · Comunidad", "11 reparaciones comunitarias", -34.5868, -58.4330),
            user(OgtIds.MarianaD, "dev-user-mariana-d", "mariana.d@email.com", "Mariana D.", 2610, "MARI-D", "Palermo", "Nivel 3 · Comunidad", "19 comidas compartidas", -34.5875, -58.4255),
            user(OgtIds.DiegoF, "dev-user-diego-f", "diego.f@email.com", "Diego F.", 2600, "DIE-F", "Palermo", "Nivel 3 · Comunidad", "6 tránsitos de mascotas", -34.5905, -58.4340),
            user(OgtIds.Roberto, "dev-user-roberto", "roberto.v@email.com", "Roberto V.", 1540, "ROBE-V", "Palermo", "Nivel 2 · Comunidad", "Nietos en la plaza", -34.5881, -58.4290),
        )

        db.companies += LocalCompany(
            id = OgtIds.Acme,
            legalName = "Acme Solidaridad S.A.",
            tradeName = "Acme RSE",
            verified = true,
            campaignBalanceCents = 5_000_000,
            impactScore = 82.5,
        )
        db.companyAdmins += OgtIds.Acme to OgtIds.Diego

        db.stories += listOf(
            LocalStory("story-me", "Tu historia"),
            LocalStory("follow-valeria", "Valeria"),
            LocalStory("follow-carlosr", "Carlos"),
            LocalStory("follow-camila", "Camila"),
            LocalStory("follow-mariana", "Mariana"),
            LocalStory("follow-carlosg", "Carlos G."),
            LocalStory("follow-sofia", "Sofía"),
            LocalStory("follow-lucas", "Lucas"),
            LocalStory("follow-mateo", "Mateo"),
            LocalStory("follow-diego", "Diego"),
            LocalStory("follow-lucia", "Lucía"),
        )

        val now = currentEpochMs()
        db.posts += listOf(
            LocalSocialPost(OgtIds.PostPlaya, AuthorKind.USER, OgtIds.ValeriaP, null, "Costa de Vicente López", "Hace 20 min", "Limpieza de playa", "Hoy juntamos plásticos en la costa con el club de remo. Salieron 6 bolsas y dejamos el arenado más limpio para el fin de semana.", 42, 6, false, null, now - 20 * 60_000L, eventStartsAtEpochMs = now - 40 * 60_000L),
            LocalSocialPost(OgtIds.PostPatitas, AuthorKind.USER, OgtIds.CarlosR, null, "Refugio Patitas", "Hace 1 h", "Rescate animal", "Milo y Rocco ya tienen patio nuevo en el refugio. La comunidad acercó madera y una pileta para los días de calor.", 61, 9, false, null, now - 60 * 60_000L),
            LocalSocialPost(OgtIds.PostCompost, AuthorKind.USER, OgtIds.Camila, null, "Pasaje del Lago", "Hace 3 h", "Compost comunitario", "El punto comunitario de compost ya suma 45 kg. Si traen restos húmedos, los recibimos de martes a sábado a la mañana.", 37, 5, false, null, now - 3 * 60 * 60_000L),
            LocalSocialPost(OgtIds.PostArboles, AuthorKind.USER, OgtIds.Mariana, null, "Parque Central", "Hace 5 h", "Reforestación", "¡Misión cumplida! Plantamos 45 árboles nativos en el Parque Central gracias a las 26 personas que se sumaron este sábado. Juntos reverdecemos la comunidad. 🌿💚", 248, 34, false, null, now - 5 * 60 * 60_000L, eventStartsAtEpochMs = now - 8 * 60 * 60_000L),
            LocalSocialPost(OgtIds.PostBebederos, AuthorKind.USER, OgtIds.CarlosG, null, "Distrito Norte", "Hace 8 h", "Energía solar", "Los bebederos solares ya están dando agua fresca para aves y perritos callejeros. Tienen sensor de flujo y filtración recirculante para los días de calor. 🐾💧", 88, 12, false, null, now - 8 * 60 * 60_000L),
            LocalSocialPost(OgtIds.PostTaller, AuthorKind.USER, OgtIds.Sofia, null, "Plaza Serrano", "Ayer", "Huerta comunitaria", "Plantines de albahaca y romero para quien se sume el sábado a las 11. Vamos a multiplicar sin químicos; si tienen frascos de vidrio, tráiganlos.", 86, 18, false, null, now - 26 * 60 * 60_000L, eventStartsAtEpochMs = now + 20 * 60 * 60_000L),
            LocalSocialPost(OgtIds.PostBebederoPlaza, AuthorKind.USER, OgtIds.Lucas, null, "Plaza Armenia", "Ayer", "Cuidado comunitario", "Revisamos el filtro del bebedero de la plaza. Quedó circulando otra vez; si ven que baja la presión, avisen en el grupo.", 29, 4, false, null, now - 28 * 60 * 60_000L),
            LocalSocialPost(OgtIds.PostTrueque, AuthorKind.USER, OgtIds.Mateo, null, "Taller de cerámica", "Hace 2 d", "Trueque", "Cerámicas y un par de bicis listas para intercambiar. El domingo armamos mesa en la vereda de las 16 a las 19.", 33, 7, false, null, now - 48 * 60 * 60_000L, eventStartsAtEpochMs = now + 40 * 60 * 60_000L),
            LocalSocialPost(OgtIds.PostRescate, AuthorKind.USER, OgtIds.DiegoF, null, "Palermo", "Hace 2 d", "Tránsito animal", "Dos gatitos en tránsito hasta el fin de semana. Buscamos casa o una noche más de cobijo; comida y vacunas van por nuestra cuenta.", 54, 11, false, null, now - 50 * 60 * 60_000L),
            LocalSocialPost(OgtIds.PostMerienda, AuthorKind.USER, OgtIds.Lucia, null, "Comedor San Cayetano", "Hace 3 d", "Merienda comunitaria", "La merienda comunitaria salió con lo que sobró del domingo. Alcance para 28 chicos y sobró fruta para el martes.", 47, 8, false, null, now - 72 * 60 * 60_000L, eventStartsAtEpochMs = now - 80 * 60 * 60_000L),
            LocalSocialPost(OgtIds.PostAnaComedor, AuthorKind.USER, OgtIds.Ana, null, "Balvanera", "Hoy", "Comedor", "Hoy llevé alimento a un comedor de Balvanera. Si alguien suma frutas, avisame.", 18, 2, false, null, now - 6 * 60 * 60_000L, eventStartsAtEpochMs = now - 7 * 60 * 60_000L),
            LocalSocialPost(OgtIds.PostAcmeRse, AuthorKind.COMPANY, null, OgtIds.Acme, "CABA", "Esta semana", "RSE", "Acme financia 200 kg de alimento para refugios esta semana.", 54, 0, false, null, now - 4 * 24 * 60 * 60_000L),
            LocalSocialPost(OgtIds.PostLuna, AuthorKind.USER, OgtIds.Carla, null, "Parque Centenario", "Hace 30 min", "Adopción", "Luna · 6 meses. Cachorra mestiza rescatada en Parque Centenario. Cariñosa y con energía de plaza.", 22, 3, false, null, now - 30 * 60_000L, listingId = OgtIds.AnimalLuna),
            LocalSocialPost(OgtIds.PostOliver, AuthorKind.USER, OgtIds.CarlosR, null, "Palermo", "Hace 3 d", "Mascota perdida", "Oliver se perdió cerca de Plaza Serrano. Collar rojo, oreja caída. Radio 2 km: ya avisamos a 140 personas de la comunidad.", 31, 5, false, null, now - 3 * 86_400_000L, listingId = OgtIds.AnimalOliver),
            LocalSocialPost(OgtIds.PostGrisu, AuthorKind.USER, OgtIds.Ana, null, "Chacarita", "Hace 5 h", "Adopción", "Grisú, gato negro de un solo ojo, vive en la veterinaria de Corrientes. Se sube a las computadoras. Ideal para alguien que trabaje en casa.", 16, 2, false, null, now - 5 * 60 * 60_000L, listingId = OgtIds.AnimalGrisu),
            LocalSocialPost(OgtIds.PostCoco, AuthorKind.USER, OgtIds.Nicolas, null, "Villa Crespo", "Hace 1 d", "Mascota perdida", "Coco se bajó del techo del taller y no volvió. Atigrado, come atún, responde si le silbás bajo.", 24, 4, false, null, now - 26 * 60 * 60_000L, listingId = OgtIds.AnimalCoco),
            LocalSocialPost(OgtIds.PostNube, AuthorKind.USER, OgtIds.Lucia, null, "Almagro", "Hace 8 h", "Adopción", "Nube, coneja blanca, llegó en una caja de verduras. Come heno y se esconde detrás de la heladera. Busca familia calma.", 11, 1, false, null, now - 8 * 60 * 60_000L, listingId = OgtIds.AnimalNube),
            LocalSocialPost(OgtIds.PostPepe, AuthorKind.USER, OgtIds.MarianaD, null, "San Telmo", "Hace 12 h", "Adopción", "Un perico verde se posó en el aljibe de Defensa. Come manzana y dice hola si abrís la ventana. Buscamos vecino con voladera.", 14, 3, false, null, now - 12 * 60 * 60_000L, listingId = OgtIds.AnimalPepe),
            LocalSocialPost(OgtIds.PostRita, AuthorKind.USER, OgtIds.Lucas, null, "Núñez", "Hace 2 d", "Adopción", "Rita, tortuga de orejas rojas, llegó a la escuela 12 en una palangana. El club de ciencias busca un adulto que la adopte en serio.", 9, 1, false, null, now - 48 * 60 * 60_000L, listingId = OgtIds.AnimalRita),
            LocalSocialPost(OgtIds.PostPancho, AuthorKind.USER, OgtIds.Mariana, null, "Saavedra", "Hace 6 h", "Adopción", "Pancho, hámster sirio, se escapó del aula y apareció en el guardapolvo. Come pipas y duerme de día. Casa sin gatos curiosos.", 8, 2, false, null, now - 6 * 60 * 60_000L, listingId = OgtIds.AnimalPancho),
            LocalSocialPost(OgtIds.PostTernura, AuthorKind.USER, OgtIds.Camila, null, "Pasaje del Lago", "Hace 40 min", "Cría", "Luna tiene ocho semanas. Duerme en un cesto y se despierta si escuchás el sobre de la comida.", 19, 4, false, null, now - 40 * 60_000L),
            LocalSocialPost(OgtIds.PostHomenaje, AuthorKind.USER, OgtIds.Mariana, null, "Palermo Soho", "Hace 2 h", "Post mortem", "Don Héctor nos enseñó a no pasar de largo si un vecino necesita una mano. Se extraña su risa en el patio.", 27, 6, false, null, now - 2 * 60 * 60_000L, honoreeName = "Don Héctor", heartCount = 14),
            LocalSocialPost(
                id = OgtIds.PostAnecdoteShare,
                authorKind = AuthorKind.USER,
                authorUserId = OgtIds.Sofia,
                authorCompanyId = null,
                place = "Palermo Soho",
                timeLabel = "Hace 12 min",
                tag = "Anécdota",
                body = "Un domingo le llevó sillas a la plaza para que las abuelas no se quedaran de pie. No avisó: las dejó y se fue a comprar facturas.",
                impactCount = 8,
                commentCount = 1,
                isStory = false,
                storyLabel = null,
                createdAtEpochMs = now - 12 * 60_000L,
                sourceUrl = "ogt://p/${OgtIds.PostHomenaje}",
                honoreeName = "Don Héctor",
                parentPostId = OgtIds.PostHomenaje,
            ),
        )

        val homeAuthors = listOf(
            OgtIds.PostPlaya to OgtIds.ValeriaP,
            OgtIds.PostPatitas to OgtIds.CarlosR,
            OgtIds.PostCompost to OgtIds.Camila,
            OgtIds.PostArboles to OgtIds.Mariana,
            OgtIds.PostBebederos to OgtIds.CarlosG,
            OgtIds.PostTaller to OgtIds.Sofia,
            OgtIds.PostBebederoPlaza to OgtIds.Lucas,
            OgtIds.PostTrueque to OgtIds.Mateo,
            OgtIds.PostRescate to OgtIds.DiegoF,
            OgtIds.PostMerienda to OgtIds.Lucia,
            OgtIds.PostAnaComedor to OgtIds.Ana,
            OgtIds.PostLuna to OgtIds.Carla,
            OgtIds.PostOliver to OgtIds.CarlosR,
            OgtIds.PostGrisu to OgtIds.Ana,
            OgtIds.PostCoco to OgtIds.Nicolas,
            OgtIds.PostNube to OgtIds.Lucia,
            OgtIds.PostPepe to OgtIds.MarianaD,
            OgtIds.PostRita to OgtIds.Lucas,
            OgtIds.PostPancho to OgtIds.Mariana,
            OgtIds.PostTernura to OgtIds.Camila,
        )
        db.postPeople += LocalPostPerson(OgtIds.PostHomenaje, OgtIds.Mariana, PostPersonRole.AUTHOR)
        db.postPeople += LocalPostPerson(OgtIds.PostHomenaje, OgtIds.Roberto, PostPersonRole.PROTAGONIST)
        db.postPeople += LocalPostPerson(OgtIds.PostAnecdoteShare, OgtIds.Sofia, PostPersonRole.AUTHOR)
        db.anecdotes += LocalAnecdote(
            id = OgtIds.AnecdoteHectorSillas,
            postId = OgtIds.PostHomenaje,
            authorUserId = OgtIds.Sofia,
            authorName = "Sofía M.",
            body = "Un domingo le llevó sillas a la plaza para que las abuelas no se quedaran de pie. No avisó: las dejó y se fue a comprar facturas.",
            sortOrder = 0,
            createdAtEpochMs = now - 40 * 60_000L,
            impactCount = 5,
            heartCount = 2,
        )
        homeAuthors.forEach { (postId, userId) ->
            db.postPeople += LocalPostPerson(postId, userId, PostPersonRole.AUTHOR)
            db.postPeople += LocalPostPerson(postId, userId, PostPersonRole.PROTAGONIST)
        }
        db.follows += listOf(
            OgtIds.ValeriaP, OgtIds.CarlosR, OgtIds.Camila, OgtIds.Mariana, OgtIds.CarlosG,
            OgtIds.Sofia, OgtIds.Lucas, OgtIds.Mateo, OgtIds.DiegoF, OgtIds.Lucia,
        ).filter { it != OgtIds.Mariana }.map { LocalFollow(OgtIds.Mariana, it) }
        db.follows += LocalFollow(OgtIds.Bruno, OgtIds.Ana)
        db.follows += LocalFollow(OgtIds.Carla, OgtIds.Ana)

        fun event(id: String, postId: String, kind: FeedEventKind, hoursAgo: Double) = LocalFeedEvent(
            id = id,
            viewerId = OgtIds.Mariana,
            postId = postId,
            kind = kind,
            createdAtEpochMs = now - (hoursAgo * 3_600_000).toLong(),
        )
        db.feedEvents += listOf(
            event("ev-homenaje-heart", OgtIds.PostHomenaje, FeedEventKind.HEART, 1.5),
            event("ev-arboles-clap", OgtIds.PostArboles, FeedEventKind.CLAP, 4.0),
            event("ev-arboles-comment", OgtIds.PostArboles, FeedEventKind.COMMENT, 4.0),
            event("ev-playa-clap", OgtIds.PostPlaya, FeedEventKind.CLAP, 0.2),
            event("ev-playa-profile", OgtIds.PostPlaya, FeedEventKind.PROFILE_TAP, 0.2),
            event("ev-patitas-clap", OgtIds.PostPatitas, FeedEventKind.CLAP, 0.8),
            event("ev-taller-comment", OgtIds.PostTaller, FeedEventKind.COMMENT, 20.0),
            event("ev-merienda-impression", OgtIds.PostMerienda, FeedEventKind.IMPRESSION, 70.0),
        )

        fun image(postId: String, key: String, order: Int = 0) = LocalPostMedia(
            id = "$postId-m$order",
            postId = postId,
            kind = MediaKind.IMAGE,
            url = "asset://$key",
            sortOrder = order,
            assetKey = key,
        )
        db.postMedia += listOf(
            image(OgtIds.PostPlaya, "feed_story_playa"),
            image(OgtIds.PostPlaya, "feed_story_compost", 1),
            image(OgtIds.PostPatitas, "feed_story_patitas"),
            image(OgtIds.PostPatitas, "feed_story_donacion", 1),
            image(OgtIds.PostCompost, "feed_story_compost"),
            image(OgtIds.PostArboles, "feed_photo_arboles"),
            image(OgtIds.PostArboles, "feed_story_playa", 1),
            image(OgtIds.PostBebederos, "feed_photo_bebederos"),
            image(OgtIds.PostTaller, "feed_photo_arboles"),
            image(OgtIds.PostBebederoPlaza, "feed_photo_bebederos"),
            image(OgtIds.PostTrueque, "feed_story_donacion"),
            image(OgtIds.PostRescate, "feed_story_patitas"),
            image(OgtIds.PostMerienda, "feed_story_donacion"),
            image(OgtIds.PostAnaComedor, "feed_story_donacion"),
            image(OgtIds.PostAcmeRse, "feed_story_solar"),
            image(OgtIds.PostLuna, "seed_pet_puppy"),
            image(OgtIds.PostLuna, "seed_pet_golden", 1),
            image(OgtIds.PostTernura, "seed_pet_puppy"),
            image(OgtIds.PostHomenaje, "feed_story_donacion"),
            image(OgtIds.PostAnecdoteShare, "feed_story_donacion"),
            image(OgtIds.PostOliver, "seed_pet_dog"),
            image(OgtIds.PostOliver, "seed_pet_tabby", 1),
            image(OgtIds.PostGrisu, "seed_pet_cat"),
            image(OgtIds.PostCoco, "seed_pet_tabby"),
            image(OgtIds.PostNube, "seed_pet_rabbit"),
            image(OgtIds.PostPepe, "seed_pet_parrot"),
            image(OgtIds.PostRita, "seed_pet_turtle"),
            image(OgtIds.PostPancho, "seed_pet_hamster"),
        )
        db.postMedia += LocalPostMedia(
            id = "${OgtIds.PostPlaya}-vid",
            postId = OgtIds.PostPlaya,
            kind = MediaKind.VIDEO,
            url = "https://upload.wikimedia.org/wikipedia/commons/transcoded/3/3a/Sea_lion_Ushuaia.webm/Sea_lion_Ushuaia.webm.480p.vp9.webm",
            posterUrl = "asset://feed_story_playa",
            sortOrder = 2,
            assetKey = "feed_story_playa",
            altText = "Video de la costa",
            durationMs = 24_000,
        )
        // El feed editorial vive en la VPS. No sembrar las 40+100 noticias locales inventadas.

        db.comments += listOf(
            LocalComment("c-roberto-arboles", OgtIds.PostArboles, OgtIds.Roberto, "¡Gran iniciativa vecina! Mis nietos amaron aprender a regarlos.", "Hace 1 h", null),
            LocalComment("c-sofia-arboles", OgtIds.PostArboles, OgtIds.Sofia, "¿Cuándo es la próxima fecha? ¡Me anoto con 3 amigos del club!", "Hace 50 min", null),
            LocalComment(
                "c-lucas-playa",
                OgtIds.PostPlaya,
                OgtIds.Lucas,
                "Brutal. El sábado sumo bolsas y guantes.",
                "Hace 10 min",
                null,
                clapCount = 4,
                clapUserIds = listOf(OgtIds.ValeriaP, OgtIds.Camila, OgtIds.Sofia, OgtIds.Mariana),
            ),
            LocalComment(
                "c-sofia-playa",
                OgtIds.PostPlaya,
                OgtIds.Sofia,
                "Llevo mate y una bolsa extra. ¿Nos vemos en el club de remo?",
                "Hace 8 min",
                null,
                clapCount = 2,
                clapUserIds = listOf(OgtIds.Lucas, OgtIds.ValeriaP),
            ),
            LocalComment("c-camila-playa", OgtIds.PostPlaya, OgtIds.Camila, "Sumo. Si alguien tiene pinzas para el plástico chico, avisen.", "Hace 4 min", null),
            LocalComment("c-lucia-patitas", OgtIds.PostPatitas, OgtIds.Lucia, "Les quedó hermoso el patio. ¿Hace falta más madera?", "Hace 40 min", null),
            LocalComment("c-mateo-compost", OgtIds.PostCompost, OgtIds.Mateo, "Llevo el balde el jueves a la mañana.", "Hace 2 h", null),
            LocalComment("c-nico", OgtIds.PostTaller, OgtIds.Nicolas, "¡Me sumo con 4 frascos grandes y plantines de menta para compartir! Nos vemos el sábado.", "Nivel 3 · Hace 45 min", null),
            LocalComment("c-mari-d", OgtIds.PostTaller, OgtIds.MarianaD, "Hermosa iniciativa Sofi, ¡siempre inspirando a la comunidad!", "Hace 1 h", null),
            LocalComment("c-ana-1", "55555555-5555-5555-5555-555555555555", OgtIds.Bruno, "Llevo naranjas el jueves.", "Hace 20 min", null),
        )

        val parkTtl = currentEpochMs() + 12 * 60_000L
        db.parkingSpots += LocalParkingSpot(
            id = OgtIds.SpotCorrientes,
            ownerUserId = OgtIds.Lucas,
            claimedByUserId = null,
            latitude = -34.6040,
            longitude = -58.3819,
            status = ParkingStatus.AVAILABLE,
            version = 0,
            address = "Av. Corrientes 1420",
            vehicleLabel = "Toyota Corolla Blanco · ABC 492",
            etaSeconds = 180,
            distanceMeters = 450.0,
            rewardPoints = 50,
            notes = "Al ceder el lugar, Lucas recibe 50 puntos comunitarios y tú evitas 850 g de emisiones de CO₂.",
            expiresAtEpochMs = parkTtl,
            ownerLastLat = -34.6040,
            ownerLastLng = -58.3819,
        )
        db.parkingSpots += LocalParkingSpot(
            id = "77777777-7777-7777-7777-777777777777",
            ownerUserId = OgtIds.Bruno,
            claimedByUserId = null,
            latitude = -34.6037,
            longitude = -58.3816,
            status = ParkingStatus.AVAILABLE,
            version = 0,
            address = "Av. 9 de Julio y Corrientes",
            vehicleLabel = "Hatchback gris",
            etaSeconds = 720,
            distanceMeters = 180.0,
            rewardPoints = 40,
            notes = "Salgo de Av. 9 de Julio y Corrientes",
            expiresAtEpochMs = parkTtl,
            ownerLastLat = -34.6037,
            ownerLastLng = -58.3816,
        )
        db.parkingSpots += LocalParkingSpot(
            id = "spot-thames",
            ownerUserId = OgtIds.ValeriaP,
            claimedByUserId = null,
            latitude = -34.5886,
            longitude = -58.4294,
            status = ParkingStatus.AVAILABLE,
            version = 0,
            address = "Thames 1420, entre Gorriti y Honduras",
            vehicleLabel = "SUV mediano",
            etaSeconds = 180,
            distanceMeters = 90.0,
            rewardPoints = 50,
            notes = "Sombra de árbol · Confirmado por 3 personas",
            expiresAtEpochMs = parkTtl,
            ownerLastLat = -34.5886,
            ownerLastLng = -58.4294,
        )

        db.animals += listOf(
            LocalAnimalListing(
                OgtIds.AnimalLuna, OgtIds.Carla, "ADOPTION", "DOG", "SMALL", "LOW", "Luna · 6 meses",
                "Cachorra mestiza rescatada en Parque Centenario. Cariñosa, sociable y con energía juguetona.",
                "Parque Centenario", 2000, 0, false, petName = "Luna",
                ageLabel = "6 meses", sex = "HEMBRA", temperament = "Cariñosa y sociable",
                vaccinated = true, sterilized = false, homeNeeds = "Alguien en casa varias horas. Se lleva bien con perros calmos.",
            ),
            LocalAnimalListing(
                id = OgtIds.AnimalOliver,
                reporterUserId = OgtIds.CarlosR,
                kind = "LOST",
                species = "DOG",
                size = "LARGE",
                urgency = "HIGH",
                title = "Se busca a Oliver",
                description = "Mestizo grande, collar rojo, oreja derecha caída. Responde a su nombre.",
                place = "Palermo",
                alertRadiusM = 2000,
                neighborsAlerted = 140,
                resolved = false,
                petName = "Oliver",
                marks = "Collar rojo, oreja derecha caída, paso alegre",
                lastSeenPlace = "Plaza Serrano y Costa Rica",
                latitude = -34.5889,
                longitude = -58.4328,
                lastSeenAtEpochMs = now - 3 * 86_400_000L,
            ),
            LocalAnimalListing(
                id = OgtIds.AnimalGrisu,
                reporterUserId = OgtIds.Ana,
                kind = "ADOPTION",
                species = "CAT",
                size = "MEDIUM",
                urgency = "LOW",
                title = "Grisú · 4 años",
                description = "Gato negro de un solo ojo. Se sube a las computadoras. Ideal para alguien que trabaje en casa y le guste el silencio.",
                place = "Chacarita",
                alertRadiusM = 0,
                neighborsAlerted = 0,
                resolved = false,
                petName = "Grisú",
                ageLabel = "4 años",
                sex = "MACHO",
                temperament = "Silencioso, curioso, se queda si le hablás bajo",
                vaccinated = true,
                sterilized = true,
                homeNeeds = "Depto tranquilo. Sin perros grandes.",
            ),
            LocalAnimalListing(
                id = OgtIds.AnimalCoco,
                reporterUserId = OgtIds.Nicolas,
                kind = "LOST",
                species = "CAT",
                size = "MEDIUM",
                urgency = "HIGH",
                title = "Se busca a Coco",
                description = "Atigrado, come atún, responde si le silbás bajo. Se bajó del techo del taller y no volvió.",
                place = "Villa Crespo",
                alertRadiusM = 1000,
                neighborsAlerted = 64,
                resolved = false,
                petName = "Coco",
                marks = "Atigrado, collar de hilo rojo, oreja izquierda con un corte viejo",
                lastSeenPlace = "Taller de Thames y Camargo",
                latitude = -34.5984,
                longitude = -58.4372,
                lastSeenAtEpochMs = now - 26 * 60 * 60_000L,
            ),
            LocalAnimalListing(
                id = OgtIds.AnimalNube,
                reporterUserId = OgtIds.Lucia,
                kind = "ADOPTION",
                species = "RABBIT",
                size = "SMALL",
                urgency = "LOW",
                title = "Nube · 8 meses",
                description = "Coneja blanca. Come heno y se esconde detrás de la heladera. Tres noches de tránsito y ya busca familia calma.",
                place = "Almagro",
                alertRadiusM = 0,
                neighborsAlerted = 0,
                resolved = false,
                petName = "Nube",
                ageLabel = "8 meses",
                sex = "HEMBRA",
                temperament = "Miedosa al principio, se deja acariciar si hay silencio",
                vaccinated = false,
                sterilized = false,
                homeNeeds = "Jaula amplia o rincón cerrado. Sin perros sueltos.",
            ),
            LocalAnimalListing(
                id = OgtIds.AnimalPepe,
                reporterUserId = OgtIds.MarianaD,
                kind = "ADOPTION",
                species = "BIRD",
                size = "SMALL",
                urgency = "LOW",
                title = "Pepe · adulto",
                description = "Perico verde. Come manzana y dice hola si abrís la ventana. Buscamos criador responsable o vecino con voladera.",
                place = "San Telmo",
                alertRadiusM = 0,
                neighborsAlerted = 0,
                resolved = false,
                petName = "Pepe",
                ageLabel = "adulto",
                sex = "MACHO",
                temperament = "Hablador a la mañana, calmo a la siesta",
                vaccinated = false,
                sterilized = false,
                homeNeeds = "Voladera o jaula grande. No soltar a la calle.",
            ),
            LocalAnimalListing(
                id = OgtIds.AnimalRita,
                reporterUserId = OgtIds.Lucas,
                kind = "ADOPTION",
                species = "TURTLE",
                size = "SMALL",
                urgency = "LOW",
                title = "Rita · 3 años",
                description = "Tortuga de orejas rojas. Llegó a la escuela 12 en una palangana. El club de ciencias arma un terrario y busca un adulto que la adopte en serio.",
                place = "Núñez",
                alertRadiusM = 0,
                neighborsAlerted = 0,
                resolved = false,
                petName = "Rita",
                ageLabel = "3 años",
                sex = "HEMBRA",
                temperament = "Lenta, se asoma si hay sol",
                vaccinated = false,
                sterilized = false,
                homeNeeds = "Acuario con rampa y filtro. No es un souvenir.",
            ),
            LocalAnimalListing(
                id = OgtIds.AnimalPancho,
                reporterUserId = OgtIds.Mariana,
                kind = "ADOPTION",
                species = "HAMSTER",
                size = "SMALL",
                urgency = "LOW",
                title = "Pancho · 4 meses",
                description = "Hámster sirio. Come pipas y duerme de día. Se escapó del aula y apareció en el guardapolvo. Buscamos casa sin gatos curiosos.",
                place = "Saavedra",
                alertRadiusM = 0,
                neighborsAlerted = 0,
                resolved = false,
                petName = "Pancho",
                ageLabel = "4 meses",
                sex = "MACHO",
                temperament = "Nocturno, se deja tomar si no hay apuro",
                vaccinated = false,
                sterilized = false,
                homeNeeds = "Jaula con rueda. Sin gatos sueltos.",
            ),
        )

        db.causes += listOf(
            LocalCause(OgtIds.CausePibes, OgtIds.Carla, "Comedor Los Pibes: Huerto Agroecológico", "Riego por goteo, compostaje comunal y plantines para 120 familias. Cierra en 14 días.", 500_000, 320_000, 88, true),
            LocalCause("99999999-9999-9999-9999-999999999999", OgtIds.Carla, "Techo para el refugio de Palermo", "Reparación urgente del techo antes del invierno.", 2_500_000, 430_000, 22, true),
            LocalCause("cause-taller", OgtIds.Sofia, "Taller Comunitario de Reciclaje Urbano", "Sábado · 10:00 AM · 4 vacantes", 0, 0, 12, true),
        )

        db.skillTags += listOf(
            LocalSkillTag(OgtIds.TagHuerta, "huerta", "Jardinería Urbana"),
            LocalSkillTag(OgtIds.TagBici, "bici", "Reparación de Bicis"),
            LocalSkillTag("tag-ingles", "ingles", "Inglés Conversacional"),
            LocalSkillTag(OgtIds.TagVegana, "vegana", "Cocina Vegana"),
            LocalSkillTag(OgtIds.TagCeramica, "ceramica", "Cerámica"),
            LocalSkillTag("tag-foto", "foto", "Fotografía con Celular"),
            LocalSkillTag("tag-torneada", "torneada", "Cerámica Torneada"),
            LocalSkillTag(OgtIds.TagMueble, "mueble", "Bajar un mueble"),
            LocalSkillTag(OgtIds.TagTramite, "tramite", "Hacer un trámite"),
        )
        db.userSkills += listOf(
            LocalUserSkill(OgtIds.Mariana, OgtIds.TagHuerta, offered = true, requested = false),
            LocalUserSkill(OgtIds.Mariana, OgtIds.TagBici, offered = true, requested = false),
            LocalUserSkill(OgtIds.Mariana, "tag-ingles", offered = true, requested = false),
            LocalUserSkill(OgtIds.Mariana, OgtIds.TagVegana, offered = false, requested = true),
            LocalUserSkill(OgtIds.Mariana, OgtIds.TagCeramica, offered = false, requested = true),
            LocalUserSkill(OgtIds.Mariana, OgtIds.TagMueble, offered = false, requested = true),
            LocalUserSkill(OgtIds.Sofia, OgtIds.TagVegana, offered = true, requested = false),
            LocalUserSkill(OgtIds.Sofia, OgtIds.TagHuerta, offered = false, requested = true),
            LocalUserSkill(OgtIds.Mateo, "tag-torneada", offered = true, requested = false),
            LocalUserSkill(OgtIds.Mateo, OgtIds.TagBici, offered = false, requested = true),
        )

        db.matches += listOf(
            LocalTimebankMatch(OgtIds.MatchSofia, OgtIds.Mariana, OgtIds.Sofia, "Cocina Vegana", "Armar la huerta del balcón", 98, "a 1.2 km", "Hago panes y quesos vegetales. A cambio necesito una mano para armar la huerta.", true),
            LocalTimebankMatch(OgtIds.MatchMateo, OgtIds.Mariana, OgtIds.Mateo, "Cerámica Torneada", "Reparación de Bicis", 92, "a 800 m", "Torno una maceta si me ayudás a dejar la bici en regla.", true),
            LocalTimebankMatch(OgtIds.MatchCamila, OgtIds.Mariana, OgtIds.Camila, "Compostera", "2 Plantines", 100, "Plaza Armenia", "Cambio la compostera por plantines. Día y hora, en el chat.", true),
            LocalTimebankMatch(OgtIds.MatchValeria, OgtIds.Mariana, OgtIds.ValeriaP, "Una clase de foto con el celular", "Cuidar las plantas 4 días", 90, "a 600 m", "Viajo el finde. El día lo hablamos en el chat.", true),
        )
        db.helpExchanges += listOf(
            LocalHelpExchange(
                OgtIds.HelpValeria,
                OgtIds.ValeriaP,
                "Cuidar las plantas 4 días",
                "Una clase de foto con el celular",
                "Viajo el finde. Puedo devolver el favor cuando vuelva.",
                now - 3_600_000L,
            ),
        )

        db.messages += listOf(
            LocalMessage("m1", OgtIds.MatchCamila, OgtIds.Camila, "¡Hola! Vi tu publicación sobre los plantines de huerta. ¿Todavía te queda la compostera casera?", "16:42 hs"),
            LocalMessage("m2", OgtIds.MatchCamila, OgtIds.Mariana, "¡Hola Camila! Sí, la tengo lista con las lombrices californianas activas. ¿Te queda bien hoy por Plaza Armenia?", "16:45 hs"),
            LocalMessage("m3", OgtIds.MatchCamila, OgtIds.Camila, "¡Buenísimo! Sí, puedo tipo 17:30 hs. Llevo los plantines de romero y unas semillas de albahaca de regalo.", "16:48 hs"),
            LocalMessage("m4", OgtIds.MatchCamila, OgtIds.Mariana, "¡Excelente! Ya salgo para allá con la caja protegida. Nos vemos en la pérgola.", "17:05 hs"),
        )

        db.inbox += listOf(
            LocalInboxThread("in-camila", OgtIds.Camila, "Camila T.", "Trueque en curso · Pérgola Armenia", "¡Excelente! Ya salgo para allá con la caja protegida.", "Hace 5 min", OgtIds.MatchCamila, false),
            LocalInboxThread("in-carlos", OgtIds.CarlosR, "Carlos Rodríguez", "Rescate Animal", "Vi tu aviso sobre el perrito avistado cerca de Plaza Serrano. Ya salgo a chequear.", "Hace 42 min", null, false),
            LocalInboxThread("in-lucia", OgtIds.Lucia, "Lucía V. (Huertas)", "Semillas e intercambio · +50 pts", "Muchísimas gracias por el compost. Las semillas de albahaca ya están brotando.", "Ayer", null, false),
            LocalInboxThread("in-vale", OgtIds.ValeriaP, "Valeria M. · Thames 1400", "Parking Vecinal", "El lugar de parking quedó liberado justo a tiempo, gracias.", "Hace 2 días", null, false),
            LocalInboxThread("in-verde", null, "Palermo Soho Verde", "142 personas comprometidas · Activo", "Martín: El sábado sumamos 15 tachos de compost comunitario en Costa Rica y Armenia.", "Hace 18m", null, true),
            LocalInboxThread("in-alertas", null, "Red de Alertas Mascotas", "89 personas en patrulla · Alerta", "Carlos: Buscando a Milo, perrito bretón canela cerca de Gurruchaga.", "Hace 31m", null, true),
        )

        db.campaigns += LocalCampaign(
            "88888888-8888-8888-8888-888888888888",
            OgtIds.Acme,
            "Premiá 100 cesiones de estacionamiento",
            "Si la comunidad concreta 100 handoffs, emitimos cupones de almuerzo.",
            100,
            12,
        )

        db.notifications += listOf(
            LocalNotification("n-rocco", "ALERT", "Se busca a Rocco", "Golden Retriever con collar rojo. Visto por última vez en Gurruchaga y Costa Rica. La familia pide apoyo visual de la comunidad.", "Hace 15 min · Radio de búsqueda: 400m", true, OgtIds.Mariana),
            LocalNotification("n-parking", "KARMA", "¡Ganaste +100 pts de comunidad!", "Valeria M. confirmó que ocupó el espacio de estacionamiento que liberaste en Av. Santa Fe y Armenia.", "Hace 1 h · Impacto: menos tráfico en la zona", false, OgtIds.Mariana),
            LocalNotification("n-trueque", "MATCH", "Propuesta de Intercambio", "Nicolás B. quiere cambiar 1 clase de guitarra por tus 5 kg de compost orgánico.", "Hace 3 h", false, OgtIds.Mariana),
            LocalNotification("n-nivel", "LEVEL", "¡Nuevo Nivel Alcanzado!", "¡Ahora eres Guardián Comunitario Nivel 4! Desbloqueaste descuentos exclusivos del 15% en ferias y comercios agroecológicos de la comunidad.", "Siguiente meta: Guardián de Bosque Urbano (Nivel 5)", false, OgtIds.Mariana),
            LocalNotification("n-medalla", "THANKS", "Medalla de Gratitud", "Mariana S. te envió la medalla “Mano Solidaria”. “¡Muchas gracias por ayudarme con la pala para el huerto el sábado!”", "Hoy", false, OgtIds.Mariana),
        )

        db.karma += listOf(
            LocalKarmaEntry("k1", OgtIds.Mariana, "Ayuda a encontrar perrita extraviada", "Palermo Soho • Hace 2 horas", 100, "Hace 2 h", "paw"),
            LocalKarmaEntry("k2", OgtIds.Mariana, "Espacio de parking liberado", "Av. Santa Fe 3400 • Ayer", 50, "Ayer", "yield"),
            LocalKarmaEntry("k3", OgtIds.Mariana, "Taller gratuito de huerta urbana comunitaria", "Plaza Armenia • Hace 3 días", 150, "Hace 3 días", "skills"),
            LocalKarmaEntry("k4", OgtIds.Mariana, "Donación de semillas a refugio comunitario", "Huerta El Manantial • Hace 5 días", -200, "Hace 5 días", "feed"),
        )

        db.rewards += listOf(
            LocalReward("r1", "Café Gratis", 180, "Havanna • Palermo", "Cappuccino o alfajor de chocolate", "havanna"),
            LocalReward("r2", "20% OFF", 250, "Vivero Verde Esperanza", "Plantines y tierra agroecológica", "vivero"),
            LocalReward("r3", "15% OFF", 120, "Panadería La Espiga • Colegiales", "Pan de masa madre y medialunas", "espiga"),
            LocalReward("r4", "Sponsor Impacto", 400, "Santander Río", "Pase Cultural + 3 meses bonificados", "santander"),
        )

        db.mapPins += listOf(
            LocalMapPin("pin-thames", "PARKING", "Espacio Libre", "Thames 1420 · hace 3m", -34.5886, -58.4294),
            LocalMapPin("pin-oliver", "ANIMAL", "Perrito caniche encontrado", "Plaza Armenia · Hace 12 min", -34.5894, -58.4288),
            LocalMapPin("pin-plantas", "SKILL", "Plantas de interior x Frascos", "Thames y Soler · Hace 24 min", -34.5880, -58.4308),
        )

        db.inviteContacts += listOf(
            LocalInviteContact("Carolina Rossi", "+54 9 11 4455-8899"),
            LocalInviteContact("Agustín Pereyra", "agustin.p@correo.com"),
            LocalInviteContact("Valeria Gómez", "Vecina activa • Huertas · En OnlyGoodThings"),
        )

        db.sponsors += listOf(
            LocalSponsor("s1", "Promocionado con Causa", "Banco Santander Río", "Tasa Cero Movilidad y Solar", "Alcanzaste 1.450 puntos: tasa preferencial 0% para paneles solares y bicicletas eléctricas. Por cada acción, Santander planta 1 árbol nativo en tu municipio.", "Simular Beneficio Verde"),
            LocalSponsor("s2", "A 400m de vos", "Almacén Orgánico Raíces Libres", null, "25% OFF en compras a granel presentando tu perfil OGT. Apoyando huertas agroecológicas de la zona.", null),
            LocalSponsor("s3", null, "Cablevisión Flow", "3 Meses Bonificados", "Tus horas de voluntariado te otorgan 3 meses de suscripción + Flow conecta el merendero Los Aromos sin costo. Requisito: 8 horas de acción social.", "Activar Beneficio Flow"),
        )

        db.addSighting(
            viewer = db.user(OgtIds.Lucas),
            postId = OgtIds.PostOliver,
            listingId = OgtIds.AnimalOliver,
            note = "Creo que lo vi cruzando Serrano hacia Costa Rica. Collar rojo.",
            latitude = -34.5892,
            longitude = -58.4315,
            timeLabel = "Hace 2 d",
            createdAtEpochMs = now - 2 * 86_400_000L,
        )
        db.addSighting(
            viewer = db.user(OgtIds.Sofia),
            postId = OgtIds.PostOliver,
            listingId = OgtIds.AnimalOliver,
            note = "Un perro parecido olió el bebedero de la plaza. Oreja caída.",
            latitude = -34.5880,
            longitude = -58.4340,
            timeLabel = "Ayer",
            createdAtEpochMs = now - 86_400_000L,
        )
        db.addSighting(
            viewer = db.user(OgtIds.Camila),
            postId = OgtIds.PostOliver,
            listingId = OgtIds.AnimalOliver,
            note = "",
            latitude = -34.5904,
            longitude = -58.4328,
            timeLabel = "Hoy",
            createdAtEpochMs = now - 4 * 60 * 60_000L,
        )

        return db
    }

    private fun user(
        id: String,
        firebaseUid: String,
        email: String,
        name: String,
        points: Int,
        invite: String,
        barrio: String,
        level: String,
        honor: String,
        lat: Double,
        lng: Double,
        role: UserRole = UserRole.USER,
    ) = LocalUser(
        id = id,
        firebaseUid = firebaseUid,
        email = email,
        phoneE164 = null,
        displayName = name,
        photoUrl = null,
        role = role,
        communityPoints = points,
        inviteCode = invite,
        barrio = barrio,
        levelLabel = level,
        honorTag = honor,
        latitude = lat,
        longitude = lng,
    )
}
