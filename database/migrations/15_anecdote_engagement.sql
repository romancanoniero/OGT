-- Cada anécdota de un homenaje tiene aplauso, corazón y comentarios propios.

ALTER TABLE post_anecdotes
    ADD COLUMN IF NOT EXISTS impact_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS comment_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS heart_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE post_anecdotes
    DROP CONSTRAINT IF EXISTS post_anecdotes_impact_count_check;
ALTER TABLE post_anecdotes
    ADD CONSTRAINT post_anecdotes_impact_count_check CHECK (impact_count >= 0);
ALTER TABLE post_anecdotes
    DROP CONSTRAINT IF EXISTS post_anecdotes_comment_count_check;
ALTER TABLE post_anecdotes
    ADD CONSTRAINT post_anecdotes_comment_count_check CHECK (comment_count >= 0);
ALTER TABLE post_anecdotes
    DROP CONSTRAINT IF EXISTS post_anecdotes_heart_count_check;
ALTER TABLE post_anecdotes
    ADD CONSTRAINT post_anecdotes_heart_count_check CHECK (heart_count >= 0);

CREATE TABLE IF NOT EXISTS anecdote_impacts (
    anecdote_id UUID NOT NULL REFERENCES post_anecdotes (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (anecdote_id, user_id)
);

CREATE TABLE IF NOT EXISTS anecdote_hearts (
    anecdote_id UUID NOT NULL REFERENCES post_anecdotes (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (anecdote_id, user_id)
);

ALTER TABLE post_comments
    ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;
