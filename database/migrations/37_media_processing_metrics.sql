-- Anonymous processing measurements only. No users, filenames, URLs or content.
-- Historical files have no known input size and are deliberately not backfilled.
CREATE TABLE IF NOT EXISTS media_processing_metrics (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source TEXT NOT NULL CHECK (source IN ('UPLOAD','STORY','STORY_POSTER')),
    kind TEXT NOT NULL CHECK (kind IN ('IMAGE','VIDEO')),
    received_bytes BIGINT NOT NULL CHECK (received_bytes BETWEEN 1 AND 8388608),
    stored_bytes BIGINT NOT NULL CHECK (stored_bytes > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS media_processing_time ON media_processing_metrics(created_at);
