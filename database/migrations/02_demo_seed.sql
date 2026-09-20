-- Datos de demostración para Postman y QA local. No usar en producción.

INSERT INTO users (id, firebase_uid, email, display_name, role, invite_code, community_points, home_location)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'dev-user-ana', 'ana@onlygoodthings.test', 'Ana Pérez', 'USER', 'ANA-GOOD-01', 120,
     ST_SetSRID(ST_MakePoint(-58.3816, -34.6037), 4326)),
    ('22222222-2222-2222-2222-222222222222', 'dev-user-bruno', 'bruno@onlygoodthings.test', 'Bruno Díaz', 'USER', 'BRU-GOOD-02', 40,
     ST_SetSRID(ST_MakePoint(-58.3920, -34.6090), 4326)),
    ('33333333-3333-3333-3333-333333333333', 'dev-mod-carla', 'carla@onlygoodthings.test', 'Carla Gómez', 'COMMUNITY_MODERATOR', 'CAR-MOD-03', 300,
     ST_SetSRID(ST_MakePoint(-58.4000, -34.5880), 4326)),
    ('44444444-4444-4444-4444-444444444444', 'dev-admin-diego', 'diego@acme-rse.test', 'Diego RSE', 'COMPANY_ADMIN', 'DIE-RSE-04', 10,
     ST_SetSRID(ST_MakePoint(-58.3700, -34.6010), 4326));

INSERT INTO companies (id, legal_name, trade_name, tax_id, verification_status, verified_at, campaign_balance_cents, impact_score)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Acme Solidaridad S.A.', 'Acme RSE', '30-12345678-9', 'VERIFIED', now(), 5_000_000, 82.5);

INSERT INTO company_admins (company_id, user_id, is_primary)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '44444444-4444-4444-4444-444444444444', TRUE);

INSERT INTO social_posts (id, author_kind, author_user_id, author_company_id, body, media_urls, impact_count, comment_count, location)
VALUES
    ('55555555-5555-5555-5555-555555555555', 'USER', '11111111-1111-1111-1111-111111111111', NULL,
     'Hoy llevé alimento a un comedor de Balvanera. Si alguien suma frutas, avisame.',
     ARRAY['https://cdn.onlygoodthings.test/posts/comedor.jpg'], 18, 2,
     ST_SetSRID(ST_MakePoint(-58.4008, -34.6092), 4326)),
    ('66666666-6666-6666-6666-666666666666', 'COMPANY', NULL, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     'Acme financia 200 kg de alimento para refugios esta semana.',
     ARRAY[]::TEXT[], 54, 0,
     ST_SetSRID(ST_MakePoint(-58.3700, -34.6010), 4326));

INSERT INTO parking_spots (id, owner_user_id, location, status, expires_at, notes)
VALUES
    ('77777777-7777-7777-7777-777777777777', '22222222-2222-2222-2222-222222222222',
     ST_SetSRID(ST_MakePoint(-58.3819, -34.6040), 4326),
     'AVAILABLE', now() + interval '12 minutes', 'Salgo de Av. 9 de Julio y Corrientes');

INSERT INTO campaigns (id, company_id, title, description, status, social_goal, social_progress, budget_cents)
VALUES
    ('88888888-8888-8888-8888-888888888888', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     'Premiá 100 cesiones de estacionamiento',
     'Si la comunidad concreta 100 handoffs, emitimos cupones de almuerzo.',
     'ACTIVE', 100, 12, 800000);

INSERT INTO community_causes (id, organizer_user_id, title, description, status, goal_cents, raised_cents, verified, location)
VALUES
    ('99999999-9999-9999-9999-999999999999', '33333333-3333-3333-3333-333333333333',
     'Techo para el refugio de Palermo',
     'Reparación urgente del techo antes del invierno.',
     'LIVE', 2500000, 430000, TRUE,
     ST_SetSRID(ST_MakePoint(-58.4250, -34.5780), 4326));
