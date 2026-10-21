-- CRM foundation: least-privilege platform staff, tenant memberships and audit.
-- No staff account is created automatically. Provision through an authorized,
-- out-of-band operational process after MFA enrollment and identity verification.
CREATE TABLE IF NOT EXISTS crm_platform_staff (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    staff_role TEXT NOT NULL CHECK (staff_role IN
        ('OWNER', 'OPERATIONS', 'TRUST_SAFETY', 'FINANCE', 'SUPPORT', 'ANALYST')),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT crm_staff_approval_chk CHECK
        (NOT enabled OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_crm_staff_enabled ON crm_platform_staff(staff_role)
    WHERE enabled;

CREATE TABLE IF NOT EXISTS crm_company_members (
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    member_role TEXT NOT NULL CHECK (member_role IN
        ('OWNER', 'ADMIN', 'CAMPAIGN_MANAGER', 'FINANCE', 'ANALYST', 'VIEWER')),
    status TEXT NOT NULL DEFAULT 'INVITED'
        CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REMOVED')),
    invited_by UUID REFERENCES users(id) ON DELETE SET NULL,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_crm_company_member_user
    ON crm_company_members(user_id, company_id) WHERE status = 'ACTIVE';

-- Existing enterprise admins keep their existing permissions; explicit membership
-- is prepared for a later cutover once all endpoints use the new role matrix.
INSERT INTO crm_company_members (company_id, user_id, member_role, status, accepted_at)
SELECT company_id, user_id, CASE WHEN is_primary THEN 'OWNER' ELSE 'ADMIN' END,
       'ACTIVE', created_at
FROM company_admins
ON CONFLICT (company_id, user_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS crm_audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    company_id UUID REFERENCES companies(id) ON DELETE SET NULL,
    action TEXT NOT NULL CHECK (length(action) BETWEEN 3 AND 120),
    target_type TEXT NOT NULL CHECK (length(target_type) BETWEEN 2 AND 80),
    target_id TEXT,
    reason TEXT,
    request_id TEXT,
    ip_hash TEXT,
    outcome TEXT NOT NULL CHECK (outcome IN ('SUCCESS', 'DENIED', 'ERROR')),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_crm_audit_actor
    ON crm_audit_events(actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_company
    ON crm_audit_events(company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_audit_time ON crm_audit_events(created_at DESC);

CREATE OR REPLACE FUNCTION crm_audit_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'crm_audit_events is append-only';
END;
$$;
DROP TRIGGER IF EXISTS trg_crm_audit_immutable ON crm_audit_events;
CREATE TRIGGER trg_crm_audit_immutable
    BEFORE UPDATE OR DELETE ON crm_audit_events
    FOR EACH ROW EXECUTE FUNCTION crm_audit_immutable();
