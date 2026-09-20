-- Menciones de honor: crédito a alguien que todavía no es usuaria.
-- post_people admite user_id XOR honor_id. El claim lo decide el servidor.

CREATE TYPE honor_channel AS ENUM ('WHATSAPP', 'SMS', 'EMAIL');
CREATE TYPE honor_status AS ENUM ('PENDING', 'CLAIMED', 'DECLINED');

CREATE TABLE honor_mentions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issuer_user_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    given_name       TEXT NOT NULL,
    channel          honor_channel NOT NULL,
    contact          TEXT NOT NULL,
    claim_token      TEXT NOT NULL UNIQUE,
    status           honor_status NOT NULL DEFAULT 'PENDING',
    claimed_user_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    post_id          UUID REFERENCES social_posts (id) ON DELETE SET NULL,
    role             post_person_role,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at       TIMESTAMPTZ
);

CREATE INDEX idx_honor_mentions_contact ON honor_mentions (channel, contact) WHERE status = 'PENDING';
CREATE INDEX idx_honor_mentions_post ON honor_mentions (post_id) WHERE post_id IS NOT NULL;

ALTER TABLE post_people DROP CONSTRAINT post_people_pkey;
ALTER TABLE post_people ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE post_people ADD COLUMN honor_id UUID REFERENCES honor_mentions (id) ON DELETE CASCADE;
ALTER TABLE post_people ADD CONSTRAINT post_people_subject_xor_chk
    CHECK (num_nonnulls(user_id, honor_id) = 1);
ALTER TABLE post_people ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE post_people ADD CONSTRAINT post_people_pkey PRIMARY KEY (id);

DROP INDEX IF EXISTS idx_post_people_user;
CREATE UNIQUE INDEX idx_post_people_user
    ON post_people (post_id, user_id, role)
    WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX idx_post_people_honor
    ON post_people (post_id, honor_id, role)
    WHERE honor_id IS NOT NULL;
