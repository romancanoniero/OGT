-- Additive: preserve publication kinds, credits, media, invitations and historical labels.
ALTER TABLE recognition_categories ADD COLUMN IF NOT EXISTS tag_key TEXT;
ALTER TABLE recognition_categories ADD COLUMN IF NOT EXISTS requires_recognition BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE recognition_categories SET tag_key = translate(lower(regexp_replace(label,'[^[:alnum:]_]','','g')),'áéíóúüñ','aeiouun') WHERE tag_key IS NULL;
-- Nullable for compatibility with re-running migration 39 and older administrative clients.
CREATE UNIQUE INDEX IF NOT EXISTS recognition_categories_tag_key ON recognition_categories(tag_key);
INSERT INTO recognition_categories(id,label,icon,sort_order,tag_key,requires_recognition) VALUES
 ('buena-accion','Buena Acción','🤝',0,'buenaaccion',TRUE),
 ('ternura','Ternura','💛',1,'ternura',FALSE),
 ('perros','Perros','🐶',70,'perros',FALSE),
 ('gatos','Gatos','🐱',80,'gatos',FALSE)
ON CONFLICT DO NOTHING;

ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS hashtags JSONB NOT NULL DEFAULT '[]'::jsonb;
-- Backfill once without changing any existing topic or kind. Inactive categories remain readable.
-- This metadata backfill must not turn historical posts into newly edited posts.
-- Only the timestamp trigger is suspended under this statement's exclusive table
-- lock. PostgreSQL rolls back its state on failure; every other trigger stays active.
DO $backfill$
DECLARE timestamp_trigger_mode "char";
BEGIN
    IF EXISTS (SELECT 1 FROM social_posts WHERE hashtags='[]'::jsonb AND length(btrim(COALESCE(topic,'')))>=2) THEN
        SELECT tgenabled INTO timestamp_trigger_mode FROM pg_trigger
        WHERE tgrelid='social_posts'::regclass AND tgname='trg_posts_updated' AND NOT tgisinternal;
        IF timestamp_trigger_mode IN ('O','A','R') THEN
            ALTER TABLE social_posts DISABLE TRIGGER trg_posts_updated;
        END IF;
        UPDATE social_posts p SET hashtags = jsonb_build_array(jsonb_build_object(
         'key',COALESCE(c.tag_key,translate(lower(regexp_replace(p.topic,'[^[:alnum:]_]','','g')),'áéíóúüñ','aeiouun')),
         'label',p.topic,'categoryId',c.id))
        FROM (SELECT s.id,COALESCE(s.recognition_category_id,rc.id) AS category_id FROM social_posts s LEFT JOIN recognition_categories rc ON lower(rc.label)=lower(s.topic)) old
        LEFT JOIN recognition_categories c ON c.id=old.category_id
        WHERE p.id=old.id AND p.hashtags='[]'::jsonb AND length(btrim(COALESCE(p.topic,'')))>=2;
        IF timestamp_trigger_mode='O' THEN
            ALTER TABLE social_posts ENABLE TRIGGER trg_posts_updated;
        ELSIF timestamp_trigger_mode='A' THEN
            ALTER TABLE social_posts ENABLE ALWAYS TRIGGER trg_posts_updated;
        ELSIF timestamp_trigger_mode='R' THEN
            ALTER TABLE social_posts ENABLE REPLICA TRIGGER trg_posts_updated;
        END IF;
    END IF;
END
$backfill$;
CREATE INDEX IF NOT EXISTS social_posts_hashtags_gin ON social_posts USING gin(hashtags jsonb_path_ops);
CREATE INDEX IF NOT EXISTS feed_events_viewer_recent ON feed_events(viewer_id,created_at DESC);
