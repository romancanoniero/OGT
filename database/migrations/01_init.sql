-- Only Good Things — esquema fundacional PostgreSQL + PostGIS
-- SRID 4326 (WGS84). Geometrías almacenadas como Point; consultas de radio sobre geography.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

--------------------------------------------------------------------------------
-- Tipos de dominio
--------------------------------------------------------------------------------

CREATE TYPE user_role AS ENUM ('USER', 'COMPANY_ADMIN', 'COMMUNITY_MODERATOR');
CREATE TYPE account_status AS ENUM ('PENDING', 'ACTIVE', 'SUSPENDED', 'DELETED');
CREATE TYPE company_verification AS ENUM ('UNVERIFIED', 'PENDING_REVIEW', 'VERIFIED', 'REJECTED');
CREATE TYPE parking_status AS ENUM ('AVAILABLE', 'CLAIMED', 'COMPLETED', 'EXPIRED', 'CANCELLED');
CREATE TYPE author_kind AS ENUM ('USER', 'COMPANY');
CREATE TYPE moderation_status AS ENUM ('VISIBLE', 'HIDDEN', 'REMOVED', 'PENDING_REVIEW');
CREATE TYPE animal_listing_kind AS ENUM ('LOST', 'FOUND', 'ADOPTION');
CREATE TYPE animal_species AS ENUM ('DOG', 'CAT', 'BIRD', 'RABBIT', 'HAMSTER', 'FISH', 'TURTLE', 'OTHER');
CREATE TYPE animal_size AS ENUM ('SMALL', 'MEDIUM', 'LARGE');
CREATE TYPE urgency_level AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
CREATE TYPE volunteer_attendance AS ENUM ('REGISTERED', 'CONFIRMED', 'CHECKED_IN', 'NO_SHOW', 'COMPLETED');
CREATE TYPE match_status AS ENUM ('PROPOSED', 'MUTUAL', 'ACTIVE', 'CLOSED');
CREATE TYPE campaign_status AS ENUM ('DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED');
CREATE TYPE promo_code_status AS ENUM ('ISSUED', 'REDEEMED', 'EXPIRED', 'REVOKED');
CREATE TYPE cause_status AS ENUM ('DRAFT', 'LIVE', 'FUNDED', 'CLOSED', 'CANCELLED');
CREATE TYPE donation_status AS ENUM ('PENDING', 'CAPTURED', 'FAILED', 'REFUNDED');
CREATE TYPE referral_status AS ENUM ('ISSUED', 'REGISTERED', 'FIRST_ACTION', 'REWARDED', 'EXPIRED');

--------------------------------------------------------------------------------
-- Identidad y RSE
--------------------------------------------------------------------------------

CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid        TEXT NOT NULL UNIQUE,
    email               CITEXT,
    phone_e164          TEXT,
    display_name        TEXT NOT NULL,
    photo_url           TEXT,
    role                user_role NOT NULL DEFAULT 'USER',
    status              account_status NOT NULL DEFAULT 'ACTIVE',
    community_points    INTEGER NOT NULL DEFAULT 0 CHECK (community_points >= 0),
    invite_code         TEXT NOT NULL UNIQUE,
    referred_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    home_location       GEOMETRY(Point, 4326),
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_seen_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_contact_chk CHECK (email IS NOT NULL OR phone_e164 IS NOT NULL)
);

CREATE TABLE companies (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_name            TEXT NOT NULL,
    trade_name            TEXT,
    tax_id                TEXT UNIQUE,
    verification_status   company_verification NOT NULL DEFAULT 'UNVERIFIED',
    verified_at           TIMESTAMPTZ,
    campaign_balance_cents BIGINT NOT NULL DEFAULT 0 CHECK (campaign_balance_cents >= 0),
    impact_score          NUMERIC(10, 2) NOT NULL DEFAULT 0,
    hq_location           GEOMETRY(Point, 4326),
    website_url           TEXT,
    logo_url              TEXT,
    metadata              JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE company_admins (
    company_id  UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    is_primary  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, user_id)
);

--------------------------------------------------------------------------------
-- Módulo 1 — Estacionamiento colaborativo
--------------------------------------------------------------------------------

