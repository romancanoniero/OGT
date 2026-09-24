-- Anécdotas del homenaje a René Favaloro. No van al hilo.

UPDATE social_posts
SET honoree_name = 'René Favaloro'
WHERE id = 'a2000000-0000-4000-8000-000000000011'
  AND (honoree_name IS NULL OR btrim(honoree_name) = '');

INSERT INTO post_anecdotes (id, post_id, author_user_id, body, source_url, sort_order, created_at) VALUES
(
    'd2000000-0000-4000-8000-000000000051',
    'a2000000-0000-4000-8000-000000000011',
    '22222222-2222-2222-2222-222222222222',
    'Antes de Cleveland fue médico rural en Jacinto Arauz, La Pampa, con su hermano. Doce años de pueblo: partos, fiebre y caminos de tierra. Ahí aprendió que el paciente tiene nombre, no número de cama.',
    'https://www.fundacionfavaloro.org/biografia/',
    0, now() - interval '50 minutes'
),
(
    'd2000000-0000-4000-8000-000000000052',
    'a2000000-0000-4000-8000-000000000011',
    '33333333-3333-3333-3333-333333333333',
    'En 1967, en la Cleveland Clinic, estandarizó el bypass con vena safena. No lo patentó. Dijo que una técnica que salva vidas no se cobra como invento: se enseña.',
    'https://www.fundacionfavaloro.org/biografia/',
    1, now() - interval '44 minutes'
),
(
    'd2000000-0000-4000-8000-000000000053',
    'a2000000-0000-4000-8000-000000000011',
    '44444444-4444-4444-4444-444444444444',
    'En 1971 dejó la Clínica en el pico de su carrera. El último tercio, escribió, era para levantar en Buenos Aires un centro como el que lo había formado. Volvió sin pedirle un privilegio al país.',
    'https://www.fundacionfavaloro.org/biografia/',
    2, now() - interval '38 minutes'
),
(
    'd2000000-0000-4000-8000-000000000054',
    'a2000000-0000-4000-8000-000000000011',
    '11111111-1111-1111-1111-111111111111',
    'En 1975 nació la Fundación. La frase que colgó como regla no era un eslogan: “Los únicos privilegiados son los pacientes.”',
    'https://www.fundacionfavaloro.org/biografia/',
    3, now() - interval '32 minutes'
),
(
    'd2000000-0000-4000-8000-000000000055',
    'a2000000-0000-4000-8000-000000000011',
    '22222222-2222-2222-2222-222222222222',
    'Armó residencias y mandó a los discípulos a operar en hospitales públicos. El bypass no tenía que quedar en una clínica de pocos: tenía que llegar a quien llegara tarde al turno.',
    'https://www.fundacionfavaloro.org/biografia/',
    4, now() - interval '26 minutes'
),
(
    'd2000000-0000-4000-8000-000000000056',
    'a2000000-0000-4000-8000-000000000011',
    '33333333-3333-3333-3333-333333333333',
    'A los residentes les pedía humanismo. Un cirujano sin eso, decía, es un técnico con bisturí. La mano importa; la mirada, más.',
    'https://www.fundacionfavaloro.org/biografia/',
    5, now() - interval '20 minutes'
),
(
    'd2000000-0000-4000-8000-000000000057',
    'a2000000-0000-4000-8000-000000000011',
    '44444444-4444-4444-4444-444444444444',
    'Cuando el Instituto se ahogaba en deudas, salió a pedir créditos y a escribir cartas. No para su casa: para que no se apagara el quirófano. El hospital era la obra, no el nombre en la placa.',
    'https://www.fundacionfavaloro.org/biografia/',
    6, now() - interval '14 minutes'
),
(
    'd2000000-0000-4000-8000-000000000058',
    'a2000000-0000-4000-8000-000000000011',
    '11111111-1111-1111-1111-111111111111',
    'Seguía operando y enseñando cuando ya era una figura mundial. El que había cambiado la cirugía cardíaca se sentaba a explicar un caso como si fuera el primero del día.',
    'https://www.fundacionfavaloro.org/biografia/',
    7, now() - interval '8 minutes'
)
ON CONFLICT (id) DO NOTHING;
