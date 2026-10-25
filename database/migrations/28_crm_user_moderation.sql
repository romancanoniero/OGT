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

-- Reports are moderation evidence from other people. Preserve the report
-- itself when its author is erased, without retaining the reporter identity.
ALTER TABLE content_reports ALTER COLUMN reporter_id DROP NOT NULL;
ALTER TABLE content_reports DROP CONSTRAINT IF EXISTS content_reports_reporter_id_fkey;
ALTER TABLE content_reports ADD CONSTRAINT content_reports_reporter_id_fkey
    FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE SET NULL;

-- Durable cleanup journal: delete publicly served files after the database
-- commit. Failures remain here for a future retry, never silently forgotten.
CREATE TABLE IF NOT EXISTS crm_erased_media (
    stored_name TEXT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    outcome TEXT CHECK (outcome IN ('DELETED','UNREFERENCED','SHARED'))
);
