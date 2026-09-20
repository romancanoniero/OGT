-- Un post puede tener varias fotos y videos (mínimo una pieza), como Instagram.
CREATE TYPE media_kind AS ENUM ('IMAGE', 'VIDEO');

CREATE TABLE post_media (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id      UUID NOT NULL REFERENCES social_posts (id) ON DELETE CASCADE,
    kind         media_kind NOT NULL,
    url          TEXT NOT NULL,
    poster_url   TEXT,
    sort_order   INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0 AND sort_order < 10),
    duration_ms  INTEGER CHECK (duration_ms IS NULL OR duration_ms > 0),
    alt_text     TEXT,
    source_url   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, sort_order)
);

CREATE INDEX idx_post_media_post ON post_media (post_id, sort_order);

-- Toda fila histórica pasa a tener al menos una imagen.
INSERT INTO post_media (post_id, kind, url, sort_order, alt_text)
SELECT p.id, 'IMAGE',
       COALESCE(p.media_urls[1], 'https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/800px-PNG_transparency_demonstration_1.png'),
       0,
       'Imagen de la publicación'
FROM social_posts p
WHERE NOT EXISTS (SELECT 1 FROM post_media m WHERE m.post_id = p.id);

ALTER TABLE social_posts
    ADD COLUMN source_url TEXT;

COMMENT ON TABLE post_media IS 'Carrusel del post: 1 a 10 piezas IMAGE/VIDEO. El feed hidrata desde acá.';
