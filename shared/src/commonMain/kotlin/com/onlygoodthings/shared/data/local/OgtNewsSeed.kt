package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.AuthorKind
import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.PostPersonRole

/**
 * 40 noticias reales resumidas (no copiamos el artículo).
 * Cada post tiene ≥1 media; varios son carrusel; tres incluyen video.
 */
internal object OgtNewsSeed {
    data class NewsItem(
        val id: String,
        val author: String,
        val place: String,
        val hoursAgo: Double,
        val tag: String,
        val body: String,
        val sourceUrl: String,
        val assets: List<String>,
        val videoUrl: String? = null,
        val impact: Int,
    )

    val items: List<NewsItem> = listOf(
        NewsItem("news-01", OgtIds.ValeriaP, "Isla Floreana, Galápagos", 8.0, "Fauna", "158 tortugas gigantes volvieron a Floreana después de 150 años. El Parque Nacional usó satélites de la NASA para elegir los dos sitios de suelta.", "https://tn.com.ar/sociedad/2026/09/16/una-especie-desaparecio-durante-150-anos-y-ahora-158-animales-vuelven-a-una-isla-gracias-a-datos-de-la-nasa/", listOf("seed_n01", "seed_n01b"), impact = 210),
        NewsItem("news-02", OgtIds.CarlosG, "Costanera Sur, CABA", 12.0, "Rescate animal", "El Ecoparque rehabilitó y liberó dos lobos marinos. El traslado hasta el mar recorrió más de 340 km.", "https://radiomasmontecaseros.com/rescatan-y-liberan-a-dos-lobos-marinos-tras-340-kilometros-de-rehabilitacion/", listOf("seed_n02", "seed_n02b"), videoUrl = "https://upload.wikimedia.org/wikipedia/commons/transcoded/3/3a/Sea_lion_Ushuaia.webm/Sea_lion_Ushuaia.webm.480p.vp9.webm", impact = 176),
        NewsItem("news-03", OgtIds.Lucia, "Usaquén, Bogotá", 30.0, "Adopción", "Pirata Patria, rescatado en 2024 casi sin fuerzas, encontró familia en una jornada del IDPYBA. Hoy convive con Frank y una nena de cuatro.", "https://www.pulzo.com/vivir-bien/mascotas/adopcion-en-bogota-la-emotiva-recuperacion-de-pirata-patria-con-el-idpyba-PP5193405A", listOf("seed_n03", "seed_n03b"), impact = 188),
        NewsItem("news-04", OgtIds.CarlosR, "Ciudad de México", 36.0, "Adopción", "CDMX abrió amaresadoptar.cdmx.gob.mx para dar hogar a perros rescatados del Franciscano, Ajusco y la Brigada. El trámite pide visita y contrato.", "https://www.dossierdeprensa.mx/impulsa-brugada-adopcion-responsable-de-animales-rescatados-inicia-proceso-con-plataforma-digital/", listOf("seed_n04"), impact = 154),
        NewsItem("news-05", OgtIds.MateoG, "Red Deer, Canadá", 48.0, "Reforestación", "Voluntarios plantaron 250 árboles y arbustos nativos en Kerry Wood Nature Centre para armar un corredor de fauna.", "https://rdnewsnow.com/2026/09/14/restoration-volunteers-plant-250-trees-shrubs-at-kerry-wood-nature-centre/", listOf("seed_n05"), impact = 97),
        NewsItem("news-06", OgtIds.Camila, "Amberley, Nueva Zelanda", 60.0, "Huerta comunitaria", "Más de 70 vecinos plantaron 15 frutales y compañeras en el primer día del bosque comestible de Amberley.", "https://www.hurunui.govt.nz/council/news?item=id%3A2yn0ol03217q9sv1iv7n", listOf("seed_n06"), impact = 84),
        NewsItem("news-07", OgtIds.Sofia, "Loudoun, Virginia", 72.0, "Huerta comunitaria", "350 voluntarios plantaron 60.000 plantines en JK Community Farm. Todo va a despensas; apuntan a 285.000 libras este año.", "https://jkcommunityfarm.org/in-the-news/volunteers-plant-60k-seedlings-at-jk-community-farm/", listOf("seed_n07", "seed_n07b"), impact = 132),
        NewsItem("news-08", OgtIds.MarianaD, "Reston, Virginia", 80.0, "Merienda comunitaria", "850 vecinos juntaron casi 13,6 toneladas de comida para 60 secundarias. Food For Neighbors cumple 10 años.", "https://patch.com/virginia/reston/food-neighbors-volunteers-collect-nearly-30-000-pounds-fight-teen-hunger", listOf("seed_n08"), impact = 141),
        NewsItem("news-09", OgtIds.Joaquin, "Fresno, California", 90.0, "Merienda comunitaria", "Offer Kindness rescató más de 113 toneladas de fruta que iba a tirarse y las lleva a vecinos sin transporte.", "https://www.ucanr.edu/site/f3-local-farm-food-innovation/article/offer-kindness-rescuing-food-and-feeding-community", listOf("seed_n09"), impact = 119),
        NewsItem("news-10", OgtIds.DiegoF, "Costa oeste de India", 100.0, "Fauna", "Pescadores que antes cazaban tiburones ballena ahora los desenredan de las redes. Wildlife Trust of India los forma y compensa las redes rotas.", "https://mongabay1.substack.com/p/whale-sharks-released-from-nets-along", listOf("seed_n10", "seed_n10b"), impact = 201),
        NewsItem("news-11", OgtIds.ValeriaP, "14 países", 110.0, "Océano", "Coralpalooza 2026 juntó a más de 20 organizaciones: viveros, 118 fragmentos en Misool y 2.709 libras de basura fuera de una playa de Oahu.", "https://coralrestoration.org/celebrating-coralpalooza-2026-around-the-world/", listOf("seed_n11", "seed_n11b"), videoUrl = "https://upload.wikimedia.org/wikipedia/commons/transcoded/8/80/Coral_reef.webm/Coral_reef.webm.480p.vp9.webm", impact = 167),
        NewsItem("news-12", OgtIds.Nicolas, "Bali, Indonesia", 120.0, "Océano", "El programa Indonesia Coral Reef Garden restauró unas 72 ha de arrecife en Bali y ocupó a 11.250 trabajadores costeros.", "https://asiatoday.id/read/indonesias-blue-economy-drive-protects-ocean-biodiversity", listOf("seed_n12"), impact = 109),
        NewsItem("news-13", OgtIds.Ana, "Karimnagar, India", 6.0, "Comedor", "Inauguraron una cocina central de 8 crore de rupias para desayunar a 49.025 chicos en 669 escuelas públicas.", "https://www.thehindu.com/news/national/telangana/8-crore-centralised-kitchen-opened-in-karimnagar-to-serve-breakfast-to-49025-students/article71472979.ece", listOf("seed_n13"), impact = 156),
        NewsItem("news-14", OgtIds.Carla, "Nalanda, Bihar", 140.0, "Comedor", "Didi Ki Rasoi pone a mujeres de JEEViKA a cargo de la cocina de un internado de 300 estudiantes: comida y laburo digno.", "https://indianmasterminds.com/news/didi-ki-rasoi-bihar-229956/", listOf("seed_n14"), impact = 93),
        NewsItem("news-15", OgtIds.Lucas, "Comodoro Rivadavia", 20.0, "Limpieza de playa", "Manos a la Costa: más de 30 vecinos sacaron 5.000 litros de basura de Kilómetro 5 y ya convocan otra fecha.", "https://www.adnsur.com.ar/sociedad/-manos-a-la-costa---mas-de-30-vecinos-limpiaron-la-playa-de-kilometro-5-y-retiraron-5-000-litros-de-basura_a6a7f5c764a152b98eeb6618f", listOf("seed_n15", "seed_n15b"), impact = 128),
        NewsItem("news-16", OgtIds.Bruno, "Córdoba, Argentina", 50.0, "Comedor", "La municipalidad entregó mesas, bancos, huertas y composteras hechas con scrap industrial a 25 merenderos, más verdura agroecológica.", "https://cordoba.gob.ar/passerini-entrego-mobiliario-a-comedores-y-merenderos-comunitarios-fabricado-con-scrap-de-industrias-cordobesas/", listOf("seed_n16"), impact = 101),
        NewsItem("news-17", OgtIds.Mariana, "Barrio Güemes, Salta", 160.0, "Merienda comunitaria", "Vecinos abrieron el merendero Tomás y la Pandilla del Bien para acompañar a pibes del barrio y sostener la memoria de Tomás Arias.", "https://www.vocescriticas.com/noticias/2026/06/12/204133-en-homenaje-a-tomas-arias-impulsan-un-merendero-y-preparan-una-jornada-solidaria-en-barrio-gemes", listOf("seed_n17"), impact = 146),
        NewsItem("news-18", OgtIds.Camila, "Santiago, Chile", 70.0, "Huerta comunitaria", "Fundación Huertas Comunitarias cumple 10 años: 110 huertas, 80 mil personas y casi 395 mil kilos cosechados.", "https://eldesconcierto.cl/hoja-ruta/diez-anos-huertas-comunitarias-110-proyectos-y-casi-400000-kilos-alimentos-cosechados-n5462049", listOf("seed_n18"), impact = 118),
        NewsItem("news-19", OgtIds.Mateo, "La Boquilla, Cartagena", 200.0, "Limpieza de playa", "Global Shapers y pibes de Villa Gloria juntaron 55 kg de plástico y rescataron plántulas de mangle para la ciénaga.", "https://www.eluniversal.com.co/cartagena/2026/02/24/limpieza-y-rescate-de-mangles-la-doble-jornada-de-global-shapers-en-la-boquilla/", listOf("seed_n19"), impact = 112),
        NewsItem("news-20", OgtIds.CarlosR, "Iztapalapa, CDMX", 40.0, "Adopción", "Cereza y Cerecito, sobrevivientes de la explosión de 2025, cumplieron un año juntos en Huellitas Amor Sin Fronteras.", "https://www.milenio.com/comunidad/cereza-cerecito-perritos-sobrevivientes-iztapalapa-regresan-a-hogar", listOf("seed_n20", "seed_n20b"), impact = 231),
        NewsItem("news-21", OgtIds.Valeria, "Nautla, Veracruz", 15.0, "Fauna", "Fundación Yépez soltó entre 3.200 y 3.500 crías de tortuga marina en El Raudal, tras meses de patrullaje nocturno de nidos.", "https://www.upi.com/Top_News/World-News/2026/09/01/latam-mexico-sea-turtles-released/1251788286661/", listOf("seed_n21"), videoUrl = "https://upload.wikimedia.org/wikipedia/commons/transcoded/6/66/Baby_sea_turtles.webm/Baby_sea_turtles.webm.360p.vp9.webm", impact = 198),
        NewsItem("news-22", OgtIds.Sofia, "Kertih, Malasia", 55.0, "Fauna", "500 crías de tortuga verde de un día salieron al mar en Ma’ Daerah, con las Girl Guides de Terengganu.", "https://thesun.my/news/malaysia-news/people-issues/500-green-sea-turtle-hatchlings-released-in-kertih/", listOf("seed_n22"), impact = 87),
        NewsItem("news-23", OgtIds.Lucia, "Río Aguarico, Ecuador", 85.0, "Fauna", "Siete mujeres siekopai —las Turtle Women— incubaron 235 huevos de charapa y devolvieron 208 crías al río.", "https://timesofindia.indiatimes.com/science/meet-the-turtle-women-of-amazon-7-indigenous-women-are-bringing-vulnerable-river-turtles-back-from-the-brink-after-releasing-208-baby-turtles-into-the-wild/articleshow/133726609.cms", listOf("seed_n23", "seed_n23b"), impact = 174),
        NewsItem("news-24", OgtIds.Lucas, "El Campello, España", 25.0, "Fauna", "Nacieron 44 tortugas boba en Muchavista. Voluntarios de Xaloc Mar cuidaron el nido de día y de noche.", "https://www.diariodealicante.net/en/sea-%E2%80%8B%E2%80%8Bturtle-hatchlings-born-Muchavista-Campello/", listOf("seed_n24"), impact = 99),
        NewsItem("news-25", OgtIds.Ana, "Jackson Ward, Richmond", 45.0, "Comedor", "RVA Community Fridges instaló su heladera libre número 17 atrás de Mama J’s. Cualquiera deja o retira comida.", "https://www.wvtf.org/news/2026-09-04/rva-community-fridges-opens-its-17th-free-fridge-and-it-cant-keep-them-full", listOf("seed_n25"), impact = 77),
        NewsItem("news-26", OgtIds.DiegoF, "CDMX", 95.0, "Adopción", "Amar es Adoptar publica de a poco fichas con foto, talla y carácter. Piden que el hogar dure toda la vida del animal.", "https://dondeir.com/mascotas/cdmx-estrena-plataforma-para-adoptar-perros-rescatados-asi-funciona/2026/09/", listOf("seed_n26"), impact = 90),
        NewsItem("news-27", OgtIds.MarianaD, "Unicentro, Bogotá", 18.0, "Adopción", "El IDPYBA sigue abriendo jornadas en shoppings. Recuerdan que también buscan casa para adultos y perros con tratamiento.", "https://www.semana.com/4patas/perros/articulo/en-video-la-historia-de-pirata-patria-el-perro-que-fue-rescatado-en-un-estado-de-salud-grave-y-duro-casi-dos-anos-sin-hogar/202625/", listOf("seed_n27"), impact = 81),
        NewsItem("news-28", OgtIds.CarlosR, "Cuajimalpa, CDMX", 42.0, "Adopción", "156 perros volvieron al Franciscano y el Gobierno habilitó el canal formal de adopción responsable.", "https://www.cronica.com.mx/metropoli/2026/09/14/regresan-156-perros-al-refugio-franciscano-cdmx-inicia-programa-de-adopcion-responsable/", listOf("seed_n28", "seed_n28b"), impact = 123),
        NewsItem("news-29", OgtIds.Joaquin, "Veracruz, México", 130.0, "Fauna", "En el Santuario Playas del Totonacapan soltaron 2.505 crías (verde, lora y carey) durante el festival, con 14 técnicos comunitarios.", "https://www.upi.com/Top_News/World-News/2026/09/01/latam-mexico-sea-turtles-released/1251788286661/", listOf("seed_n29"), impact = 104),
        NewsItem("news-30", OgtIds.Mateo, "La Boquilla, Colombia", 210.0, "Cuidado comunitario", "El plástico de la jornada entra al proyecto Mangle para reciclarse en objetos nuevos, no al relleno.", "https://www.eluniversal.com.co/cartagena/2026/02/24/limpieza-y-rescate-de-mangles-la-doble-jornada-de-global-shapers-en-la-boquilla/", listOf("seed_n30"), impact = 68),
        NewsItem("news-31", OgtIds.Nicolas, "Santa Fe, Argentina", 150.0, "Reforestación", "Más de 150 voluntarios limpiaron el Bajo Oroño y el Parque Federal: 35 árboles nuevos, rastrilleo de playa y 100 scouts en el Día del Scout.", "https://veonoticias.com/santa-fe-limpieza-reforestacion-y-mantenimiento-en-el-bajo-orono-y-el-parque-federal/", listOf("seed_n31"), impact = 96),
        NewsItem("news-32", OgtIds.Roberto, "Barrio Santa Rosa de Lima, Santiago del Estero", 26.0, "Merienda comunitaria", "El merendero Los Chifladitos pide galletas y pan para seguir recibiendo a 70 chicos cada miércoles.", "https://infodelestero.com/2026/09/15/un-merendero-santiagueno-pide-ayuda-para-seguir-acompanando-a-70-ninos-cada-miercoles", listOf("seed_n32"), impact = 88),
        NewsItem("news-33", OgtIds.Lucas, "Villa Aeroparque, Uruguay", 22.0, "Comedor", "Fuego Solidario cocina con los merenderos, no por ellos. En Nuestra Fe acompañan a 58–68 chicos de unas 20 familias.", "https://elmegafono.uy/fuego-solidario-cocinar-para-acompanar-y-visibilizar-el-trabajo-de-los-merenderos/", listOf("seed_n33"), impact = 79),
        NewsItem("news-34", OgtIds.Sofia, "Winter Park, Florida", 11.0, "Adopción", "Pilotos voluntarios de Puppy Rescue Flights ya trasladaron más de 12.000 perros y gatos desde refugios saturados de Alabama hacia hogares en Florida.", "https://www.wftv.com/news/local/winter-park-nonprofit-transports-12000-animals-florida-rescues/QBDFXKCZDRBBDPOQ2GV27PIU4E/", listOf("seed_n34"), impact = 214),
        NewsItem("news-35", OgtIds.Camila, "Breton Bay, Maryland", 65.0, "Océano", "Voluntarios plantaron un millón de ostras bebés en Breton Bay. Desde 2017 la comunidad ya soltó más de 4,5 millones.", "https://thebaynet.com/shell-yeah-volunteers-wanted-to-plant-1-million-oysters-in-breton-bay/", listOf("seed_n35"), impact = 143),
        NewsItem("news-36", OgtIds.ValeriaP, "Miami-Dade, Florida", 14.0, "Limpieza de playa", "La 41ª International Coastal Cleanup suma 65 puntos y 4.000 voluntarios en el condado, con la ballena jorobada como animal del año.", "https://volunteercleanup.org/event/rickenbacker-marina-with-rescue-a-reef-icc2026", listOf("seed_n36"), impact = 121),
        NewsItem("news-37", OgtIds.Lucia, "Kondangal, Telangana", 7.0, "Comedor", "En el piloto de Kondangal la asistencia escolar subió del 75% al 90% con el desayuno. Quieren copiar la cocina en 31 distritos.", "https://www.thehindu.com/news/national/telangana/8-crore-centralised-kitchen-opened-in-karimnagar-to-serve-breakfast-to-49025-students/article71472979.ece", listOf("seed_n37"), impact = 91),
        NewsItem("news-38", OgtIds.Mariana, "Oahu, Hawái", 112.0, "Limpieza de playa", "En Coralpalooza 2026, una sola playa de Oahu sacó 2.709 libras de basura mientras otros equipos outplanteaban coral.", "https://coralrestoration.org/celebrating-coralpalooza-2026-around-the-world/", listOf("seed_n38"), impact = 105),
        NewsItem("news-39", OgtIds.DiegoF, "Parque Federal, Santa Fe", 148.0, "Cuidado comunitario", "Más de 100 scouts de Santa Fe y Recreo mantuvieron el Parque Federal: flora, residuos y un taller de emergencias.", "https://veonoticias.com/santa-fe-limpieza-reforestacion-y-mantenimiento-en-el-bajo-orono-y-el-parque-federal/", listOf("seed_n39"), impact = 73),
        NewsItem("news-40", OgtIds.Carla, "Biscayne National Park, Florida", 13.0, "Limpieza de playa", "Voluntarios cruzan en lancha a Elliott Key, entran al agua hasta la cintura y pesan la basura al volver al visitor center.", "https://volunteercleanup.org/event/2026-international-coastal-cleanup-at-biscayne-national-park", listOf("seed_n40"), impact = 86),
    )

