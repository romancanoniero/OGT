-- 10 piezas editoriales surtidas. Las fotos viven en disco de la API (/media/u/{uuid}.jpg).
-- En la VPS se bajan con scripts/import-editorial-vps.sh; no se hotlinkea Commons.

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000001', 'USER', '11111111-1111-1111-1111-111111111111',
    'En febrero volvieron 158 tortugas de entre 12 y 14 años a Floreana. El Parque Nacional las crió hasta que pudieran solas. Cada una lleva un GPS liviano. Hacía más de 180 años que no había quelonios en esa isla.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000001.jpg']::TEXT[],
    0, 0, 'Llegó a casa',
    'https://galapagos.gob.ec/un-regreso-a-casa-para-floreana/',
    ST_SetSRID(ST_MakePoint(-90.433, -1.300), 4326),
    now() - interval '4 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000001', 'a2000000-0000-4000-8000-000000000001', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000001.jpg',
    0, 'Tortuga gigante de caparazón alto sobre tierra seca',
    'https://galapagos.gob.ec/un-regreso-a-casa-para-floreana/'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000002', 'USER', '22222222-2222-2222-2222-222222222222',
    'Defensa Civil los levantó en Costanera Sur y en el Club de Pescadores. En el Ecoparque los rehabilitaron. Después recorrieron más de 340 kilómetros, con Mundo Marino, hasta otra vez el mar.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000002.jpg']::TEXT[],
    0, 0, 'Rescate animal',
    'https://www.lanacion.com.ar/sociedad/el-emocionante-video-de-dos-lobos-marinos-en-libertad-aparecieron-en-el-lugar-menos-pensado-de-la-nid07092026/',
    ST_SetSRID(ST_MakePoint(-58.351, -34.617), 4326),
    now() - interval '10 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000002', '22222222-2222-2222-2222-222222222222', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000002', '22222222-2222-2222-2222-222222222222', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000002', 'a2000000-0000-4000-8000-000000000002', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000002.jpg',
    0, 'Dos lobos marinos sobre las rocas',
    'https://www.lanacion.com.ar/sociedad/el-emocionante-video-de-dos-lobos-marinos-en-libertad-aparecieron-en-el-lugar-menos-pensado-de-la-nid07092026/'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000003', 'USER', '33333333-3333-3333-3333-333333333333',
    'Astra Milagros tiene 14 años. El IDPYBA la atiende desde 2019. El 19 de julio, en El Muelle, Engativá, estaba con más de 16 perros y gatos rescatados, a la espera de una casa que dure.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000003.jpg']::TEXT[],
    0, 0, 'Adopción',
    'https://www.animalesbog.gov.co/noticias-pyba/hogar-dulce-hogar-la-gran-jornada-de-adopcion-del-idpyba',
    ST_SetSRID(ST_MakePoint(-74.147, 4.711), 4326),
    now() - interval '18 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000003', '33333333-3333-3333-3333-333333333333', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000003', '33333333-3333-3333-3333-333333333333', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000003', 'a2000000-0000-4000-8000-000000000003', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000003.jpg',
    0, 'Perro mestizo de pelo corto mirando de frente',
    'https://www.animalesbog.gov.co/noticias-pyba/hogar-dulce-hogar-la-gran-jornada-de-adopcion-del-idpyba'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000004', 'USER', '44444444-4444-4444-4444-444444444444',
    'En El Impenetrable le pusieron un collar GPS al segundo tatú carreta del parque. El equipo de CeIBA y Parques lo revisó y lo soltó en el mismo claro. Es el armadillo más grande del mundo.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000004.jpg']::TEXT[],
    0, 0, 'Fauna',
    'https://www.argentina.gob.ar/noticias/segundo-tatu-carreta-monitoreado-en-el-parque-nacional-el-impenetrable',
    ST_SetSRID(ST_MakePoint(-61.200, -25.000), 4326),
    now() - interval '26 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000004', '44444444-4444-4444-4444-444444444444', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000004', '44444444-4444-4444-4444-444444444444', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000004', 'a2000000-0000-4000-8000-000000000004', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000004.jpg',
    0, 'Tatú carreta de caparazón oscuro sobre el suelo',
    'https://www.argentina.gob.ar/noticias/segundo-tatu-carreta-monitoreado-en-el-parque-nacional-el-impenetrable'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000005', 'USER', '11111111-1111-1111-1111-111111111111',
    'En el faldeo del Cerro El Dedal plantaron 4.500 coihues y cipreses de semilla local. El bosque se había quemado en 2015. Voluntarios y Parques Nacionales los bajaron al suelo, uno por uno.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000005.jpg']::TEXT[],
    0, 0, 'Reforestación',
    'https://www.argentina.gob.ar/noticias/comenzo-la-plantacion-de-4500-arboles-en-el-parque-nacional-los-alerces',
    ST_SetSRID(ST_MakePoint(-71.750, -42.800), 4326),
    now() - interval '34 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111111', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000005', '11111111-1111-1111-1111-111111111111', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000005', 'a2000000-0000-4000-8000-000000000005', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000005.jpg',
    0, 'Coihue de tronco claro y copa abierta',
    'https://www.argentina.gob.ar/noticias/comenzo-la-plantacion-de-4500-arboles-en-el-parque-nacional-los-alerces'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000006', 'USER', '22222222-2222-2222-2222-222222222222',
    'En la Huerta Municipal de Berazategui anunciaron más de 550.000 plantines para el otoño: kale, verdeo, lechuga, acelga. Hay 45 puntos de entrega. Desde 2024 ya pasaron 1,5 millones por 125 municipios.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000006.jpg']::TEXT[],
    0, 0, 'Huerta comunitaria',
    'https://gba.gob.ar/desarrollo_agrario/Noticias/rodr%C3%ADguez_lanz%C3%B3_una_nueva_edici%C3%B3n_del_programa_huertas_urbanas',
    ST_SetSRID(ST_MakePoint(-58.213, -34.764), 4326),
    now() - interval '42 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000006', '22222222-2222-2222-2222-222222222222', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000006', '22222222-2222-2222-2222-222222222222', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000006', 'a2000000-0000-4000-8000-000000000006', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000006.jpg',
    0, 'Huerta comunitaria con surcos y vegetales',
    'https://gba.gob.ar/desarrollo_agrario/Noticias/rodr%C3%ADguez_lanz%C3%B3_una_nueva_edici%C3%B3n_del_programa_huertas_urbanas'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000007', 'USER', '33333333-3333-3333-3333-333333333333',
    'Cien voluntarios, con ANA, el municipio y SERNANP, juntaron dos mil kilos en la ribera de Paracas. Había redes, sogas y costales. Antes les dieron una charla y un equipo para no lastimarse.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000007.jpg']::TEXT[],
    0, 0, 'Limpieza de playa',
    'https://www.gob.pe/institucion/ana/noticias/1185341-con-la-participacion-de-100-voluntarios-la-ana-retira-dos-mil-kilos-de-residuos-solidos-de-la-ribera-de-playa-en-el-mar-de-paracas',
    ST_SetSRID(ST_MakePoint(-76.250, -13.840), 4326),
    now() - interval '50 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000007', '33333333-3333-3333-3333-333333333333', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000007', '33333333-3333-3333-3333-333333333333', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000007', 'a2000000-0000-4000-8000-000000000007', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000007.jpg',
    0, 'Costa desierta de la Reserva Nacional de Paracas',
    'https://www.gob.pe/institucion/ana/noticias/1185341-con-la-participacion-de-100-voluntarios-la-ana-retira-dos-mil-kilos-de-residuos-solidos-de-la-ribera-de-playa-en-el-mar-de-paracas'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000008', 'USER', '44444444-4444-4444-4444-444444444444',
    'A última hora de la tarde abrieron la guillotina. Acaí, nacida libre en Iberá, se metió al monte del Impenetrable. Es la quinta hembra suelta. Ahí ya andan Keraná, Nalá, Miní y Quiyoc.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000008.jpg']::TEXT[],
    0, 0, 'Fauna',
    'https://www.argentina.gob.ar/noticias/nueva-liberacion-de-una-hembra-de-yaguarete-en-el-parque-nacional-el-impenetrable',
    ST_SetSRID(ST_MakePoint(-61.180, -24.980), 4326),
    now() - interval '58 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000008', '44444444-4444-4444-4444-444444444444', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000008', '44444444-4444-4444-4444-444444444444', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000008', 'a2000000-0000-4000-8000-000000000008', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000008.jpg',
    0, 'Yaguareté de perfil sobre un tronco',
    'https://www.argentina.gob.ar/noticias/nueva-liberacion-de-una-hembra-de-yaguarete-en-el-parque-nacional-el-impenetrable'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000009', 'USER', '11111111-1111-1111-1111-111111111111',
    'Un equipo de 16 personas sacó 298.180 libras de redes y plásticos de Papahānaumokuākea, entre abril y octubre. Entre las redes había cuatro tortugas verdes hawaianas. Las desenredaron y las soltaron.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000009.jpg']::TEXT[],
    0, 0, 'Océano',
    'https://www.noaa.gov/news/marine-debris-removal-season-at-pacific-monument-ends-with-record-breaking-results',
    ST_SetSRID(ST_MakePoint(-177.370, 28.210), 4326),
    now() - interval '66 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000009', '11111111-1111-1111-1111-111111111111', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000009', '11111111-1111-1111-1111-111111111111', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000009', 'a2000000-0000-4000-8000-000000000009', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000009.jpg',
    0, 'Tortuga verde nadando sobre coral',
    'https://www.noaa.gov/news/marine-debris-removal-season-at-pacific-monument-ends-with-record-breaking-results'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000010', 'USER', '22222222-2222-2222-2222-222222222222',
    'Cuando Amparo Gavidia cumplió 100, el Concejo de Munera le puso su nombre al Auditorio Municipal. Fue la primera bibliotecaria del Cervantes, en 1966, y dirigió la escuela del pueblo.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000010.jpg']::TEXT[],
    0, 0, 'En vida',
    'https://munera.es/plenos-actas/',
    ST_SetSRID(ST_MakePoint(-2.482, 39.039), 4326),
    now() - interval '74 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000010', '22222222-2222-2222-2222-222222222222', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000010', '22222222-2222-2222-2222-222222222222', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000010', 'a2000000-0000-4000-8000-000000000010', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000010.jpg',
    0, 'Estantería de biblioteca con lomos de libros',
    'https://munera.es/plenos-actas/'
);
