-- Encuadre de la foto al marco 16:9. No reemplaza el archivo.
ALTER TABLE post_media ADD COLUMN IF NOT EXISTS frame_x double precision;
ALTER TABLE post_media ADD COLUMN IF NOT EXISTS frame_y double precision;
ALTER TABLE post_media ADD COLUMN IF NOT EXISTS frame_zoom double precision;

ALTER TABLE post_media DROP CONSTRAINT IF EXISTS post_media_frame_range;
ALTER TABLE post_media ADD CONSTRAINT post_media_frame_range CHECK (
    (frame_x IS NULL OR (frame_x >= 0 AND frame_x <= 1))
    AND (frame_y IS NULL OR (frame_y >= 0 AND frame_y <= 1))
    AND (frame_zoom IS NULL OR (frame_zoom >= 1 AND frame_zoom <= 6))
);

COMMENT ON COLUMN post_media.frame_x IS 'Punto de la foto, 0..1, que queda al centro del marco 16:9.';
COMMENT ON COLUMN post_media.frame_y IS 'Punto de la foto, 0..1, que queda al centro del marco 16:9.';
COMMENT ON COLUMN post_media.frame_zoom IS 'Acercamiento sobre el recorte que ya cubre el marco. 1 no acerca.';