CREATE TABLE parking_spots (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    claimed_by_user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    location              GEOMETRY(Point, 4326) NOT NULL,
    status                parking_status NOT NULL DEFAULT 'AVAILABLE',
    version               INTEGER NOT NULL DEFAULT 0,
    vacated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at            TIMESTAMPTZ NOT NULL,
    claimed_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    owner_last_location   GEOMETRY(Point, 4326),
    claimant_last_location GEOMETRY(Point, 4326),
    eta_seconds           INTEGER,
    distance_meters       NUMERIC(10, 2),
    reward_points         INTEGER NOT NULL DEFAULT 25 CHECK (reward_points >= 0),
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT parking_expiry_chk CHECK (expires_at > vacated_at)
);

CREATE TABLE parking_handoffs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parking_spot_id   UUID NOT NULL REFERENCES parking_spots (id) ON DELETE CASCADE,
    owner_user_id     UUID NOT NULL REFERENCES users (id),
    claimant_user_id  UUID NOT NULL REFERENCES users (id),
    proximity_meters  NUMERIC(10, 2) NOT NULL,
    verified          BOOLEAN NOT NULL DEFAULT FALSE,
    points_awarded    INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

--------------------------------------------------------------------------------
-- Módulo 2 — Feed social
--------------------------------------------------------------------------------

CREATE TABLE social_posts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_kind         author_kind NOT NULL,
    author_user_id      UUID REFERENCES users (id) ON DELETE SET NULL,
    author_company_id   UUID REFERENCES companies (id) ON DELETE SET NULL,
    body                TEXT NOT NULL,
    media_urls          TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    location            GEOMETRY(Point, 4326),
    impact_count        INTEGER NOT NULL DEFAULT 0 CHECK (impact_count >= 0),
    comment_count       INTEGER NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
    moderation_status   moderation_status NOT NULL DEFAULT 'VISIBLE',
    is_story            BOOLEAN NOT NULL DEFAULT FALSE,
    story_expires_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT social_posts_author_chk CHECK (
        (author_kind = 'USER' AND author_user_id IS NOT NULL AND author_company_id IS NULL)
        OR (author_kind = 'COMPANY' AND author_company_id IS NOT NULL)
    )
);

