-- Índice de Publicaciones. La búsqueda lee esta tabla, no un barrido de cada columna.
-- Solo entran filas VISIBLE. REMOVED, HIDDEN y PENDING_REVIEW quedan afuera.
-- No existe el estado DELETED: cualquier estado distinto de VISIBLE tampoco entra.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS crm_publication_index (
    post_id UUID NOT NULL REFERENCES social_posts (id) ON DELETE CASCADE,
    origin  TEXT NOT NULL CHECK (origin IN ('author', 'label', 'topic', 'body', 'photo', 'id')),
    value   TEXT NOT NULL,
    PRIMARY KEY (post_id, origin)
);

CREATE INDEX IF NOT EXISTS idx_crm_publication_index_value
    ON crm_publication_index USING gin (value gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_crm_publication_index_origin
    ON crm_publication_index (origin);

COMMENT ON TABLE crm_publication_index IS
    'Orígenes de la búsqueda de Control: nombre guardado, rótulo de la app, tema, texto, texto de la foto e id. Solo publicaciones VISIBLE.';

CREATE OR REPLACE FUNCTION crm_refresh_publication_index(target UUID) RETURNS void
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    status TEXT;
    stored TEXT;
    topic_text TEXT;
    body_text TEXT;
    photo_text TEXT;
BEGIN
    SELECT p.moderation_status::text,
           COALESCE(NULLIF(btrim(u.display_name), ''), NULLIF(btrim(c.trade_name), ''), NULLIF(btrim(c.legal_name), '')),
           NULLIF(btrim(p.topic), ''),
           NULLIF(btrim(p.body), '')
      INTO status, stored, topic_text, body_text
      FROM social_posts p
      LEFT JOIN users u ON u.id = p.author_user_id
      LEFT JOIN companies c ON c.id = p.author_company_id
     WHERE p.id = target;

    IF NOT FOUND OR status IS DISTINCT FROM 'VISIBLE' THEN
        DELETE FROM crm_publication_index WHERE post_id = target;
        RETURN;
    END IF;

    DELETE FROM crm_publication_index WHERE post_id = target;

    SELECT string_agg(btrim(m.alt_text), ' ' ORDER BY m.sort_order)
      INTO photo_text
      FROM post_media m
     WHERE m.post_id = target
       AND NULLIF(btrim(m.alt_text), '') IS NOT NULL;

    INSERT INTO crm_publication_index (post_id, origin, value)
    VALUES (target, 'label', 'Alguien de la comunidad');
    INSERT INTO crm_publication_index (post_id, origin, value)
    VALUES (target, 'id', target::text);
    IF stored IS NOT NULL THEN
        INSERT INTO crm_publication_index (post_id, origin, value)
        VALUES (target, 'author', stored);
    END IF;
    IF topic_text IS NOT NULL THEN
        INSERT INTO crm_publication_index (post_id, origin, value)
        VALUES (target, 'topic', topic_text);
    END IF;
    IF body_text IS NOT NULL THEN
        INSERT INTO crm_publication_index (post_id, origin, value)
        VALUES (target, 'body', body_text);
    END IF;
    IF photo_text IS NOT NULL THEN
        INSERT INTO crm_publication_index (post_id, origin, value)
        VALUES (target, 'photo', photo_text);
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION crm_publication_index_from_post() RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND NEW.body IS NOT DISTINCT FROM OLD.body
       AND NEW.topic IS NOT DISTINCT FROM OLD.topic
       AND NEW.moderation_status IS NOT DISTINCT FROM OLD.moderation_status
       AND NEW.author_user_id IS NOT DISTINCT FROM OLD.author_user_id
       AND NEW.author_company_id IS NOT DISTINCT FROM OLD.author_company_id
       AND NEW.author_kind IS NOT DISTINCT FROM OLD.author_kind THEN
        RETURN NULL;
    END IF;
    PERFORM crm_refresh_publication_index(NEW.id);
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION crm_publication_index_from_user() RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF NEW.display_name IS NOT DISTINCT FROM OLD.display_name THEN
        RETURN NULL;
    END IF;
    PERFORM crm_refresh_publication_index(p.id)
      FROM social_posts p
     WHERE p.author_user_id = NEW.id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION crm_publication_index_from_company() RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF NEW.trade_name IS NOT DISTINCT FROM OLD.trade_name
       AND NEW.legal_name IS NOT DISTINCT FROM OLD.legal_name THEN
        RETURN NULL;
    END IF;
    PERFORM crm_refresh_publication_index(p.id)
      FROM social_posts p
     WHERE p.author_company_id = NEW.id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION crm_publication_index_from_media() RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.alt_text IS NOT DISTINCT FROM OLD.alt_text
       AND NEW.post_id IS NOT DISTINCT FROM OLD.post_id THEN
        RETURN NULL;
    END IF;
    PERFORM crm_refresh_publication_index(COALESCE(NEW.post_id, OLD.post_id));
    IF TG_OP = 'UPDATE' AND NEW.post_id IS DISTINCT FROM OLD.post_id THEN
        PERFORM crm_refresh_publication_index(OLD.post_id);
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_crm_publication_index_post ON social_posts;
CREATE TRIGGER trg_crm_publication_index_post
    AFTER INSERT OR UPDATE OF body, topic, moderation_status, author_user_id, author_company_id, author_kind
    ON social_posts
    FOR EACH ROW EXECUTE FUNCTION crm_publication_index_from_post();

DROP TRIGGER IF EXISTS trg_crm_publication_index_user ON users;
CREATE TRIGGER trg_crm_publication_index_user
    AFTER UPDATE OF display_name ON users
    FOR EACH ROW EXECUTE FUNCTION crm_publication_index_from_user();

DROP TRIGGER IF EXISTS trg_crm_publication_index_company ON companies;
CREATE TRIGGER trg_crm_publication_index_company
    AFTER UPDATE OF trade_name, legal_name ON companies
    FOR EACH ROW EXECUTE FUNCTION crm_publication_index_from_company();

DROP TRIGGER IF EXISTS trg_crm_publication_index_media ON post_media;
CREATE TRIGGER trg_crm_publication_index_media
    AFTER INSERT OR DELETE OR UPDATE OF alt_text, post_id ON post_media
    FOR EACH ROW EXECUTE FUNCTION crm_publication_index_from_media();

SELECT crm_refresh_publication_index(id) FROM social_posts;
