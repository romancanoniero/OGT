-- Ranking de feed estilo Instagram: grafo + descubrimiento + eventos del viewer.
-- No filtra por barrio. La geografía es señal opcional (home_location / location).

CREATE TYPE post_person_role AS ENUM ('AUTHOR', 'PROTAGONIST', 'PARTICIPANT');
CREATE TYPE feed_event_kind AS ENUM (
    'IMPRESSION', 'DWELL', 'CLAP', 'COMMENT', 'PROFILE_TAP', 'SHARE', 'HIDE', 'SKIP'
);

ALTER TABLE social_posts
    ADD COLUMN topic TEXT NOT NULL DEFAULT '';

CREATE TABLE follows (
    follower_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followed_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followed_id),
    CONSTRAINT follows_distinct_chk CHECK (follower_id <> followed_id)
);

CREATE TABLE post_people (
    post_id     UUID NOT NULL REFERENCES social_posts (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role        post_person_role NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id, role)
);

-- Un solo protagonista por publicación.
CREATE UNIQUE INDEX idx_post_people_one_protagonist
    ON post_people (post_id)
    WHERE role = 'PROTAGONIST';

CREATE TABLE feed_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    viewer_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id     UUID NOT NULL REFERENCES social_posts (id) ON DELETE CASCADE,
    kind        feed_event_kind NOT NULL,
    dwell_ms    INTEGER CHECK (dwell_ms IS NULL OR dwell_ms >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_follows_followed ON follows (followed_id);
CREATE INDEX idx_post_people_user ON post_people (user_id, role);
CREATE INDEX idx_feed_events_viewer ON feed_events (viewer_id, created_at DESC);
CREATE INDEX idx_feed_events_post ON feed_events (post_id, kind);
CREATE INDEX idx_posts_topic ON social_posts (topic)
    WHERE moderation_status = 'VISIBLE' AND is_story = FALSE;

UPDATE social_posts
SET topic = 'Comedor'
WHERE id = '55555555-5555-5555-5555-555555555555';

UPDATE social_posts
SET topic = 'RSE'
WHERE id = '66666666-6666-6666-6666-666666666666';

INSERT INTO post_people (post_id, user_id, role)
VALUES
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'AUTHOR'),
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'PROTAGONIST');

-- Bruno y Carla siguen a Ana: su post entra por grafo. Diego no sigue a nadie (cold start).
INSERT INTO follows (follower_id, followed_id)
VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111'),
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111');

INSERT INTO feed_events (viewer_id, post_id, kind)
VALUES
    ('22222222-2222-2222-2222-222222222222', '55555555-5555-5555-5555-555555555555', 'IMPRESSION'),
    ('22222222-2222-2222-2222-222222222222', '55555555-5555-5555-5555-555555555555', 'CLAP'),
    ('22222222-2222-2222-2222-222222222222', '55555555-5555-5555-5555-555555555555', 'COMMENT'),
    ('22222222-2222-2222-2222-222222222222', '55555555-5555-5555-5555-555555555555', 'PROFILE_TAP'),
    ('33333333-3333-3333-3333-333333333333', '55555555-5555-5555-5555-555555555555', 'IMPRESSION');
