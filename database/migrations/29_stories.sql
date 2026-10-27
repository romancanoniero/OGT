-- Additive: existing posts, media and moderation remain unchanged.
BEGIN;
CREATE TABLE IF NOT EXISTS story_details (
    post_id UUID PRIMARY KEY REFERENCES social_posts(id) ON DELETE CASCADE,
    audience TEXT NOT NULL CHECK (audience IN ('COMMUNITY', 'FOLLOWERS')),
    kind TEXT NOT NULL CHECK (kind IN ('IMAGE', 'VIDEO')),
    content_type TEXT NOT NULL,
    duration_ms BIGINT NOT NULL CHECK (duration_ms BETWEEN 1 AND 30000),
    content_hash TEXT NOT NULL,
    has_poster BOOLEAN NOT NULL DEFAULT FALSE,
    removal_reason TEXT,
    removed_by UUID REFERENCES users(id) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS story_views (
    post_id UUID NOT NULL REFERENCES story_details(post_id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(post_id, viewer_id)
);
CREATE INDEX IF NOT EXISTS idx_story_active ON social_posts(story_expires_at, created_at DESC)
    WHERE is_story AND moderation_status='VISIBLE';
COMMIT;
