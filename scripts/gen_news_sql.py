#!/usr/bin/env python3
"""Genera 05_news_seed.sql a partir del catálogo de 40 noticias."""
from pathlib import Path

# (hours, tag, place, body, source, images, video_url)
# authors cycle Ana, Bruno, Carla, Diego
NEWS = [
    (8, "Fauna", "Isla Floreana, Galápagos",
     "158 tortugas gigantes volvieron a Floreana después de 150 años. El Parque Nacional usó satélites de la NASA para elegir los dos sitios de suelta.",
     "https://tn.com.ar/sociedad/2026/09/16/una-especie-desaparecio-durante-150-anos-y-ahora-158-animales-vuelven-a-una-isla-gracias-a-datos-de-la-nasa/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Galapagos_Giant_Tortoise.jpg/800px-Galapagos_Giant_Tortoise.jpg"],
     None),
    (12, "Rescate animal", "Costanera Sur, CABA",
     "El Ecoparque rehabilitó y liberó dos lobos marinos. El traslado hasta el mar recorrió más de 340 km.",
     "https://radiomasmontecaseros.com/rescatan-y-liberan-a-dos-lobos-marinos-tras-340-kilometros-de-rehabilitacion/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/7/74/Zalophus_californianus.jpg/800px-Zalophus_californianus.jpg"],
     "https://upload.wikimedia.org/wikipedia/commons/transcoded/3/3a/Sea_lion_Ushuaia.webm/Sea_lion_Ushuaia.webm.480p.vp9.webm"),
    (30, "Adopción", "Usaquén, Bogotá",
     "Pirata Patria, rescatado en 2024 casi sin fuerzas, encontró familia en una jornada del IDPYBA.",
     "https://www.pulzo.com/vivir-bien/mascotas/adopcion-en-bogota-la-emotiva-recuperacion-de-pirata-patria-con-el-idpyba-PP5193405A",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/d/d9/Golden_Retriever_medium-to-light-coat.jpg/800px-Golden_Retriever_medium-to-light-coat.jpg"],
     None),
    (36, "Adopción", "Ciudad de México",
     "CDMX abrió amaresadoptar.cdmx.gob.mx para dar hogar a perros rescatados del Franciscano, Ajusco y la Brigada.",
     "https://www.dossierdeprensa.mx/impulsa-brugada-adopcion-responsable-de-animales-rescatados-inicia-proceso-con-plataforma-digital/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/2/27/Street_dog.jpg/800px-Street_dog.jpg"],
     None),
    (48, "Reforestación", "Red Deer, Canadá",
     "Voluntarios plantaron 250 árboles y arbustos nativos en Kerry Wood Nature Centre para armar un corredor de fauna.",
     "https://rdnewsnow.com/2026/09/14/restoration-volunteers-plant-250-trees-shrubs-at-kerry-wood-nature-centre/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/Tree_planting.jpg/800px-Tree_planting.jpg"],
     None),
    (60, "Huerta barrial", "Amberley, Nueva Zelanda",
     "Más de 70 vecinos plantaron 15 frutales y compañeras en el primer día del bosque comestible de Amberley.",
     "https://www.hurunui.govt.nz/council/news?item=id%3A2yn0ol03217q9sv1iv7n",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Community_garden.jpg/800px-Community_garden.jpg"],
     None),
    (72, "Huerta barrial", "Loudoun, Virginia",
     "350 voluntarios plantaron 60.000 plantines en JK Community Farm. Todo va a despensas.",
     "https://jkcommunityfarm.org/in-the-news/volunteers-plant-60k-seedlings-at-jk-community-farm/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/4/4a/Vegetable_seedlings.jpg/800px-Vegetable_seedlings.jpg"],
     None),
    (80, "Merienda comunitaria", "Reston, Virginia",
     "850 vecinos juntaron casi 13,6 toneladas de comida para 60 secundarias. Food For Neighbors cumple 10 años.",
     "https://patch.com/virginia/reston/food-neighbors-volunteers-collect-nearly-30-000-pounds-fight-teen-hunger",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/8/8a/Food_bank.jpg/800px-Food_bank.jpg"],
     None),
    (90, "Merienda comunitaria", "Fresno, California",
     "Offer Kindness rescató más de 113 toneladas de fruta que iba a tirarse y las lleva a vecinos sin transporte.",
     "https://www.ucanr.edu/site/f3-local-farm-food-innovation/article/offer-kindness-rescuing-food-and-feeding-community",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/Fresh_fruit.jpg/800px-Fresh_fruit.jpg"],
     None),
    (100, "Fauna", "Costa oeste de India",
     "Pescadores que antes cazaban tiburones ballena ahora los desenredan de las redes.",
     "https://mongabay1.substack.com/p/whale-sharks-released-from-nets-along",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Whale_shark.jpg/800px-Whale_shark.jpg"],
     None),
    (110, "Océano", "14 países",
     "Coralpalooza 2026 juntó a más de 20 organizaciones: viveros, 118 fragmentos en Misool y 2.709 libras de basura fuera de Oahu.",
     "https://coralrestoration.org/celebrating-coralpalooza-2026-around-the-world/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Coral_reef.jpg/800px-Coral_reef.jpg"],
     "https://upload.wikimedia.org/wikipedia/commons/transcoded/8/80/Coral_reef.webm/Coral_reef.webm.480p.vp9.webm"),
    (120, "Océano", "Bali, Indonesia",
     "El programa Indonesia Coral Reef Garden restauró unas 72 ha de arrecife en Bali y ocupó a 11.250 trabajadores costeros.",
     "https://asiatoday.id/read/indonesias-blue-economy-drive-protects-ocean-biodiversity",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/Coral_Outcrop_Flynn_Reef.jpg/800px-Coral_Outcrop_Flynn_Reef.jpg"],
     None),
    (6, "Comedor", "Karimnagar, India",
     "Inauguraron una cocina central de 8 crore de rupias para desayunar a 49.025 chicos en 669 escuelas públicas.",
     "https://www.thehindu.com/news/national/telangana/8-crore-centralised-kitchen-opened-in-karimnagar-to-serve-breakfast-to-49025-students/article71472979.ece",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/School_lunch.jpg/800px-School_lunch.jpg"],
     None),
    (140, "Comedor", "Nalanda, Bihar",
     "Didi Ki Rasoi pone a mujeres de JEEViKA a cargo de la cocina de un internado de 300 estudiantes.",
     "https://indianmasterminds.com/news/didi-ki-rasoi-bihar-229956/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Community_kitchen.jpg/800px-Community_kitchen.jpg"],
     None),
    (20, "Limpieza de playa", "Comodoro Rivadavia",
     "Manos a la Costa: más de 30 vecinos sacaron 5.000 litros de basura de Kilómetro 5.",
     "https://www.adnsur.com.ar/sociedad/-manos-a-la-costa---mas-de-30-vecinos-limpiaron-la-playa-de-kilometro-5-y-retiraron-5-000-litros-de-basura_a6a7f5c764a152b98eeb6618f",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Beach_cleanup.jpg/800px-Beach_cleanup.jpg"],
     None),
    (50, "Comedor", "Córdoba, Argentina",
     "La municipalidad entregó mesas, bancos, huertas y composteras hechas con scrap industrial a 25 merenderos.",
     "https://cordoba.gob.ar/passerini-entrego-mobiliario-a-comedores-y-merenderos-comunitarios-fabricado-con-scrap-de-industrias-cordobesas/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Community_dining.jpg/800px-Community_dining.jpg"],
     None),
    (160, "Merienda comunitaria", "Barrio Güemes, Salta",
     "Vecinos abrieron el merendero Tomás y la Pandilla del Bien para acompañar a pibes del barrio.",
     "https://www.vocescriticas.com/noticias/2026/06/12/204133-en-homenaje-a-tomas-arias-impulsan-un-merendero-y-preparan-una-jornada-solidaria-en-barrio-gemes",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/3/3b/Children_eating.jpg/800px-Children_eating.jpg"],
     None),
    (70, "Huerta barrial", "Santiago, Chile",
     "Fundación Huertas Comunitarias cumple 10 años: 110 huertas, 80 mil personas y casi 395 mil kilos cosechados.",
     "https://eldesconcierto.cl/hoja-ruta/diez-anos-huertas-comunitarias-110-proyectos-y-casi-400000-kilos-alimentos-cosechados-n5462049",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/7/7d/Urban_garden.jpg/800px-Urban_garden.jpg"],
     None),
    (200, "Limpieza de playa", "La Boquilla, Cartagena",
     "Global Shapers y pibes de Villa Gloria juntaron 55 kg de plástico y rescataron plántulas de mangle.",
     "https://www.eluniversal.com.co/cartagena/2026/02/24/limpieza-y-rescate-de-mangles-la-doble-jornada-de-global-shapers-en-la-boquilla/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Mangrove.jpg/800px-Mangrove.jpg"],
     None),
    (40, "Adopción", "Iztapalapa, CDMX",
     "Cereza y Cerecito, sobrevivientes de la explosión de 2025, cumplieron un año juntos en Huellitas Amor Sin Fronteras.",
     "https://www.milenio.com/comunidad/cereza-cerecito-perritos-sobrevivientes-iztapalapa-regresan-a-hogar",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/5/55/Rescue_dog.jpg/800px-Rescue_dog.jpg"],
     None),
    (15, "Fauna", "Nautla, Veracruz",
     "Fundación Yépez soltó entre 3.200 y 3.500 crías de tortuga marina en El Raudal.",
     "https://www.upi.com/Top_News/World-News/2026/09/01/latam-mexico-sea-turtles-released/1251788286661/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/6/6a/Sea_turtle_hatchling.jpg/800px-Sea_turtle_hatchling.jpg"],
     "https://upload.wikimedia.org/wikipedia/commons/transcoded/6/66/Baby_sea_turtles.webm/Baby_sea_turtles.webm.360p.vp9.webm"),
    (55, "Fauna", "Kertih, Malasia",
     "500 crías de tortuga verde de un día salieron al mar en Ma Daerah, con las Girl Guides de Terengganu.",
     "https://thesun.my/news/malaysia-news/people-issues/500-green-sea-turtle-hatchlings-released-in-kertih/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/b/bb/Green_turtle.jpg/800px-Green_turtle.jpg"],
     None),
    (85, "Fauna", "Río Aguarico, Ecuador",
     "Siete mujeres siekopai —las Turtle Women— incubaron 235 huevos de charapa y devolvieron 208 crías al río.",
     "https://timesofindia.indiatimes.com/science/meet-the-turtle-women-of-amazon-7-indigenous-women-are-bringing-vulnerable-river-turtles-back-from-the-brink-after-releasing-208-baby-turtles-into-the-wild/articleshow/133726609.cms",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Podocnemis_unifilis.jpg/800px-Podocnemis_unifilis.jpg"],
     None),
    (25, "Fauna", "El Campello, España",
     "Nacieron 44 tortugas boba en Muchavista. Voluntarios de Xaloc Mar cuidaron el nido de día y de noche.",
     "https://www.diariodealicante.net/en/sea-%E2%80%8B%E2%80%8Bturtle-hatchlings-born-Muchavista-Campello/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Loggerhead_sea_turtle.jpg/800px-Loggerhead_sea_turtle.jpg"],
     None),
    (45, "Comedor", "Jackson Ward, Richmond",
     "RVA Community Fridges instaló su heladera libre número 17 atrás de Mama J’s.",
     "https://www.wvtf.org/news/2026-09-04/rva-community-fridges-opens-its-17th-free-fridge-and-it-cant-keep-them-full",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/1/1f/Community_fridge.jpg/800px-Community_fridge.jpg"],
     None),
    (95, "Adopción", "CDMX",
     "Amar es Adoptar publica de a poco fichas con foto, talla y carácter. Piden que el hogar dure toda la vida del animal.",
     "https://dondeir.com/mascotas/cdmx-estrena-plataforma-para-adoptar-perros-rescatados-asi-funciona/2026/09/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Dog_for_adoption.jpg/800px-Dog_for_adoption.jpg"],
     None),
    (18, "Adopción", "Unicentro, Bogotá",
     "El IDPYBA sigue abriendo jornadas en shoppings. También buscan casa para adultos y perros con tratamiento.",
     "https://www.semana.com/4patas/perros/articulo/en-video-la-historia-de-pirata-patria-el-perro-que-fue-rescatado-en-un-estado-de-salud-grave-y-duro-casi-dos-anos-sin-hogar/202625/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/4/43/Puppy.jpg/800px-Puppy.jpg"],
     None),
    (42, "Adopción", "Cuajimalpa, CDMX",
     "156 perros volvieron al Franciscano y el Gobierno habilitó el canal formal de adopción responsable.",
     "https://www.cronica.com.mx/metropoli/2026/09/14/regresan-156-perros-al-refugio-franciscano-cdmx-inicia-programa-de-adopcion-responsable/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/b/b7/Beagle_puppy.jpg/800px-Beagle_puppy.jpg"],
     None),
    (130, "Fauna", "Veracruz, México",
     "En el Santuario Playas del Totonacapan soltaron 2.505 crías durante el festival, con 14 técnicos comunitarios.",
     "https://www.upi.com/Top_News/World-News/2026/09/01/latam-mexico-sea-turtles-released/1251788286661/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/6/66/Baby_sea_turtles.jpg/800px-Baby_sea_turtles.jpg"],
     None),
    (210, "Cuidado barrial", "La Boquilla, Colombia",
     "El plástico de la jornada entra al proyecto Mangle para reciclarse en objetos nuevos, no al relleno.",
     "https://www.eluniversal.com.co/cartagena/2026/02/24/limpieza-y-rescate-de-mangles-la-doble-jornada-de-global-shapers-en-la-boquilla/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Plastic_pollution_cleanup.jpg/800px-Plastic_pollution_cleanup.jpg"],
     None),
    (150, "Reforestación", "Santa Fe, Argentina",
     "Más de 150 voluntarios limpiaron el Bajo Oroño y el Parque Federal: 35 árboles nuevos y rastrilleo de playa.",
     "https://veonoticias.com/santa-fe-limpieza-reforestacion-y-mantenimiento-en-el-bajo-orono-y-el-parque-federal/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/a/a8/Tree_planting_volunteers.jpg/800px-Tree_planting_volunteers.jpg"],
     None),
    (26, "Merienda comunitaria", "Santiago del Estero",
     "El merendero Los Chifladitos pide galletas y pan para seguir recibiendo a 70 chicos cada miércoles.",
     "https://infodelestero.com/2026/09/15/un-merendero-santiagueno-pide-ayuda-para-seguir-acompanando-a-70-ninos-cada-miercoles",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/2/2c/Children_sharing_food.jpg/800px-Children_sharing_food.jpg"],
     None),
    (22, "Comedor", "Villa Aeroparque, Uruguay",
     "Fuego Solidario cocina con los merenderos, no por ellos. En Nuestra Fe acompañan a 58-68 chicos de unas 20 familias.",
     "https://elmegafono.uy/fuego-solidario-cocinar-para-acompanar-y-visibilizar-el-trabajo-de-los-merenderos/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/d/d2/Volunteers_cooking.jpg/800px-Volunteers_cooking.jpg"],
     None),
    (11, "Adopción", "Winter Park, Florida",
     "Pilotos voluntarios de Puppy Rescue Flights ya trasladaron más de 12.000 perros y gatos desde refugios saturados.",
     "https://www.wftv.com/news/local/winter-park-nonprofit-transports-12000-animals-florida-rescues/QBDFXKCZDRBBDPOQ2GV27PIU4E/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/1/14/Rescue_dogs.jpg/800px-Rescue_dogs.jpg"],
     None),
    (65, "Océano", "Breton Bay, Maryland",
     "Voluntarios plantaron un millón de ostras bebés en Breton Bay. Desde 2017 la comunidad ya soltó más de 4,5 millones.",
     "https://thebaynet.com/shell-yeah-volunteers-wanted-to-plant-1-million-oysters-in-breton-bay/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/5/54/Oyster_reef.jpg/800px-Oyster_reef.jpg"],
     None),
    (14, "Limpieza de playa", "Miami-Dade, Florida",
     "La 41ª International Coastal Cleanup suma 65 puntos y 4.000 voluntarios en el condado.",
     "https://volunteercleanup.org/event/rickenbacker-marina-with-rescue-a-reef-icc2026",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/Beach_cleanup_volunteers.jpg/800px-Beach_cleanup_volunteers.jpg"],
     None),
    (7, "Comedor", "Kondangal, Telangana",
     "En el piloto de Kondangal la asistencia escolar subió del 75% al 90% con el desayuno.",
     "https://www.thehindu.com/news/national/telangana/8-crore-centralised-kitchen-opened-in-karimnagar-to-serve-breakfast-to-49025-students/article71472979.ece",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/0/0b/Midday_meal.jpg/800px-Midday_meal.jpg"],
     None),
    (112, "Limpieza de playa", "Oahu, Hawái",
     "En Coralpalooza 2026, una sola playa de Oahu sacó 2.709 libras de basura.",
     "https://coralrestoration.org/celebrating-coralpalooza-2026-around-the-world/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Hawaii_beach.jpg/800px-Hawaii_beach.jpg"],
     None),
    (148, "Cuidado barrial", "Parque Federal, Santa Fe",
     "Más de 100 scouts de Santa Fe y Recreo mantuvieron el Parque Federal: flora, residuos y un taller de emergencias.",
     "https://veonoticias.com/santa-fe-limpieza-reforestacion-y-mantenimiento-en-el-bajo-orono-y-el-parque-federal/",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/2/27/Scout_volunteers.jpg/800px-Scout_volunteers.jpg"],
     None),
    (13, "Limpieza de playa", "Biscayne National Park, Florida",
     "Voluntarios cruzan en lancha a Elliott Key, entran al agua hasta la cintura y pesan la basura al volver.",
     "https://volunteercleanup.org/event/2026-international-coastal-cleanup-at-biscayne-national-park",
     ["https://upload.wikimedia.org/wikipedia/commons/thumb/d/d5/Biscayne_National_Park.jpg/800px-Biscayne_National_Park.jpg"],
     None),
]

