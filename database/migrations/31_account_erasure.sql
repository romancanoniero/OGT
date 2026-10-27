-- Durable deletion work: OGT data and the tombstone are committed before contacting Firebase.
-- The provider UID is retained only until deletion succeeds; no tokens or credentials are stored.
CREATE TABLE IF NOT EXISTS account_erasure_jobs (
    uid_hash TEXT PRIMARY KEY REFERENCES crm_erased_accounts(uid_hash),
    firebase_uid TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error_code TEXT CHECK (last_error_code ~ '^[A-Z0-9_]{1,80}$'),
    CHECK (completed_at IS NULL OR firebase_uid IS NULL)
);
CREATE INDEX IF NOT EXISTS account_erasure_pending ON account_erasure_jobs(next_attempt_at)
    WHERE completed_at IS NULL;

-- Track uploads before publication too, so another account cannot cause their cleanup.
CREATE TABLE IF NOT EXISTS uploaded_media_owners (
    stored_name TEXT PRIMARY KEY CHECK (stored_name ~ '^[0-9a-fA-F-]{36}\.(jpg|jpeg|png|webp|mp4|webm)$'),
    uploader_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS uploaded_media_by_owner ON uploaded_media_owners(uploader_user_id);

-- Story rows disappear with their author; keep cleanup independent of that cascade.
CREATE TABLE IF NOT EXISTS erased_story_media (
    story_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);
