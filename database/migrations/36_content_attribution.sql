-- Optional user credit, independent of editorial source_url / internal homage links.
ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS attribution_url text;
ALTER TABLE social_posts DROP CONSTRAINT IF EXISTS social_posts_attribution_length;
ALTER TABLE social_posts ADD CONSTRAINT social_posts_attribution_length
    CHECK (attribution_url IS NULL OR char_length(attribution_url) <= 2048);