    fun append(db: OgtLocalDatabase, now: Long) {
        items.forEachIndexed { index, item ->
            val post = LocalSocialPost(
                id = item.id,
                authorKind = AuthorKind.USER,
                authorUserId = item.author,
                authorCompanyId = null,
                place = item.place,
                timeLabel = timeLabel(item.hoursAgo),
                tag = item.tag,
                body = item.body,
                impactCount = item.impact,
                commentCount = 1 + index % 5,
                isStory = false,
                storyLabel = null,
                createdAtEpochMs = now - (item.hoursAgo * 3_600_000).toLong(),
                sourceUrl = item.sourceUrl,
            )
            db.posts += post
            db.postPeople += LocalPostPerson(item.id, item.author, PostPersonRole.AUTHOR)
            db.postPeople += LocalPostPerson(item.id, item.author, PostPersonRole.PROTAGONIST)
            item.assets.forEachIndexed { order, key ->
                db.postMedia += LocalPostMedia(
                    id = "${item.id}-img-$order",
                    postId = item.id,
                    kind = MediaKind.IMAGE,
                    url = "asset://$key",
                    sortOrder = order,
                    assetKey = key,
                    altText = item.tag,
                )
            }
            if (item.videoUrl != null) {
                db.postMedia += LocalPostMedia(
                    id = "${item.id}-vid",
                    postId = item.id,
                    kind = MediaKind.VIDEO,
                    url = item.videoUrl,
                    posterUrl = "asset://${item.assets.first()}",
                    sortOrder = item.assets.size,
                    assetKey = item.assets.first(),
                    altText = "Video de ${item.tag}",
                    durationMs = 28_000,
                )
            }
        }
        OgtPetNewsSeed.append(db, now)
    }

    private fun timeLabel(hoursAgo: Double): String = when {
        hoursAgo < 1 -> "Hace ${(hoursAgo * 60).toInt()} min"
        hoursAgo < 24 -> "Hace ${hoursAgo.toInt()} h"
        hoursAgo < 48 -> "Ayer"
        else -> "Hace ${(hoursAgo / 24).toInt()} d"
    }
}
