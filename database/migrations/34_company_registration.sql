-- A request grants no corporate permission. Only a separately authorized review
-- creates a verified company and its owner, in one transaction.
CREATE TABLE IF NOT EXISTS csr_company_registrations (
 id UUID PRIMARY KEY,
 applicant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 payload_hash TEXT NOT NULL,
 legal_name TEXT NOT NULL CHECK(length(legal_name) BETWEEN 2 AND 160),
 trade_name TEXT NOT NULL DEFAULT '' CHECK(length(trade_name)<=160),
 tax_id TEXT NOT NULL CHECK(length(tax_id) BETWEEN 5 AND 30),
 website_url TEXT NOT NULL DEFAULT '' CHECK(length(website_url)<=500),
 applicant_note TEXT NOT NULL CHECK(length(applicant_note) BETWEEN 10 AND 2000),
 status TEXT NOT NULL DEFAULT 'PENDING_REVIEW' CHECK(status IN ('PENDING_REVIEW','APPROVED','REJECTED','WITHDRAWN')),
 company_id UUID UNIQUE REFERENCES companies(id),
 review_request_id UUID UNIQUE,
 reviewer_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
 review_reason TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK((status='APPROVED')=(company_id IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS csr_registration_pending_applicant
 ON csr_company_registrations(applicant_user_id) WHERE status='PENDING_REVIEW';
CREATE INDEX IF NOT EXISTS csr_registration_status_time ON csr_company_registrations(status,created_at);