AUTHORS = [
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222",
    "33333333-3333-3333-3333-333333333333",
    "44444444-4444-4444-4444-444444444444",
]
FALLBACK = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/800px-PNG_transparency_demonstration_1.png"

def uid(prefix: str, n: int) -> str:
    return f"{prefix}000000-0000-4000-8000-{n:012d}"

lines = [
    "-- 40 noticias reales (resumen + source_url + 1..N post_media IMAGE/VIDEO).",
    "-- Autores: Ana / Bruno / Carla / Diego del seed de lab.",
    "",
]
for i, (hours, tag, place, body, source, images, video) in enumerate(NEWS, start=1):
    post_id = uid("a1", i)
    author = AUTHORS[(i - 1) % 4]
    urls = images or [FALLBACK]
    arr = ",".join("'" + u.replace("'", "''") + "'" for u in urls)
    body_sql = body.replace("'", "''")
    place_sql = place.replace("'", "''")
    tag_sql = tag.replace("'", "''")
    source_sql = source.replace("'", "''")
    impact = 40 + (i * 3) % 180
    lines.append(
        f"INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, created_at)\n"
        f"VALUES ('{post_id}', 'USER', '{author}', '{body_sql}', ARRAY[{arr}]::TEXT[], {impact}, {(i % 5) + 1}, '{tag_sql}', '{source_sql}', now() - interval '{hours} hours');\n"
    )
    lines.append(
        f"INSERT INTO post_people (post_id, user_id, role) VALUES ('{post_id}', '{author}', 'AUTHOR'), ('{post_id}', '{author}', 'PROTAGONIST');\n"
    )
    for order, url in enumerate(urls):
        mid = uid("b1", i * 10 + order)
        url_sql = url.replace("'", "''")
        lines.append(
            f"INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)\n"
            f"VALUES ('{mid}', '{post_id}', 'IMAGE', '{url_sql}', {order}, '{tag_sql}', '{source_sql}');\n"
        )
    if video:
        mid = uid("b1", i * 10 + 9)
        vid = video.replace("'", "''")
        poster = urls[0].replace("'", "''")
        lines.append(
            f"INSERT INTO post_media (id, post_id, kind, url, poster_url, sort_order, duration_ms, alt_text, source_url)\n"
            f"VALUES ('{mid}', '{post_id}', 'VIDEO', '{vid}', '{poster}', {len(urls)}, 28000, 'Video de {tag_sql}', '{source_sql}');\n"
        )
    lines.append("")

out = Path("/Users/romancanoniero/.cursor/OnlyGoodThings/database/migrations/05_news_seed.sql")
out.write_text("".join(lines) + "COMMENT ON TABLE post_media IS 'Carrusel Instagram: 1 a 10 piezas IMAGE/VIDEO por post.';\n", encoding="utf-8")
print(f"wrote {out} ({len(NEWS)} posts)")