CREATE TABLE post_impacts (
    post_id     UUID NOT NULL REFERENCES social_posts (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE post_comments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID NOT NULL REFERENCES social_posts (id) ON DELETE CASCADE,
    author_user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_comment_id   UUID REFERENCES post_comments (id) ON DELETE CASCADE,
    body                TEXT NOT NULL,
    moderation_status   moderation_status NOT NULL DEFAULT 'VISIBLE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE content_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id         UUID REFERENCES social_posts (id) ON DELETE CASCADE,
    comment_id      UUID REFERENCES post_comments (id) ON DELETE CASCADE,
    reason          TEXT NOT NULL,
    details         TEXT,
    status          moderation_status NOT NULL DEFAULT 'PENDING_REVIEW',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT content_reports_target_chk CHECK (post_id IS NOT NULL OR comment_id IS NOT NULL)
);

--------------------------------------------------------------------------------
-- Módulo 3 — Referidos y deep links
--------------------------------------------------------------------------------

CREATE TABLE referral_invites (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issuer_user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    invite_code         TEXT NOT NULL,
    dynamic_link        TEXT NOT NULL,
    invited_user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    status              referral_status NOT NULL DEFAULT 'ISSUED',
    first_action_at     TIMESTAMPTZ,
    rewarded_at         TIMESTAMPTZ,
    reward_points       INTEGER NOT NULL DEFAULT 50,
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

--------------------------------------------------------------------------------
-- Módulo 4 — Protección animal y voluntariado
--------------------------------------------------------------------------------

CREATE TABLE animal_listings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind                animal_listing_kind NOT NULL,
    species             animal_species NOT NULL,
    size                animal_size,
    urgency             urgency_level NOT NULL DEFAULT 'MEDIUM',
    title               TEXT NOT NULL,
    description         TEXT NOT NULL,
    photo_urls          TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    location            GEOMETRY(Point, 4326) NOT NULL,
    alert_radius_m      INTEGER NOT NULL DEFAULT 3000 CHECK (alert_radius_m BETWEEN 200 AND 20000),
    resolved            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE volunteer_calls (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_user_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    organizer_company_id UUID REFERENCES companies (id) ON DELETE SET NULL,
    title               TEXT NOT NULL,
    description         TEXT NOT NULL,
    location            GEOMETRY(Point, 4326) NOT NULL,
    geofence_radius_m   INTEGER NOT NULL DEFAULT 150 CHECK (geofence_radius_m BETWEEN 30 AND 2000),
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    slots               INTEGER NOT NULL DEFAULT 10 CHECK (slots > 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT volunteer_window_chk CHECK (ends_at > starts_at)
);

CREATE TABLE volunteer_signups (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id             UUID NOT NULL REFERENCES volunteer_calls (id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    attendance          volunteer_attendance NOT NULL DEFAULT 'REGISTERED',
    hours_logged        NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (hours_logged >= 0),
    check_in_location   GEOMETRY(Point, 4326),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (call_id, user_id)
);

--------------------------------------------------------------------------------
-- Módulo 5 — Banco de tiempo
--------------------------------------------------------------------------------

CREATE TABLE skill_tags (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug    TEXT NOT NULL UNIQUE,
    label   TEXT NOT NULL
);

CREATE TABLE user_skills (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES skill_tags (id) ON DELETE CASCADE,
    offered     BOOLEAN NOT NULL DEFAULT FALSE,
    requested   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, tag_id),
    CONSTRAINT user_skills_flag_chk CHECK (offered OR requested)
);

CREATE TABLE timebank_matches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tag_id          UUID NOT NULL REFERENCES skill_tags (id),
    status          match_status NOT NULL DEFAULT 'PROPOSED',
    chat_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT timebank_distinct_chk CHECK (requester_id <> provider_id)
);

CREATE TABLE timebank_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id    UUID NOT NULL REFERENCES timebank_matches (id) ON DELETE CASCADE,
    sender_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

--------------------------------------------------------------------------------
-- Módulo 6 — RSE y patrocinios
--------------------------------------------------------------------------------

CREATE TABLE campaigns (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id              UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    title                   TEXT NOT NULL,
    description             TEXT NOT NULL,
    status                  campaign_status NOT NULL DEFAULT 'DRAFT',
    social_goal             INTEGER NOT NULL DEFAULT 0 CHECK (social_goal >= 0),
    social_progress         INTEGER NOT NULL DEFAULT 0 CHECK (social_progress >= 0),
    budget_cents            BIGINT NOT NULL DEFAULT 0 CHECK (budget_cents >= 0),
    spent_cents             BIGINT NOT NULL DEFAULT 0 CHECK (spent_cents >= 0),
    starts_at               TIMESTAMPTZ,
    ends_at                 TIMESTAMPTZ,
    impact_report           JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE promo_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id     UUID NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    company_id      UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    code            TEXT NOT NULL UNIQUE,
    status          promo_code_status NOT NULL DEFAULT 'ISSUED',
    discount_bps    INTEGER NOT NULL CHECK (discount_bps BETWEEN 100 AND 10000),
    issued_to_user  UUID REFERENCES users (id) ON DELETE SET NULL,
    redeemed_at     TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ NOT NULL,
    condition_note  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

--------------------------------------------------------------------------------
-- Módulo 7 — Crowdfunding solidario
--------------------------------------------------------------------------------

CREATE TABLE community_causes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_user_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    sponsor_company_id  UUID REFERENCES companies (id) ON DELETE SET NULL,
    title               TEXT NOT NULL,
    description         TEXT NOT NULL,
    status              cause_status NOT NULL DEFAULT 'DRAFT',
    goal_cents          BIGINT NOT NULL CHECK (goal_cents > 0),
    raised_cents        BIGINT NOT NULL DEFAULT 0 CHECK (raised_cents >= 0),
    location            GEOMETRY(Point, 4326),
    verified            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE donations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cause_id            UUID NOT NULL REFERENCES community_causes (id) ON DELETE RESTRICT,
    donor_user_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    amount_cents        BIGINT NOT NULL CHECK (amount_cents > 0),
    currency            CHAR(3) NOT NULL DEFAULT 'ARS',
    status              donation_status NOT NULL DEFAULT 'PENDING',
    gateway             TEXT NOT NULL,
    gateway_payment_id  TEXT UNIQUE,
    audit_payload       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

--------------------------------------------------------------------------------
-- Eventos de dominio (outbox para arquitectura orientada a eventos)
--------------------------------------------------------------------------------

CREATE TABLE domain_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      TEXT NOT NULL,
    aggregate_type  TEXT NOT NULL,
    aggregate_id    UUID NOT NULL,
    payload         JSONB NOT NULL,
    published       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

--------------------------------------------------------------------------------
-- Triggers de updated_at
--------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_companies_updated BEFORE UPDATE ON companies FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_parking_updated BEFORE UPDATE ON parking_spots FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_posts_updated BEFORE UPDATE ON social_posts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_animals_updated BEFORE UPDATE ON animal_listings FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_campaigns_updated BEFORE UPDATE ON campaigns FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_causes_updated BEFORE UPDATE ON community_causes FOR EACH ROW EXECUTE FUNCTION set_updated_at();

--------------------------------------------------------------------------------
-- Índices espaciales (GIST) y transaccionales
--------------------------------------------------------------------------------

CREATE INDEX idx_users_firebase_uid ON users (firebase_uid);
CREATE INDEX idx_users_role_status ON users (role, status);
CREATE INDEX idx_users_home_gix ON users USING GIST (home_location);

CREATE INDEX idx_companies_verification ON companies (verification_status);
CREATE INDEX idx_companies_hq_gix ON companies USING GIST (hq_location);

CREATE INDEX idx_parking_status_expires ON parking_spots (status, expires_at);
CREATE INDEX idx_parking_owner ON parking_spots (owner_user_id, status);
CREATE INDEX idx_parking_claimant ON parking_spots (claimed_by_user_id);
CREATE INDEX idx_parking_location_gix ON parking_spots USING GIST (location);
-- Índice parcial para matching concurrente de vacantes vivas
CREATE INDEX idx_parking_live_gix ON parking_spots USING GIST (location)
    WHERE status = 'AVAILABLE';

CREATE INDEX idx_posts_created ON social_posts (created_at DESC)
    WHERE moderation_status = 'VISIBLE';
CREATE INDEX idx_posts_author_user ON social_posts (author_user_id, created_at DESC);
CREATE INDEX idx_posts_author_company ON social_posts (author_company_id, created_at DESC);
CREATE INDEX idx_posts_location_gix ON social_posts USING GIST (location);
CREATE INDEX idx_comments_tree ON post_comments (post_id, parent_comment_id, created_at);

CREATE INDEX idx_referrals_code ON referral_invites (invite_code);
CREATE INDEX idx_referrals_issuer ON referral_invites (issuer_user_id, status);

CREATE INDEX idx_animals_open_gix ON animal_listings USING GIST (location)
    WHERE resolved = FALSE;
CREATE INDEX idx_animals_filters ON animal_listings (species, size, urgency, kind)
    WHERE resolved = FALSE;
CREATE INDEX idx_volunteer_location_gix ON volunteer_calls USING GIST (location);
CREATE INDEX idx_volunteer_window ON volunteer_calls (starts_at, ends_at);

CREATE INDEX idx_user_skills_offered ON user_skills (tag_id) WHERE offered;
CREATE INDEX idx_user_skills_requested ON user_skills (tag_id) WHERE requested;
CREATE INDEX idx_timebank_status ON timebank_matches (status, tag_id);

CREATE INDEX idx_campaigns_company ON campaigns (company_id, status);
CREATE INDEX idx_promo_codes_status ON promo_codes (company_id, status, expires_at);

CREATE INDEX idx_causes_status ON community_causes (status, verified);
CREATE INDEX idx_causes_location_gix ON community_causes USING GIST (location);
CREATE INDEX idx_donations_cause ON donations (cause_id, status, created_at);
CREATE INDEX idx_donations_gateway ON donations (gateway, gateway_payment_id);

CREATE INDEX idx_domain_events_outbox ON domain_events (published, created_at);

--------------------------------------------------------------------------------
-- Seeds de tags del banco de tiempo
--------------------------------------------------------------------------------

INSERT INTO skill_tags (slug, label) VALUES
    ('companionship', 'Acompañamiento'),
    ('tutoring', 'Tutorías'),
    ('gardening', 'Jardinería'),
    ('repairs', 'Reparaciones menores'),
    ('pet_care', 'Cuidado de mascotas'),
    ('cooking', 'Cocina solidaria'),
    ('translation', 'Traducción'),
    ('tech_help', 'Ayuda tecnológica');
