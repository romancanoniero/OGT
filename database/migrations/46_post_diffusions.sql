-- A diffusion references the original: no copied media, comments or adoption workflow.
CREATE TABLE IF NOT EXISTS post_diffusions (
    post_id uuid NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(post_id,user_id)
);
CREATE INDEX IF NOT EXISTS post_diffusions_user_recent ON post_diffusions(user_id,created_at DESC);
