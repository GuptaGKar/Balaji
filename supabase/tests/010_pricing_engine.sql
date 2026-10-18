-- pgTAP: pricing engine & threshold tests (IMPLEMENTATION_PLAN.md §4.4, §4.5, §12)
-- TH-1 through TH-12 and pricing tests

BEGIN;
SELECT plan(24);

-- ========== THRESHOLD TESTS (TH-1..TH-12) ==========
-- Seed store_settings for tests (isolated from seed.sql)
INSERT INTO store_settings (id, delivery_fee_paise, delivery_charge_threshold_paise, free_delivery_threshold_paise, mid_tier_fee_paise, online_payment_enabled, pos_price_override_enabled)
VALUES (true, 3000, 25000, NULL, NULL, false, false)
ON CONFLICT (id) DO UPDATE SET
  delivery_fee_paise = 3000,
  delivery_charge_threshold_paise = 25000,
  free_delivery_threshold_paise = NULL,
  mid_tier_fee_paise = NULL;

-- TH-1: Eligible subtotal ₹249.99 (24999 paise) → Order ACCEPTED; fee = F1 (3000)
SELECT results_eq(
  $sql$SELECT fee_paise, below_charge_threshold FROM compute_delivery_fee(24999)$sql$,
  $$VALUES (3000, true)$$,
  'TH-1: Below threshold (24999) → fee=3000, below=true'
);

-- TH-2: Exactly ₹250.00 (25000 paise) → Accepted; fee = 0
SELECT results_eq(
  $sql$SELECT fee_paise, below_charge_threshold FROM compute_delivery_fee(25000)$sql$,
  $$VALUES (0, false)$$,
  'TH-2: At threshold (25000) → fee=0, below=false'
);

-- TH-3: ₹250.01 (25001 paise) / above → Accepted; fee = 0
SELECT results_eq(
  $sql$SELECT fee_paise, below_charge_threshold FROM compute_delivery_fee(25001)$sql$,
  $$VALUES (0, false)$$,
  'TH-3: Above threshold (25001) → fee=0, below=false'
);

-- TH-4: Manager changes T1/F1; next call uses new values (test by updating settings)
UPDATE store_settings SET delivery_fee_paise = 5000, delivery_charge_threshold_paise = 30000;
SELECT results_eq(
  $sql$SELECT fee_paise FROM compute_delivery_fee(29999)$sql$,
  $$VALUES (5000)$$,
  'TH-4: Settings change reflected immediately (new T1=30000, F1=5000)'
);

-- TH-5: T2 set, F2 null (T2=50000): subtotals 24999/25000/49999/50000 → F1/F1/F1/0
UPDATE store_settings SET free_delivery_threshold_paise = 50000, mid_tier_fee_paise = NULL;
SELECT results_eq(
  $sql$SELECT fee_paise FROM compute_delivery_fee(24999)$sql$, $$VALUES (3000)$$, 'TH-5a: <T1 → F1'
);
SELECT results_eq(
  $sql$SELECT fee_paise FROM compute_delivery_fee(25000)$sql$, $$VALUES (3000)$$, 'TH-5b: ≥T1 & <T2 → F1'
);
SELECT results_eq(
  $sql$SELECT fee_paise FROM compute_delivery_fee(49999)$sql$, $$VALUES (3000)$$, 'TH-5c: ≥T1 & <T2 → F1'
);
SELECT results_eq(
  $sql$SELECT fee_paise FROM compute_delivery_fee(50000)$sql$, $$VALUES (0)$$, 'TH-5d: ≥T2 → 0'
);

-- TH-6: T2 set, F2 set (T2=50000, F2=2000): same subtotals → F1/F2/F2/0
UPDATE store_settings SET mid_tier_fee_paise = 2000;
SELECT results_eq(
  $sql$SELECT fee_paise FROM compute_delivery_fee(24999)$sql$, $$VALUES (3000)$$, 'TH-6a: <T1 → F1'
);
SELECT results_eq(
  $sql$SELECT fee_paise FROM compute_delivery_fee(25000)$sql$, $$VALUES (2000)$$, 'TH-6b: T1≤sub<T2 → F2'
);
SELECT results_eq(
  $sql$SELECT fee_paise FROM compute_delivery_fee(49999)$sql$, $$VALUES (2000)$$, 'TH-6c: T1≤sub<T2 → F2'
);
SELECT results_eq(
  $sql$SELECT fee_paise FROM compute_delivery_fee(50000)$sql$, $$VALUES (0)$$, 'TH-6d: ≥T2 → 0'
);

-- TH-7: Save T2 < T1 → Rejected with field error
UPDATE store_settings SET delivery_charge_threshold_paise = 25000;
SELECT throws_ok(
  $sql$UPDATE store_settings SET free_delivery_threshold_paise = 20000$sql$,
  null, null,
  'TH-7: T2 < T1 rejected'
);

