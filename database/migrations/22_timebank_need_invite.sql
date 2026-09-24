-- El autor invita a un vecino a apoyar el pedido. El aviso llega a su bandeja.

CREATE TABLE IF NOT EXISTS timebank_need_invites (
    need_id     UUID NOT NULL REFERENCES timebank_needs (id) ON DELETE CASCADE,
    invitee_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    inviter_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (need_id, invitee_id)
);

CREATE INDEX IF NOT EXISTS idx_timebank_need_invites_invitee
    ON timebank_need_invites (invitee_id, created_at DESC);
