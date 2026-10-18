-- M1: gated payment tables and cross-cutting tables (IMPLEMENTATION_PLAN.md §4.2)

-- payments: only populated when online payments are enabled (M11 gate).
-- Never store payment secrets or full card data.
CREATE TABLE payments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  provider text NOT NULL,
  provider_order_id text,
  provider_payment_id text,
  amount_paise bigint NOT NULL CHECK (amount_paise >= 0),
  status text NOT NULL DEFAULT 'pending',
  verified_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order ON payments(order_id);

-- Webhook dedupe: exactly one processed row per provider event (plan §4.2, §10).
CREATE TABLE payment_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider text NOT NULL,
  provider_event_id text NOT NULL,
  payload_hash text NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now(),
  processed_at timestamptz,
  UNIQUE (provider, provider_event_id)
);

-- Idempotency: primary key (user, scope, key); request_hash distinguishes a
-- retry of the same request from a conflicting reuse of the key (plan §4.7).
CREATE TABLE idempotency_keys (
  user_id uuid NOT NULL,
  scope text NOT NULL,
  key text NOT NULL,
  request_hash text NOT NULL,
  response jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, scope, key)
);

-- Append-only audit log (plan §4.2, §4.9). Populated by RPCs and triggers from
-- M2. Rows are never updated or deleted; sensitive values are redacted before
-- insert (redaction function added in M2).
-- entity_id is text to accommodate tables with non-uuid PKs (e.g., store_settings boolean singleton).
CREATE TABLE audit_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_id uuid REFERENCES profiles(id),
  action text NOT NULL,
  entity_type text NOT NULL,
  entity_id text,
  before jsonb,
  after jsonb,
  request_id text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at DESC);

REVOKE UPDATE, DELETE ON audit_logs FROM PUBLIC;

-- Notification outbox: async notifications never roll back a valid order (§4.7).
-- MVP uses in-app status + manual phone contact; providers are P1 (M10).
CREATE TABLE notification_outbox (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid REFERENCES orders(id) ON DELETE CASCADE,
  channel text NOT NULL,
  template text NOT NULL,
  status text NOT NULL DEFAULT 'pending',
  attempts integer NOT NULL DEFAULT 0,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now()
);
