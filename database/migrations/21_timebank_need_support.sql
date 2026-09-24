-- Un vecino puede apoyar un pedido. Lo que da (Doy) se lee en vivo, no se elige por aviso.

CREATE TABLE IF NOT EXISTS timebank_need_supports (
    need_id     UUID NOT NULL REFERENCES timebank_needs (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (need_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_timebank_need_supports_user
    ON timebank_need_supports (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_timebank_need_supports_need
    ON timebank_need_supports (need_id, created_at);
