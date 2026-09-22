-- Bandeja web: lecturas de avisos sintetizados. Tags de demo para el banco de tiempo.

CREATE TABLE IF NOT EXISTS notice_reads (
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    notice_key  TEXT NOT NULL,
    read_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, notice_key)
);

CREATE INDEX IF NOT EXISTS idx_notice_reads_user ON notice_reads (user_id, read_at DESC);

-- Ana necesita jardinería; Bruno la ofrece. Así el match de lab tiene con quién hablar.
INSERT INTO user_skills (user_id, tag_id, offered, requested)
SELECT '11111111-1111-1111-1111-111111111111', id, FALSE, TRUE
FROM skill_tags WHERE slug = 'gardening'
ON CONFLICT (user_id, tag_id) DO UPDATE
    SET requested = TRUE;

INSERT INTO user_skills (user_id, tag_id, offered, requested)
SELECT '22222222-2222-2222-2222-222222222222', id, TRUE, FALSE
FROM skill_tags WHERE slug = 'gardening'
ON CONFLICT (user_id, tag_id) DO UPDATE
    SET offered = TRUE;

INSERT INTO user_skills (user_id, tag_id, offered, requested)
SELECT '11111111-1111-1111-1111-111111111111', id, TRUE, FALSE
FROM skill_tags WHERE slug = 'cooking'
ON CONFLICT (user_id, tag_id) DO UPDATE
    SET offered = TRUE;

INSERT INTO timebank_matches (id, requester_id, provider_id, tag_id, status, chat_enabled)
SELECT
    'b1000000-0000-4000-8000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    t.id,
    'ACTIVE',
    TRUE
FROM skill_tags t
WHERE t.slug = 'gardening'
  AND NOT EXISTS (
      SELECT 1 FROM timebank_matches m WHERE m.id = 'b1000000-0000-4000-8000-000000000001'
  );

INSERT INTO timebank_messages (id, match_id, sender_id, body)
SELECT
    'b1000000-0000-4000-8000-000000000011',
    'b1000000-0000-4000-8000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    '¿Me das una mano el sábado con la huerta del fondo?'
WHERE NOT EXISTS (
    SELECT 1 FROM timebank_messages m WHERE m.id = 'b1000000-0000-4000-8000-000000000011'
);

INSERT INTO timebank_messages (id, match_id, sender_id, body)
SELECT
    'b1000000-0000-4000-8000-000000000012',
    'b1000000-0000-4000-8000-000000000001',
    '22222222-2222-2222-2222-222222222222',
    'Dale. Llevo plantines y las herramientas.'
WHERE NOT EXISTS (
    SELECT 1 FROM timebank_messages m WHERE m.id = 'b1000000-0000-4000-8000-000000000012'
);
