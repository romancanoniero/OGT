-- Anécdotas de un homenaje: testimonio, no hilo de chat.
-- Los comentarios siguen en post_comments; pueden colgarse de una anécdota.

ALTER TABLE social_posts
    ADD COLUMN IF NOT EXISTS honoree_name TEXT;

CREATE TABLE IF NOT EXISTS post_anecdotes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES social_posts (id) ON DELETE CASCADE,
    author_user_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body            TEXT NOT NULL,
    source_url      TEXT,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_anecdotes_post ON post_anecdotes (post_id, sort_order, created_at);

ALTER TABLE post_comments
    ADD COLUMN IF NOT EXISTS anecdote_id UUID REFERENCES post_anecdotes (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_comments_anecdote
    ON post_comments (anecdote_id, created_at)
    WHERE anecdote_id IS NOT NULL;
