-- Legacy NULL preserves the original fit presentation. Media bytes are never changed.
BEGIN;
ALTER TABLE story_details ADD COLUMN IF NOT EXISTS frame_mode TEXT;
ALTER TABLE story_details ADD COLUMN IF NOT EXISTS frame_zoom DOUBLE PRECISION;
ALTER TABLE story_details ADD COLUMN IF NOT EXISTS frame_x DOUBLE PRECISION;
ALTER TABLE story_details ADD COLUMN IF NOT EXISTS frame_y DOUBLE PRECISION;
ALTER TABLE story_details DROP CONSTRAINT IF EXISTS story_frame_valid;
ALTER TABLE story_details ADD CONSTRAINT story_frame_valid CHECK (
 (frame_mode IS NULL AND frame_zoom IS NULL AND frame_x IS NULL AND frame_y IS NULL) OR
 (frame_mode IS NOT NULL AND frame_mode IN ('FILL','FIT') AND frame_zoom IS NOT NULL AND frame_zoom BETWEEN 1 AND 6
 AND frame_x IS NOT NULL AND frame_x BETWEEN -1 AND 1 AND frame_y IS NOT NULL AND frame_y BETWEEN -1 AND 1)
);
COMMIT;
