-- Ficha de adopción / perdido: datos de la UI y vínculo al post del río.
ALTER TABLE animal_listings
    ADD COLUMN IF NOT EXISTS pet_name TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS age_label TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS sex TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS temperament TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS vaccinated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS sterilized BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS home_needs TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS marks TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS last_seen_place TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS place_label TEXT NOT NULL DEFAULT '';

ALTER TABLE animal_listings DROP CONSTRAINT IF EXISTS animal_listings_alert_radius_m_check;
ALTER TABLE animal_listings
    ADD CONSTRAINT animal_listings_alert_radius_m_check
    CHECK (alert_radius_m BETWEEN 0 AND 20000);

ALTER TABLE social_posts
    ADD COLUMN IF NOT EXISTS listing_id UUID REFERENCES animal_listings (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_posts_listing ON social_posts (listing_id)
    WHERE listing_id IS NOT NULL;
