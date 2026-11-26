-- Scheduling is additive: historical posts without a start keep their original behavior.
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS gathering_place TEXT;
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS event_starts_at TIMESTAMPTZ;
