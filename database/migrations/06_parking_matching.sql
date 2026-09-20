-- Ventana de interés + tope de espera del cedente. El ganador lo elige el server.
ALTER TABLE parking_spots
    ADD COLUMN IF NOT EXISTS owner_eta_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS interest_closes_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS owner_wait_deadline_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS leftover_open BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS matching_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS vehicle_label TEXT,
    ADD COLUMN IF NOT EXISTS address TEXT;

-- Plazas viejas siguen en FCFS leftover.
UPDATE parking_spots
SET leftover_open = TRUE,
    matching_resolved = TRUE
WHERE matching_resolved = FALSE
  AND interest_closes_at IS NULL;

CREATE TABLE IF NOT EXISTS parking_interests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parking_spot_id   UUID NOT NULL REFERENCES parking_spots (id) ON DELETE CASCADE,
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    eta_seconds       INTEGER NOT NULL CHECK (eta_seconds >= 1),
    community_points  INTEGER NOT NULL DEFAULT 0,
    expressed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (parking_spot_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_parking_interests_spot ON parking_interests (parking_spot_id);
CREATE INDEX IF NOT EXISTS idx_parking_matching_due ON parking_spots (status, interest_closes_at)
    WHERE matching_resolved = FALSE;
