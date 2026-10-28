-- Explicit memberships are authoritative, including suspension/removal. Genuine
-- legacy-only admins retain their original access until explicitly managed.
CREATE OR REPLACE VIEW crm_company_access AS
SELECT company_id,user_id,member_role FROM crm_company_members
 WHERE status='ACTIVE' AND accepted_at IS NOT NULL
UNION ALL
SELECT a.company_id,a.user_id,CASE WHEN a.is_primary THEN 'OWNER' ELSE 'ADMIN' END
 FROM company_admins a WHERE NOT EXISTS (
   SELECT 1 FROM crm_company_members m WHERE m.company_id=a.company_id AND m.user_id=a.user_id);

CREATE TABLE IF NOT EXISTS csr_team_invitations (
 id UUID PRIMARY KEY,
 company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
 recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 invited_by UUID REFERENCES users(id) ON DELETE SET NULL,
 member_role TEXT NOT NULL CHECK(member_role IN ('ADMIN','CAMPAIGN_MANAGER','FINANCE','ANALYST','VIEWER')),
 status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED','DECLINED','REVOKED','EXPIRED')),
 expires_at TIMESTAMPTZ NOT NULL DEFAULT now()+interval '7 days',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 decided_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS csr_team_one_invitation ON csr_team_invitations(company_id,recipient_id) WHERE status='PENDING';
CREATE INDEX IF NOT EXISTS csr_team_recipient ON csr_team_invitations(recipient_id) WHERE status='PENDING';

CREATE TABLE IF NOT EXISTS csr_ownership_transfers (
 id UUID PRIMARY KEY,
 company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
 proposed_by UUID REFERENCES users(id) ON DELETE SET NULL,
 recipient_id UUID REFERENCES users(id) ON DELETE SET NULL,
 status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED','DECLINED','CANCELLED','EXPIRED')),
 expires_at TIMESTAMPTZ NOT NULL DEFAULT now()+interval '48 hours',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 decided_at TIMESTAMPTZ,
 CHECK(proposed_by IS NULL OR recipient_id IS NULL OR proposed_by<>recipient_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS csr_team_one_transfer ON csr_ownership_transfers(company_id) WHERE status='PENDING';
