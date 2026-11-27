CREATE TABLE IF NOT EXISTS discovery_settings (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    adoption_radius_km integer NOT NULL DEFAULT 20 CHECK (adoption_radius_km BETWEEN 1 AND 500),
    version bigint NOT NULL DEFAULT 1,
    updated_by uuid REFERENCES users(id) ON DELETE SET NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO discovery_settings(singleton) VALUES(true) ON CONFLICT DO NOTHING;
ALTER TABLE users ADD COLUMN IF NOT EXISTS discovery_location geometry(Point,4326);
ALTER TABLE users ADD COLUMN IF NOT EXISTS discovery_place text;
