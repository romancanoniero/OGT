-- 20 piezas de figuras argentinas: optimismo, patria y prójimo.
-- Se suman al río; no reemplazan las 10 editoriales de 05_news_seed.sql.
-- Fotos en disco: scripts/import-argentina-celebrities-vps.sh

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000011', 'USER', '11111111-1111-1111-1111-111111111111',
    'En 1971 René Favaloro dejó la Cleveland Clinic y volvió. El último tercio de su vida, escribió, era para levantar en Buenos Aires un centro como el que lo había formado. En 1975 nació la Fundación. Los únicos privilegiados, decía, son los pacientes.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000011.jpg']::TEXT[],
    0, 0, 'En vida',
    'https://www.fundacionfavaloro.org/biografia/',
    ST_SetSRID(ST_MakePoint(-58.388, -34.612), 4326),
    now() - interval '1 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000011', '11111111-1111-1111-1111-111111111111', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000011', '11111111-1111-1111-1111-111111111111', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000011', 'a2000000-0000-4000-8000-000000000011', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000011.jpg',
    0, 'René Favaloro de perfil, con anteojos',
    'https://www.fundacionfavaloro.org/biografia/'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000012', 'USER', '22222222-2222-2222-2222-222222222222',
    'Después de Tucumán y Salta le ofrecieron cuarenta mil pesos. El 31 de marzo de 1813, desde Jujuy, Belgrano escribió que ese dinero no era para él: iba a cuatro escuelas en Tarija, Jujuy, Tucumán y Santiago del Estero. A los maestros les pidió preferir el bien público al privado.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000012.jpg']::TEXT[],
    0, 0, 'Enseñanza',
    'https://www.argentina.gob.ar/noticias/20-de-junio-promesa-de-lealtad-la-bandera',
    ST_SetSRID(ST_MakePoint(-65.302, -24.185), 4326),
    now() - interval '5 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000012', '22222222-2222-2222-2222-222222222222', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000012', '22222222-2222-2222-2222-222222222222', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000012', 'a2000000-0000-4000-8000-000000000012', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000012.jpg',
    0, 'Retrato de Manuel Belgrano de uniforme',
    'https://www.argentina.gob.ar/noticias/20-de-junio-promesa-de-lealtad-la-bandera'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000013', 'USER', '33333333-3333-3333-3333-333333333333',
    'El 5 de agosto de 2014, a los 83, Estela de Carlotto supo que su nieto vivía en Olavarría. Lo había buscado 36 años. Dos días después se abrazaron. Era el nieto 114 de Abuelas. Ignacio, que también es Guido, esa tarde seguía tocando el piano.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000013.jpg']::TEXT[],
    0, 0, 'En vida',
    'https://www.bbc.com/mundo/noticias/2014/08/140805_argentina_estela_carlotto_guido_busqueda_nieto_irm',
    ST_SetSRID(ST_MakePoint(-57.955, -34.921), 4326),
    now() - interval '8 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000013', '33333333-3333-3333-3333-333333333333', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000013', '33333333-3333-3333-3333-333333333333', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000013', 'a2000000-0000-4000-8000-000000000013', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000013.jpg',
    0, 'Estela de Carlotto con pañuelo blanco',
    'https://www.bbc.com/mundo/noticias/2014/08/140805_argentina_estela_carlotto_guido_busqueda_nieto_irm'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000014', 'USER', '44444444-4444-4444-4444-444444444444',
    'El 12 de agosto de 1821, en Lima, San Martín firmó que los hijos de esclavas nacidos desde la independencia serían libres. Escribió que, si la humanidad fue ultrajada, lo justo es dar el primer paso. También donó sus libros a la Biblioteca Pública de Lima.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000014.jpg']::TEXT[],
    0, 0, 'Homenaje',
    'https://www.argentina.gob.ar/cultura/instituto-nacional-sanmartiniano',
    ST_SetSRID(ST_MakePoint(-68.845, -32.890), 4326),
    now() - interval '12 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000014', '44444444-4444-4444-4444-444444444444', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000014', '44444444-4444-4444-4444-444444444444', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000014', 'a2000000-0000-4000-8000-000000000014', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000014.jpg',
    0, 'Retrato de José de San Martín de uniforme',
    'https://www.argentina.gob.ar/cultura/instituto-nacional-sanmartiniano'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000015', 'USER', '11111111-1111-1111-1111-111111111111',
    'La Fundación Leo Messi, con UNICEF, pagó el 60 por ciento de la Residencia para Madres del Hospital Mi Pueblo, en Florencio Varela. Doce camas para que una mujer del conurbano no tenga que volver de noche si el chico se queda internado.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000015.jpg']::TEXT[],
    0, 0, 'Cuidado comunitario',
    'https://messi.com/ca/fundacionacciones/donacion-unicef/',
    ST_SetSRID(ST_MakePoint(-58.275, -34.797), 4326),
    now() - interval '16 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000015', '11111111-1111-1111-1111-111111111111', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000015', '11111111-1111-1111-1111-111111111111', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000015', 'a2000000-0000-4000-8000-000000000015', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000015.jpg',
    0, 'Lionel Messi con la camiseta de la Selección',
    'https://messi.com/ca/fundacionacciones/donacion-unicef/'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000016', 'USER', '22222222-2222-2222-2222-222222222222',
    'En Oslo, en 1980, Adolfo Pérez Esquivel dijo que el Nobel no era de él: era de los más chicos, de los que no tienen voz. Lo recibió como coordinador del SERPAJ, después de años de noviolencia. La paz, dijo, solo es fruto de la justicia.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000016.jpg']::TEXT[],
    0, 0, 'Gracias',
    'https://www.nobelprize.org/prizes/peace/1980/esquivel/acceptance-speech/',
    ST_SetSRID(ST_MakePoint(-58.373, -34.608), 4326),
    now() - interval '20 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000016', '22222222-2222-2222-2222-222222222222', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000016', '22222222-2222-2222-2222-222222222222', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000016', 'a2000000-0000-4000-8000-000000000016', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000016.jpg',
    0, 'Adolfo Pérez Esquivel en 1983',
    'https://www.nobelprize.org/prizes/peace/1980/esquivel/acceptance-speech/'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000017', 'USER', '33333333-3333-3333-3333-333333333333',
    'En 1997 el arzobispo Bergoglio mandó al padre Pepe a la Villa 21-24. Después creó la Vicaría de Villas. Un vecino lo invitó a fideos con tuco. Aceptó. Dijo que le gusta sentarse en la mesa de los pobres porque ahí se comparte el corazón.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000017.jpg']::TEXT[],
    0, 0, 'Comedor',
    'https://www.vaticannews.va/es/iglesia/news/2025-04/buenos-aires-villas-populares-oracion-papa-francisco.html',
    ST_SetSRID(ST_MakePoint(-58.380, -34.650), 4326),
    now() - interval '24 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000017', '33333333-3333-3333-3333-333333333333', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000017', '33333333-3333-3333-3333-333333333333', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000017', 'a2000000-0000-4000-8000-000000000017', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000017.jpg',
    0, 'Jorge Mario Bergoglio de cardenal',
    'https://www.vaticannews.va/es/iglesia/news/2025-04/buenos-aires-villas-populares-oracion-papa-francisco.html'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000018', 'USER', '44444444-4444-4444-4444-444444444444',
    'El 19 de junio de 1948 Evita inauguró el Hogar de Tránsito Nº 2 en Lafinur. Era para mujeres con hijos que no tenían techo. Amparar al que no tiene casa, escribió, hasta que tenga trabajo y se sienta orgulloso de ser argentino. Hoy es el Museo Evita.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000018.jpg']::TEXT[],
    0, 0, 'Cuidado comunitario',
    'https://museoevita.org.ar/el-hogar-de-transito-no-2/',
    ST_SetSRID(ST_MakePoint(-58.405, -34.582), 4326),
    now() - interval '28 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000018', '44444444-4444-4444-4444-444444444444', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000018', '44444444-4444-4444-4444-444444444444', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000018', 'a2000000-0000-4000-8000-000000000018', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000018.jpg',
    0, 'Retrato de Eva Perón',
    'https://museoevita.org.ar/el-hogar-de-transito-no-2/'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000019', 'USER', '11111111-1111-1111-1111-111111111111',
    'De presidente, Sarmiento sembró más de 800 escuelas. La matrícula pasó de 30 mil a 110 mil chicos. Trajo maestras y fundó la Normal de Paraná. En el sitio oficial lo recuerdan así: la escuela como pilar de la Nación.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000019.jpg']::TEXT[],
    0, 0, 'Enseñanza',
    'https://www.argentina.gob.ar/noticias/domingo-faustino-sarmiento-la-escuela-como-pilar-de-la-nacion',
    ST_SetSRID(ST_MakePoint(-68.536, -31.537), 4326),
    now() - interval '32 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000019', '11111111-1111-1111-1111-111111111111', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000019', '11111111-1111-1111-1111-111111111111', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000019', 'a2000000-0000-4000-8000-000000000019', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000019.jpg',
    0, 'Retrato de Domingo Faustino Sarmiento',
    'https://www.argentina.gob.ar/noticias/domingo-faustino-sarmiento-la-escuela-como-pilar-de-la-nacion'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000020', 'USER', '22222222-2222-2222-2222-222222222222',
    'El 10 de diciembre de 1983 Raúl Alfonsín juró en el Congreso. La democracia volvía. Dijo que la casa estaba en orden. No era un eslogan: era la promesa de que el vecino podía dormir sin miedo y elegir de nuevo.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000020.jpg']::TEXT[],
    0, 0, 'En vida',
    'https://www.casarosada.gob.ar/la-casa-rosada/bustos-presidenciales',
    ST_SetSRID(ST_MakePoint(-58.370, -34.608), 4326),
    now() - interval '36 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000020', '22222222-2222-2222-2222-222222222222', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000020', '22222222-2222-2222-2222-222222222222', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000020', 'a2000000-0000-4000-8000-000000000020', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000020.jpg',
    0, 'Raúl Alfonsín saluda a funcionarios',
    'https://www.casarosada.gob.ar/la-casa-rosada/bustos-presidenciales'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000021', 'USER', '33333333-3333-3333-3333-333333333333',
    'El 18 de febrero de 1982 Mercedes Sosa volvió al Teatro Ópera. Trece noches llenas. Arrancó con Los hermanos, de Yupanqui. Cuando cantó que hay una hermana que se llama libertad, el teatro se paró. Todavía gobernaban los militares.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000021.jpg']::TEXT[],
    0, 0, 'En vida',
    'https://www.radionacional.com.ar/mercedes-sosa-su-regreso-del-exilio-y-sus-conciertos-multitudinarios/',
    ST_SetSRID(ST_MakePoint(-58.384, -34.604), 4326),
    now() - interval '40 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000021', '33333333-3333-3333-3333-333333333333', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000021', '33333333-3333-3333-3333-333333333333', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000021', 'a2000000-0000-4000-8000-000000000021', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000021.jpg',
    0, 'Mercedes Sosa cantando con los ojos cerrados',
    'https://www.radionacional.com.ar/mercedes-sosa-su-regreso-del-exilio-y-sus-conciertos-multitudinarios/'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000022', 'USER', '44444444-4444-4444-4444-444444444444',
    'Después del temporal del 16 de diciembre de 2023 en Bahía, Manu Ginóbili armó Fuerza Bahía. La intendencia dijo que la campaña, por su Fundación, juntó unos 5 millones de dólares, auditados. Comida para los barrios que se habían quedado sin techo.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000022.jpg']::TEXT[],
    0, 0, 'Ayuda vecinal',
    'https://dib.com.ar/2023/12/bahia-blanca-la-campana-solidaria-impulsad-por-ginobili-ya-recaudo-5-millones-de-dolares',
    ST_SetSRID(ST_MakePoint(-62.266, -38.719), 4326),
    now() - interval '44 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000022', '44444444-4444-4444-4444-444444444444', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000022', '44444444-4444-4444-4444-444444444444', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000022', 'a2000000-0000-4000-8000-000000000022', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000022.jpg',
    0, 'Emanuel Ginóbili con la camiseta de la Selección',
    'https://dib.com.ar/2023/12/bahia-blanca-la-campana-solidaria-impulsad-por-ginobili-ya-recaudo-5-millones-de-dolares'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000023', 'USER', '11111111-1111-1111-1111-111111111111',
    'Carlos Saavedra Lamas medió para que Bolivia y Paraguay dejaran de matarse en el Chaco. En 1935 se firmó el protocolo en Buenos Aires. En 1936 le dieron el Nobel de la Paz. El pacto que lleva su nombre condenaba tomar tierra por las armas.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000023.jpg']::TEXT[],
    0, 0, 'Homenaje',
    'https://cancilleria.gob.ar/es/institucional/patrimonio/museo-de-la-diplomacia-argentina/saavedra-lamas-premio-nobel-de-la-paz',
    ST_SetSRID(ST_MakePoint(-58.372, -34.610), 4326),
    now() - interval '48 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000023', '11111111-1111-1111-1111-111111111111', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000023', '11111111-1111-1111-1111-111111111111', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000023', 'a2000000-0000-4000-8000-000000000023', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000023.jpg',
    0, 'Retrato de Carlos Saavedra Lamas',
    'https://cancilleria.gob.ar/es/institucional/patrimonio/museo-de-la-diplomacia-argentina/saavedra-lamas-premio-nobel-de-la-paz'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000024', 'USER', '22222222-2222-2222-2222-222222222222',
    'Cecilia Grierson se recibió de médica el 2 de julio de 1889, la primera del país. Tres años antes había fundado la Escuela de Enfermeras. Entró a Medicina porque su amiga Amalia se estaba muriendo y nadie sabía cómo cuidarla.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000024.jpg']::TEXT[],
    0, 0, 'Enseñanza',
    'https://buenosaires.gob.ar/gcaba_historico/noticias/homenaje-cecilia-grierson-maestra-y-medica',
    ST_SetSRID(ST_MakePoint(-58.392, -34.607), 4326),
    now() - interval '52 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000024', '22222222-2222-2222-2222-222222222222', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000024', '22222222-2222-2222-2222-222222222222', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000024', 'a2000000-0000-4000-8000-000000000024', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000024.jpg',
    0, 'Retrato de Cecilia Grierson',
    'https://buenosaires.gob.ar/gcaba_historico/noticias/homenaje-cecilia-grierson-maestra-y-medica'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000025', 'USER', '33333333-3333-3333-3333-333333333333',
    'En 1979 Quino le regaló a UNICEF las viñetas de Mafalda para los derechos del niño. No cobró el dibujo. Tres décadas después UNICEF todavía las publica. Una nena de pelo negro preguntando por el otro: ese era el país que él quería.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000025.jpg']::TEXT[],
    0, 0, 'Enseñanza',
    'https://www.unicef.org/lac/historias/10-derechos-fundamentales-de-la-infancia-por-quino',
    ST_SetSRID(ST_MakePoint(-68.827, -32.890), 4326),
    now() - interval '56 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000025', '33333333-3333-3333-3333-333333333333', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000025', '33333333-3333-3333-3333-333333333333', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000025', 'a2000000-0000-4000-8000-000000000025', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000025.jpg',
    0, 'Quino con anteojos y suéter',
    'https://www.unicef.org/lac/historias/10-derechos-fundamentales-de-la-infancia-por-quino'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000026', 'USER', '44444444-4444-4444-4444-444444444444',
    'En 1943 echaron a Houssay de la UBA por pedir democracia. Le ofrecieron cátedras afuera. Se quedó. Armó el IBYME con plata privada. En 1947 fue el primer Nobel científico de América Latina. Después presidió el CONICET.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000026.jpg']::TEXT[],
    0, 0, 'Enseñanza',
    'https://ibyme.org.ar/historia-ibyme/origen-del-ibyme/',
    ST_SetSRID(ST_MakePoint(-58.420, -34.588), 4326),
    now() - interval '60 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000026', '44444444-4444-4444-4444-444444444444', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000026', '44444444-4444-4444-4444-444444444444', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000026', 'a2000000-0000-4000-8000-000000000026', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000026.jpg',
    0, 'Bernardo Houssay de traje',
    'https://ibyme.org.ar/historia-ibyme/origen-del-ibyme/'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000027', 'USER', '11111111-1111-1111-1111-111111111111',
    'Güemes sostuvo el norte con los Infernales mientras San Martín cruzaba los Andes. Frenó nueve invasiones realistas. Herido en 1821, dijo que se iba tranquilo porque detrás quedaban ellos, que sabrían defender la patria. Murió el 17 de junio.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000027.jpg']::TEXT[],
    0, 0, 'Homenaje',
    'https://www.argentina.gob.ar/noticias/martin-miguel-de-guemes-y-su-ejercito-de-infernales',
    ST_SetSRID(ST_MakePoint(-65.410, -24.789), 4326),
    now() - interval '64 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000027', '11111111-1111-1111-1111-111111111111', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000027', '11111111-1111-1111-1111-111111111111', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000027', 'a2000000-0000-4000-8000-000000000027', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000027.jpg',
    0, 'Retrato de Martín Miguel de Güemes',
    'https://www.argentina.gob.ar/noticias/martin-miguel-de-guemes-y-su-ejercito-de-infernales'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000028', 'USER', '22222222-2222-2222-2222-222222222222',
    'El 9 de diciembre de 2017 Del Potro entrenó a puertas abiertas en Tandil. La entrada era un alimento no perecedero para el Banco de Alimentos. La cola dio la vuelta. No hizo discurso: dejó que la ciudad le llevara fideos.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000028.jpg']::TEXT[],
    0, 0, 'Ayuda vecinal',
    'https://www.infobae.com/deportes-2/2018/09/10/infobae-en-tandil-las-acciones-solidarias-y-silenciosas-de-juan-martin-del-potro-en-su-ciudad/',
    ST_SetSRID(ST_MakePoint(-59.137, -37.328), 4326),
    now() - interval '68 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000028', '22222222-2222-2222-2222-222222222222', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000028', '22222222-2222-2222-2222-222222222222', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000028', 'a2000000-0000-4000-8000-000000000028', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000028.jpg',
    0, 'Juan Martín del Potro con la raqueta',
    'https://www.infobae.com/deportes-2/2018/09/10/infobae-en-tandil-las-acciones-solidarias-y-silenciosas-de-juan-martin-del-potro-en-su-ciudad/'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000029', 'USER', '33333333-3333-3333-3333-333333333333',
    'Carlos Mugica eligió la Villa 31. Construyó la capilla Cristo Obrero con su hermano y los vecinos. Lo mataron el 11 de mayo de 1974, al salir de misa. En 1999 sus restos volvieron a esa capilla. Bergoglio ofició y pidió rezar también por los silencios.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000029.jpg']::TEXT[],
    0, 0, 'Cuidado comunitario',
    'https://buenosaires.gob.ar/areas/cultura/cpphc/sitios/detalle.php?id=37',
    ST_SetSRID(ST_MakePoint(-58.378, -34.586), 4326),
    now() - interval '72 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000029', '33333333-3333-3333-3333-333333333333', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000029', '33333333-3333-3333-3333-333333333333', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000029', 'a2000000-0000-4000-8000-000000000029', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000029.jpg',
    0, 'Carlos Mugica de sacerdote',
    'https://buenosaires.gob.ar/areas/cultura/cpphc/sitios/detalle.php?id=37'
);

INSERT INTO social_posts (id, author_kind, author_user_id, body, media_urls, impact_count, comment_count, topic, source_url, location, created_at)
VALUES (
    'a2000000-0000-4000-8000-000000000030', 'USER', '44444444-4444-4444-4444-444444444444',
    'En 1816 nombraron teniente coronela a Juana Azurduy. Belgrano le entregó el sable. Había armado milicias con más de diez mil originarios y peleó más de treinta veces. Murió pobre. En 2009 el país la ascendió a generala.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000030.jpg']::TEXT[],
    0, 0, 'Homenaje',
    'https://www.argentina.gob.ar/noticias/quien-fue-juana-azurduy',
    ST_SetSRID(ST_MakePoint(-65.300, -24.185), 4326),
    now() - interval '76 hours'
);
INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000030', '44444444-4444-4444-4444-444444444444', 'AUTHOR'),
    ('a2000000-0000-4000-8000-000000000030', '44444444-4444-4444-4444-444444444444', 'PROTAGONIST');
INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000030', 'a2000000-0000-4000-8000-000000000030', 'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000030.jpg',
    0, 'Retrato de Juana Azurduy de Padilla',
    'https://www.argentina.gob.ar/noticias/quien-fue-juana-azurduy'
);
