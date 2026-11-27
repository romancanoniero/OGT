-- Extend carousel capacity in place. Existing files, order, IDs and timestamps are unchanged.
ALTER TABLE post_media DROP CONSTRAINT IF EXISTS post_media_sort_order_check;
ALTER TABLE post_media ADD CONSTRAINT post_media_sort_order_check CHECK (sort_order >= 0 AND sort_order < 20);
COMMENT ON TABLE post_media IS 'Carrusel del post: hasta 20 piezas IMAGE/VIDEO, en orden.';
