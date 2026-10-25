-- Las adopciones y alertas de animales son social_posts reales.
-- El CRM necesita encontrar el nombre y el título de las fichas, además del cuerpo.
-- Deja intacto el resto del índice, agregando orígenes indexados y sincronizados.
ALTER TABLE crm_publication_index
  DROP CONSTRAINT IF EXISTS crm_publication_index_origin_check;
ALTER TABLE crm_publication_index
  ADD CONSTRAINT crm_publication_index_origin_check
  CHECK (origin IN ('author', 'label', 'topic', 'body', 'photo', 'id', 'pet_name', 'animal_title'));

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
    pet_name_text TEXT;
    animal_title_text TEXT;
BEGIN
    SELECT p.moderation_status::text,
           COALESCE(NULLIF(btrim(u.display_name), ''), NULLIF(btrim(c.trade_name), ''), NULLIF(btrim(c.legal_name), '')),
           NULLIF(btrim(p.topic), ''),
           NULLIF(btrim(p.body), ''),
           NULLIF(btrim(a.pet_name), ''),
           NULLIF(btrim(a.title), '')
      INTO status, stored, topic_text, body_text, pet_name_text, animal_title_text
      FROM social_posts p
      LEFT JOIN animal_listings a ON a.id = p.listing_id
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
    IF pet_name_text IS NOT NULL THEN
        INSERT INTO crm_publication_index (post_id, origin, value)
        VALUES (target, 'pet_name', pet_name_text);
    END IF;
    IF animal_title_text IS NOT NULL THEN
        INSERT INTO crm_publication_index (post_id, origin, value)
        VALUES (target, 'animal_title', animal_title_text);
    END IF;
    IF photo_text IS NOT NULL THEN
        INSERT INTO crm_publication_index (post_id, origin, value)
        VALUES (target, 'photo', photo_text);
    END IF;
END;
$$;

-- Una edición de la ficha actualiza inmediatamente las búsquedas del CRM.
CREATE OR REPLACE FUNCTION crm_publication_index_from_animal() RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND NEW.pet_name IS NOT DISTINCT FROM OLD.pet_name
       AND NEW.title IS NOT DISTINCT FROM OLD.title THEN
        RETURN NULL;
    END IF;
    PERFORM crm_refresh_publication_index(p.id)
      FROM social_posts p
     WHERE p.listing_id = NEW.id;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_crm_publication_index_animal ON animal_listings;
CREATE TRIGGER trg_crm_publication_index_animal
    AFTER INSERT OR UPDATE OF pet_name, title ON animal_listings
    FOR EACH ROW EXECUTE FUNCTION crm_publication_index_from_animal();

-- Recuperar nombres/títulos en publicaciones históricas, incluidas las de hace meses.
SELECT crm_refresh_publication_index(p.id)
  FROM social_posts p
  JOIN animal_listings a ON a.id = p.listing_id;

COMMENT ON TABLE crm_publication_index IS
    'Búsqueda CRM de publicaciones visibles: autor, tema, cuerpo, foto, ID, nombre y título de mascota.';
