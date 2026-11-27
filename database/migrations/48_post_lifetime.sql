-- Prospective feed archive only. Existing posts, media, applications and chats are unchanged.
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS feed_expires_at timestamptz;
CREATE OR REPLACE FUNCTION ogt_new_post_lifetime() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT NEW.is_story AND NEW.feed_expires_at IS NULL THEN
    NEW.feed_expires_at := now() + interval '90 days';
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS ogt_new_post_lifetime ON social_posts;
CREATE TRIGGER ogt_new_post_lifetime BEFORE INSERT ON social_posts
FOR EACH ROW EXECUTE FUNCTION ogt_new_post_lifetime();
CREATE INDEX IF NOT EXISTS social_posts_feed_expiry ON social_posts(feed_expires_at)
WHERE feed_expires_at IS NOT NULL AND NOT is_story;

CREATE TABLE IF NOT EXISTS post_lifetime_notices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id uuid NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
  recipient_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  push_sent boolean NOT NULL DEFAULT false,
  UNIQUE(post_id,recipient_user_id,expires_at)
);
