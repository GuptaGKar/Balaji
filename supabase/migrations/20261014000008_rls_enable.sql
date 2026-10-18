-- M1: enable Row Level Security on every table (IMPLEMENTATION_PLAN.md §4.1, §5.2)
-- Deny-all default. Per-role policies and the catalogue read views are added in M2.
-- Local development and migrations run as the database owner, which bypasses RLS,
-- so seed and maintenance operations still work.

ALTER TABLE profiles               ENABLE ROW LEVEL SECURITY;
ALTER TABLE store_settings         ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_areas          ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories             ENABLE ROW LEVEL SECURITY;
ALTER TABLE products               ENABLE ROW LEVEL SECURITY;
ALTER TABLE addresses              ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items            ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_status_events    ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_assignments      ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_transitions      ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_movements    ENABLE ROW LEVEL SECURITY;
ALTER TABLE bill_counters          ENABLE ROW LEVEL SECURITY;
ALTER TABLE pos_sales              ENABLE ROW LEVEL SECURITY;
ALTER TABLE bills                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE bill_items             ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments               ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_events         ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys       ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs             ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_outbox    ENABLE ROW LEVEL SECURITY;

-- No default grants to anon/authenticated (plan §4.1). Catalogue reads arrive via
-- views in M2/M3; all writes go through RPCs or tightly scoped policies.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
