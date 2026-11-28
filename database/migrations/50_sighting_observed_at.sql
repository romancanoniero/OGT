-- Keep report creation timestamps and historical payloads untouched.
-- Older sightings have no known observation time; do not backfill a made-up date.
ALTER TABLE animal_contacts ADD COLUMN IF NOT EXISTS observed_at TIMESTAMPTZ;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='animal_contacts_observed_kind_check'
                   AND conrelid='animal_contacts'::regclass) THEN
        ALTER TABLE animal_contacts ADD CONSTRAINT animal_contacts_observed_kind_check
            CHECK (observed_at IS NULL OR (kind='SIGHTING' AND observed_at>'1970-01-01T00:00:00Z'::timestamptz));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS animal_contacts_observation_order
    ON animal_contacts(listing_id, (COALESCE(observed_at, created_at)) DESC, created_at DESC)
    WHERE kind='SIGHTING';
