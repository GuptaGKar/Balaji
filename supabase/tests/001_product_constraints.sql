-- pgTAP: product constraints (IMPLEMENTATION_PLAN.md §4.2)
-- Runs in a transaction; all changes roll back.

BEGIN;
SELECT plan(9);

-- 1. Negative stock is rejected (CHECK stock_qty >= 0).
SELECT throws_ok(
  $sql$INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
       VALUES ('TST-NEGSTOCK', 'Negative stock', 'PACKED', 100, 1, 'pack', 1, 1, -1)$sql$,
  null, null,
  'negative stock_qty is rejected');

-- 2. Negative price is rejected (CHECK price_paise >= 0).
SELECT throws_ok(
  $sql$INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
       VALUES ('TST-NEGPRICE', 'Negative price', 'PACKED', -1, 1, 'pack', 1, 1, 0)$sql$,
  null, null,
  'negative price_paise is rejected');

-- 3. PACKED product must have price_basis_qty = 1.
SELECT throws_ok(
  $sql$INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
       VALUES ('TST-PKBASIS', 'Packed bad basis', 'PACKED', 100, 1000, 'pack', 1, 1, 0)$sql$,
  null, null,
  'PACKED with price_basis_qty <> 1 is rejected');

-- 4. PACKED product must have step_qty = 1 (integer pack counts only).
SELECT throws_ok(
  $sql$INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
       VALUES ('TST-PKSTEP', 'Packed bad step', 'PACKED', 100, 1, 'pack', 10, 1, 0)$sql$,
  null, null,
  'PACKED with step_qty <> 1 is rejected');

-- 5. WEIGHTED product must price per 100 g or per 1 kg only.
SELECT throws_ok(
  $sql$INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
       VALUES ('TST-WTBASIS', 'Weighted bad basis', 'WEIGHTED', 100, 250, 'g', 10, 10, 0)$sql$,
  null, null,
  'WEIGHTED with price_basis_qty not in (100, 1000) is rejected');

-- 6. min_qty must be positive.
SELECT throws_ok(
  $sql$INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
       VALUES ('TST-MINQTY', 'Bad min qty', 'PACKED', 100, 1, 'pack', 1, 0, 0)$sql$,
  null, null,
  'min_qty <= 0 is rejected');

-- 7. max_qty must be >= min_qty.
SELECT throws_ok(
  $sql$INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, max_qty, stock_qty)
       VALUES ('TST-MAXQTY', 'Bad max qty', 'PACKED', 100, 1, 'pack', 1, 5, 4, 0)$sql$,
  null, null,
  'max_qty < min_qty is rejected');

-- 8. SKU must be unique.
SELECT lives_ok(
  $sql$INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
       VALUES ('TST-DUPSKU', 'First', 'PACKED', 100, 1, 'pack', 1, 1, 0)$sql$,
  'first insert with a test SKU succeeds');
SELECT throws_ok(
  $sql$INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty)
       VALUES ('TST-DUPSKU', 'Second', 'PACKED', 100, 1, 'pack', 1, 1, 0)$sql$,
  null, null,
  'duplicate SKU is rejected');

-- Note: fractional PACKED quantities are rejected by M4 RPC input
-- validation (the database column is integer base units; PostgreSQL
-- rounds decimal strings on cast, so the RPC must validate the raw
-- JSON input before casting). See docs/DECISIONS.md.

SELECT * FROM finish();
ROLLBACK;
