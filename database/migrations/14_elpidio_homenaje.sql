-- Homenaje de undercover a Don Elpidio González. Las anécdotas no van al hilo.

UPDATE users
SET display_name = 'undercover'
WHERE id = 'b7856d86-4cab-4189-8742-15c58c2088a8'
  AND firebase_uid = 'H3wjp0Ok7Fcyg0SBioiWEyhTjTY2';

INSERT INTO social_posts (
    id, author_kind, author_user_id, body, media_urls, impact_count, comment_count,
    topic, source_url, honoree_name, location, created_at
) VALUES (
    'a2000000-0000-4000-8000-000000000040',
    'USER',
    'b7856d86-4cab-4189-8742-15c58c2088a8',
    'Vicepresidente entre 1922 y 1928. Después del golpe del 30, cuando muchos cobraron privilegio, él eligió el maletín. Rechazó pensión, casa y limosna. Decía que mientras pudiera trabajar no le pediría ayuda a la República.',
    ARRAY['http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000040.jpg']::TEXT[],
    0, 0, 'Homenaje',
    'https://nuevo.nogoya.gob.ar/ordenanza-no1245/',
    'Don Elpidio González',
    ST_SetSRID(ST_MakePoint(-60.639, -32.944), 4326),
    now() - interval '20 minutes'
);

INSERT INTO post_people (post_id, user_id, role) VALUES
    ('a2000000-0000-4000-8000-000000000040', 'b7856d86-4cab-4189-8742-15c58c2088a8', 'AUTHOR');

INSERT INTO post_media (id, post_id, kind, url, sort_order, alt_text, source_url)
VALUES (
    'c2000000-0000-4000-8000-000000000040',
    'a2000000-0000-4000-8000-000000000040',
    'IMAGE',
    'http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000040.jpg',
    0,
    'Retrato de Elpidio González',
    'https://nuevo.nogoya.gob.ar/ordenanza-no1245/'
);

INSERT INTO post_anecdotes (id, post_id, author_user_id, body, source_url, sort_order, created_at) VALUES
(
    'd2000000-0000-4000-8000-000000000041',
    'a2000000-0000-4000-8000-000000000040',
    '11111111-1111-1111-1111-111111111111',
    'Le ofrecieron pensión vitalicia por haber sido vice. La rechazó. Dijo que mientras pudiera trabajar no aceptaría ayuda de la República. No era un discurso: era la regla con la que se levantaba.',
    'https://nuevo.nogoya.gob.ar/ordenanza-no1245/',
    0, now() - interval '18 minutes'
),
(
    'd2000000-0000-4000-8000-000000000042',
    'a2000000-0000-4000-8000-000000000040',
    '22222222-2222-2222-2222-222222222222',
    'En los últimos años vendía ballenitas, anilinas y pomadas para zapatos. El que había firmado decretos en la Rosada volvió al maletín. No pidió un escritorio de consuelo.',
    'https://nuevo.nogoya.gob.ar/ordenanza-no1245/',
    1, now() - interval '16 minutes'
),
(
    'd2000000-0000-4000-8000-000000000043',
    'a2000000-0000-4000-8000-000000000040',
    '33333333-3333-3333-3333-333333333333',
    'También le ofrecieron plata y una vivienda. Las devolvió. El paso por los cargos altos no le había dejado enriquecimiento. Pobre de bolsillo, grande de ética: así lo anotó el Concejo de Nogoyá.',
    'https://nuevo.nogoya.gob.ar/ordenanza-no1245/',
    2, now() - interval '14 minutes'
),
(
    'd2000000-0000-4000-8000-000000000044',
    'a2000000-0000-4000-8000-000000000040',
    '44444444-4444-4444-4444-444444444444',
    'En 1916 era diputado. Yrigoyen le pidió el Ministerio de Guerra y renunció a la banca. El cargo no era un premio: era un servicio que se podía soltar.',
    'https://nuevo.nogoya.gob.ar/ordenanza-no1245/',
    3, now() - interval '12 minutes'
),
(
    'd2000000-0000-4000-8000-000000000045',
    'a2000000-0000-4000-8000-000000000040',
    '11111111-1111-1111-1111-111111111111',
    'Después del golpe de 1930 estuvo dos años preso por haber sido funcionario de Yrigoyen. No cambió de bandera para salir antes. Salió y volvió a laburar.',
    'https://intranet.hcdiputados-ba.gov.ar/proyectos/16-17D2253012019-11-2812-11-15.pdf',
    4, now() - interval '10 minutes'
),
(
    'd2000000-0000-4000-8000-000000000046',
    'a2000000-0000-4000-8000-000000000040',
    '22222222-2222-2222-2222-222222222222',
    'En 1951 lo operaron y se quedó seis meses en el Italiano. Más que convalecencia: no tenía a dónde ir ni quién lo cuidara. Murió el 18 de octubre, a los 76.',
    'https://nuevo.nogoya.gob.ar/ordenanza-no1245/',
    5, now() - interval '8 minutes'
),
(
    'd2000000-0000-4000-8000-000000000047',
    'a2000000-0000-4000-8000-000000000040',
    '33333333-3333-3333-3333-333333333333',
    'En Rosario, en el Nacional Nº 1, lo recordaron con Alvear. Un funcionario dijo que Elpidio, siendo adinerado al principio, terminó pobre y rechazó hasta las donaciones de los conservadores.',
    'https://www.conclusion.com.ar/la-ciudad/homenajearon-a-marcelo-t-de-alvear-y-elpidio-gonzalez-en-el-nacional-no1/10/2022/',
    6, now() - interval '6 minutes'
),
(
    'd2000000-0000-4000-8000-000000000048',
    'a2000000-0000-4000-8000-000000000040',
    '44444444-4444-4444-4444-444444444444',
    'En Despeñaderos, Córdoba, el Ministerio de Salud sostiene el Hogar Elpidio González. El homenaje no fue solo una placa: fue un techo para quien ya no puede solo, que es lo que él nunca pidió para sí.',
    'https://ministeriodesalud.cba.gov.ar/despenaderos-habilitaron-mejoras-edilicias-en-el-hogar-e-gonzalez/',
    7, now() - interval '4 minutes'
);
