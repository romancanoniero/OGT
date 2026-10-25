-- Moderación de cuentas del CRM central, separada del estado de acceso.
-- Bloquear publicaciones NO suspende el login: el usuario conserva lectura.
ALTER TABLE users ADD COLUMN IF NOT EXISTS publishing_blocked BOOLEAN NOT NULL DEFAULT FALSE;

-- Tombstone de uid, no de correo: impide que Firebase recree por accidente
-- la MISMA cuenta cuya eliminación fue ordenada y auditada.
CREATE TABLE IF NOT EXISTS crm_erased_accounts (
    uid_hash TEXT PRIMARY KEY CHECK (uid_hash ~ '^[a-f0-9]{64}$'),
    actor_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    reason TEXT NOT NULL CHECK (length(reason) BETWEEN 10 AND 500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
