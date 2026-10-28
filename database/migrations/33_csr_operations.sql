-- Business operations add an auditable ledger without changing existing balances.
ALTER TABLE companies ADD COLUMN IF NOT EXISTS payment_debt_cents BIGINT NOT NULL DEFAULT 0 CHECK(payment_debt_cents>=0);
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS reserved_cents BIGINT NOT NULL DEFAULT 0 CHECK(reserved_cents>=0);
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS redeemed_by_user UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE TABLE IF NOT EXISTS csr_operations (
 id UUID PRIMARY KEY,
 company_id UUID NOT NULL REFERENCES companies(id),
 actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
 kind TEXT NOT NULL,
 payload_hash TEXT NOT NULL,
 result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS csr_ledger (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 company_id UUID NOT NULL REFERENCES companies(id),
 campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL,
 operation_id UUID REFERENCES csr_operations(id),
 actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
 kind TEXT NOT NULL,
 available_delta_cents BIGINT NOT NULL DEFAULT 0,
 reserved_delta_cents BIGINT NOT NULL DEFAULT 0,
 spent_delta_cents BIGINT NOT NULL DEFAULT 0,
 debt_delta_cents BIGINT NOT NULL DEFAULT 0,
 source_key TEXT NOT NULL,
 note TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(company_id,source_key)
);
CREATE INDEX IF NOT EXISTS csr_ledger_company_time ON csr_ledger(company_id,created_at DESC);
CREATE OR REPLACE FUNCTION csr_ledger_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='UPDATE' AND NEW.actor_user_id IS NULL AND OLD.actor_user_id IS NOT NULL
     AND NOT EXISTS(SELECT 1 FROM users WHERE id=OLD.actor_user_id)
     AND (to_jsonb(NEW)-'actor_user_id')=(to_jsonb(OLD)-'actor_user_id') THEN
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'csr_ledger is append-only';
END $$;
DROP TRIGGER IF EXISTS csr_ledger_immutable_guard ON csr_ledger;
CREATE TRIGGER csr_ledger_immutable_guard BEFORE UPDATE OR DELETE ON csr_ledger
  FOR EACH ROW EXECUTE FUNCTION csr_ledger_immutable();
CREATE TABLE IF NOT EXISTS csr_action_funding (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 outcome_id UUID UNIQUE REFERENCES action_outcomes(id) ON DELETE SET NULL,
 company_id UUID NOT NULL REFERENCES companies(id),
 campaign_id UUID NOT NULL REFERENCES campaigns(id),
 operation_id UUID NOT NULL REFERENCES csr_operations(id),
 amount_cents BIGINT NOT NULL CHECK(amount_cents>0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS csr_payment_orders (
 id UUID PRIMARY KEY,
 company_id UUID NOT NULL REFERENCES companies(id),
 actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
 amount_cents BIGINT NOT NULL CHECK(amount_cents>0),
 currency TEXT NOT NULL DEFAULT 'ARS' CHECK(currency='ARS'),
 live_mode BOOLEAN NOT NULL,
 preference_id TEXT UNIQUE,
 checkout_url TEXT,
 payment_id TEXT UNIQUE,
 provider_status TEXT NOT NULL DEFAULT 'PENDING',
 credited_net_cents BIGINT NOT NULL DEFAULT 0 CHECK(credited_net_cents>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS csr_payment_jobs (
 payment_id TEXT PRIMARY KEY CHECK(payment_id ~ '^[0-9]{1,30}$'),
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 attempts INTEGER NOT NULL DEFAULT 0,
 done BOOLEAN NOT NULL DEFAULT false,
 last_error_code TEXT,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE csr_payment_orders ADD COLUMN IF NOT EXISTS next_lookup_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE csr_payment_orders ADD COLUMN IF NOT EXISTS lookup_attempts INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS csr_payment_order_lookup ON csr_payment_orders(next_lookup_at) WHERE payment_id IS NULL;
CREATE INDEX IF NOT EXISTS csr_payment_all_order_lookup ON csr_payment_orders(next_lookup_at) WHERE preference_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS csr_payment_pending ON csr_payment_jobs(next_attempt_at) WHERE NOT done;
CREATE TABLE IF NOT EXISTS csr_payment_incidents (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 payment_id TEXT NOT NULL,
 order_id UUID REFERENCES csr_payment_orders(id),
 reason TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 resolved_at TIMESTAMPTZ,
 resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
 UNIQUE(payment_id,reason)
);
-- Activation replays additive migrations. Import opening amounts exactly once,
-- never again after real payments or reservations have changed those amounts.
CREATE TABLE IF NOT EXISTS csr_schema_state (version INTEGER PRIMARY KEY);
DO $$ BEGIN
  PERFORM pg_advisory_xact_lock(hashtextextended('csr-opening-v33',0));
  IF NOT EXISTS(SELECT 1 FROM csr_schema_state WHERE version=33) THEN
    INSERT INTO csr_ledger(company_id,kind,available_delta_cents,source_key,note)
      SELECT id,'OPENING',campaign_balance_cents,'opening-v33','Saldo existente al iniciar el registro conciliado; no acredita un nuevo pago.'
      FROM companies WHERE campaign_balance_cents>0;
    INSERT INTO csr_ledger(company_id,campaign_id,kind,spent_delta_cents,reserved_delta_cents,source_key,note)
      SELECT company_id,id,'OPENING_CAMPAIGN',spent_cents,reserved_cents,'opening-campaign:'||id::text,'Importe existente de campaña; no genera un nuevo gasto.'
      FROM campaigns WHERE spent_cents>0 OR reserved_cents>0;
    INSERT INTO csr_schema_state(version) VALUES(33);
  END IF;
END $$;