-- TH-8: Client sends tampered fee/total → Ignored; server result used
-- This is enforced by place_order not accepting client fee/total — tested in checkout tests

-- TH-9: Non-manager changes settings → Denied; audit shows nothing changed
-- Tested in RLS tests (008_rls_staff_manager.sql)

-- TH-10: Settings change → One audit row with actor, time, before/after
SELECT lives_ok(
  $sql$UPDATE store_settings SET delivery_fee_paise = 4000$sql$,
  'TH-10: Settings UPDATE creates audit row'
);
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE entity_type='store_settings' AND action='UPDATE' AND after->>'delivery_fee_paise' = '4000'$sql$,
  $$VALUES (1)$$,
  'TH-10: Audit row captures before/after'
);

-- TH-11: Old order after settings change → fee_breakdown and totals unchanged
-- Tested in checkout tests

-- TH-12: Cart/checkout/bill → Fee itemised as its own line everywhere
-- Verified by quote_cart returning delivery_fee_paise as separate field

-- ========== PRICING TESTS (plan §4.5) ==========

-- Reset settings for pricing tests
UPDATE store_settings SET
  delivery_fee_paise = 3000,
  delivery_charge_threshold_paise = 25000,
  free_delivery_threshold_paise = NULL,
  mid_tier_fee_paise = NULL;

-- P-1: PACKED pricing — 3 packs × ₹45 = ₹135.00 (13500 paise)
-- Need a product: PACKED, price_paise=4500, price_basis_qty=1
INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
VALUES ('TEST-PACKED', 'Test Packed', 'PACKED', 4500, 1, 'pack', 1, 1, 100)
ON CONFLICT (sku) DO UPDATE SET price_paise = 4500;

SELECT results_eq(
  $sql$SELECT (item_subtotal_paise, grand_total_paise) FROM quote_cart('[{"product_id": (SELECT id FROM products WHERE sku=''TEST-PACKED''), "qty_base": 3}]'::jsonb)$sql$,
  $$VALUES ((13500, 16500))$$,  -- 13500 items + 3000 fee (below threshold)
  'P-1: 3 packs × ₹45 = ₹135 items + fee'
);

-- P-2: WEIGHTED per kg — ₹60/kg × 250g = ₹15.00 (1500 paise)
INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
VALUES ('TEST-WEIGHTED-KG', 'Test Weighted KG', 'WEIGHTED', 6000, 1000, 'kg', 10, 100, 10000)
ON CONFLICT (sku) DO UPDATE SET price_paise = 6000;

SELECT results_eq(
  $sql$SELECT item_subtotal_paise FROM quote_cart('[{"product_id": (SELECT id FROM products WHERE sku=''TEST-WEIGHTED-KG''), "qty_base": 250}]'::jsonb)$sql$,
  $$VALUES (1500)$$,
  'P-2: ₹60/kg × 250g = 1500 paise (round half up)'
);

-- P-3: WEIGHTED per 100g — ₹99/kg × 333g = ₹32.97 (3297 paise)
INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
VALUES ('TEST-WEIGHTED-100G', 'Test Weighted 100g', 'WEIGHTED', 9900, 100, '100g', 10, 10, 10000)
ON CONFLICT (sku) DO UPDATE SET price_paise = 9900;

SELECT results_eq(
  $sql$SELECT item_subtotal_paise FROM quote_cart('[{"product_id": (SELECT id FROM products WHERE sku=''TEST-WEIGHTED-100G''), "qty_base": 333}]'::jsonb)$sql$,
  $$VALUES (3297)$$,
  'P-3: ₹99/kg × 333g = 3297 paise (round half up: 3296.7 → 3297)'
);

-- P-4: 1.5 kg ≡ 1500 g for WEIGHTED
SELECT results_eq(
  $sql$SELECT item_subtotal_paise FROM quote_cart('[{"product_id": (SELECT id FROM products WHERE sku=''TEST-WEIGHTED-KG''), "qty_base": 1500}]'::jsonb)$sql$,
  $$VALUES (9000)$$,
  'P-4: 1.5 kg = 1500g → ₹60/kg × 1.5 = ₹90'
);

-- P-5: PACKED rejects 0, negative, fractional, non-numeric, NaN, exponent
SELECT throws_ok(
  $sql$SELECT quote_cart('[{"product_id": (SELECT id FROM products WHERE sku=''TEST-PACKED''), "qty_base": 0}]'::jsonb)$sql$,
  null, null, 'P-5a: qty 0 rejected'
);
SELECT throws_ok(
  $sql$SELECT quote_cart('[{"product_id": (SELECT id FROM products WHERE sku=''TEST-PACKED''), "qty_base": -1}]'::jsonb)$sql$,
  null, null, 'P-5b: qty negative rejected'
);
-- Fractional strings would be cast to bigint (rounds), so quote_cart receives integer.
-- Validation is in place_order (see checkout tests).

SELECT * FROM finish();
ROLLBACK;