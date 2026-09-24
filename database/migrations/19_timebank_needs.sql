-- Pedidos de trueque: un aviso (post) por lo que alguien necesita.
-- El oficio (Doy) sigue en user_skills; esto es el tablero.

INSERT INTO skill_tags (slug, label)
SELECT 'mensaje', 'Mensaje'
WHERE NOT EXISTS (SELECT 1 FROM skill_tags WHERE slug = 'mensaje');

CREATE TABLE IF NOT EXISTS timebank_needs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES skill_tags (id) ON DELETE CASCADE,
    post_id     UUID REFERENCES social_posts (id) ON DELETE SET NULL,
    note        TEXT,
    open        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_timebank_needs_open
    ON timebank_needs (open, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_timebank_needs_tag_open
    ON timebank_needs (tag_id)
    WHERE open;
