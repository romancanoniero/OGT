-- Additive contracts: old posts, profile names and /h/ links remain valid.
CREATE TABLE IF NOT EXISTS recognition_categories (
    id TEXT PRIMARY KEY,
    label TEXT NOT NULL CHECK (length(btrim(label)) BETWEEN 2 AND 60),
    icon TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS recognition_categories_label ON recognition_categories(lower(label));
INSERT INTO recognition_categories(id,label,icon,sort_order) VALUES
 ('plantar','Reforestación','🌳',10),('limpieza','Limpieza','🧹',20),
 ('reciclaje','Reciclaje','♻',30),('vecinal','Ayuda comunitaria','🤝',40),
 ('animal','Rescate Animal','🐾',50),('movilidad','Movilidad Eco','🚲',60) ON CONFLICT DO NOTHING;

ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS users_nickname_unique ON users(lower(nickname)) WHERE nickname IS NOT NULL;
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS recognition_category_id TEXT REFERENCES recognition_categories(id);
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS publication_kind TEXT;
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS client_request_id TEXT;
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS request_fingerprint TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS social_posts_request ON social_posts(author_user_id,client_request_id) WHERE client_request_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS post_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
    issuer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    recipient_name TEXT NOT NULL,
    purpose TEXT NOT NULL CHECK (purpose IN ('RECOGNITION','PARTICIPATION')),
    channel TEXT NOT NULL CHECK (channel IN ('EMAIL','SMS','IN_APP','SHARE')),
    contact TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    -- Private share material: returned only to the issuer and the bound recipient, never public previews or logs.
    delivery_token TEXT NOT NULL,
    target_key TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','DECLINED','REVOKED')),
    claimed_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT now() + interval '30 days',
    opened_at TIMESTAMPTZ,
    share_opened_at TIMESTAMPTZ,
    registered_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS post_invitations_target ON post_invitations(post_id,purpose,target_key) WHERE status IN ('PENDING','ACCEPTED');
CREATE INDEX IF NOT EXISTS post_invitations_recipient ON post_invitations(recipient_user_id,created_at DESC);
CREATE TABLE IF NOT EXISTS invitation_deliveries (
    invitation_id UUID PRIMARY KEY REFERENCES post_invitations(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENDING','ACCEPTED','FAILED','UNKNOWN','CONFIGURATION_REQUIRED','CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    provider_id TEXT,
    last_error_code TEXT,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS invitation_deliveries_due ON invitation_deliveries(next_attempt_at) WHERE status IN ('PENDING','CONFIGURATION_REQUIRED');
