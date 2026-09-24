-- Constitución OGT: se logró ≠ clap; dinero ≠ reputación.
-- HEART alinea el enum SQL con Kotlin. Placement separa orgánico de plata.

ALTER TYPE feed_event_kind ADD VALUE IF NOT EXISTS 'HEART';

CREATE TYPE post_placement AS ENUM ('ORGANIC', 'PROMOTED', 'AD');
CREATE TYPE action_outcome_kind AS ENUM (
    'PARKING_HANDOFF',
    'ANIMAL_RESOLVED',
    'VOLUNTEER_CHECKED_IN',
    'TIMEBANK_CLOSED',
    'CAUSE_DELIVERED'
);
CREATE TYPE verification_method AS ENUM ('GEO', 'WITNESS', 'ORGANIZER', 'PAYMENT_SETTLED', 'SERVER');
CREATE TYPE money_ledger_kind AS ENUM ('PROMOTED_REACH', 'AD_IMPRESSION', 'ACTION_FINANCE');

ALTER TABLE social_posts
    ADD COLUMN IF NOT EXISTS placement post_placement NOT NULL DEFAULT 'ORGANIC',
    ADD COLUMN IF NOT EXISTS achieved_count INTEGER NOT NULL DEFAULT 0 CHECK (achieved_count >= 0);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS trust_score NUMERIC(6, 3) NOT NULL DEFAULT 0
        CHECK (trust_score >= 0 AND trust_score <= 1);

CREATE TABLE IF NOT EXISTS action_outcomes (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind                  action_outcome_kind NOT NULL,
    verification_method   verification_method NOT NULL,
    verified              BOOLEAN NOT NULL DEFAULT TRUE,
    post_id               UUID REFERENCES social_posts (id) ON DELETE SET NULL,
    actor_user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    beneficiary_user_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    company_id            UUID REFERENCES companies (id) ON DELETE SET NULL,
    campaign_id           UUID REFERENCES campaigns (id) ON DELETE SET NULL,
    source_table          TEXT NOT NULL,
    source_id             UUID,
    points_awarded        INTEGER NOT NULL DEFAULT 0 CHECK (points_awarded >= 0),
    payload               JSONB NOT NULL DEFAULT '{}'::jsonb,
    verified_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT action_outcomes_verified_chk CHECK (verified = TRUE)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_outcomes_kind_source
    ON action_outcomes (kind, source_id)
    WHERE source_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outcomes_actor ON action_outcomes (actor_user_id, verified_at DESC);
CREATE INDEX IF NOT EXISTS idx_outcomes_company ON action_outcomes (company_id, verified_at DESC)
    WHERE company_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_outcomes_post ON action_outcomes (post_id)
    WHERE post_id IS NOT NULL;

-- Ledger de plata. Ningún trigger de esta tabla toca community_points ni impact_score.
CREATE TABLE IF NOT EXISTS money_ledgers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kind            money_ledger_kind NOT NULL,
    amount_cents    BIGINT NOT NULL CHECK (amount_cents > 0),
    payer_user_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    company_id      UUID REFERENCES companies (id) ON DELETE SET NULL,
    post_id         UUID REFERENCES social_posts (id) ON DELETE SET NULL,
    campaign_id     UUID REFERENCES campaigns (id) ON DELETE SET NULL,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_money_ledgers_kind ON money_ledgers (kind, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_placement ON social_posts (placement, created_at DESC)
    WHERE moderation_status = 'VISIBLE' AND is_story = FALSE;

-- Convocatoria demo para check-in geocercado.
INSERT INTO volunteer_calls (
    id, organizer_user_id, title, description, location, geofence_radius_m,
    starts_at, ends_at, slots
)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    '33333333-3333-3333-3333-333333333333',
    'Merienda comunitaria en Balvanera',
    'Armamos viandas. El check-in es en la plaza, no se autodeclara.',
    ST_SetSRID(ST_MakePoint(-58.4008, -34.6092), 4326),
    150,
    now() - interval '30 minutes',
    now() + interval '4 hours',
    20
)
ON CONFLICT DO NOTHING;
