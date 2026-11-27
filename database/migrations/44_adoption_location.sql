-- Unknown adoption locations stay unknown; lost-pet alerts still require a point.
ALTER TABLE animal_listings ALTER COLUMN location DROP NOT NULL;
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='animal_listings'::regclass AND conname='animal_lost_location_required') THEN
  ALTER TABLE animal_listings ADD CONSTRAINT animal_lost_location_required CHECK (kind <> 'LOST' OR location IS NOT NULL);
 END IF;
END $$;
