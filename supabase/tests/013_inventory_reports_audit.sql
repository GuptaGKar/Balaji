-- pgTAP: Inventory, reports, audit tests (IMPLEMENTATION_PLAN.md §8)

BEGIN;
SELECT plan(30);

-- Fixtures: manager, staff, products
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'jjjjjjjj-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'manager@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'kkkkkkkk-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'staff@test.invalid', 'x', now(), now(), now());

INSERT INTO profiles (id, role, display_name)
VALUES ('jjjjjjjj-0000-0000-0000-000000000001', 'manager', 'Manager'),
       ('kkkkkkkk-0000-0000-0000-000000000001', 'staff', 'Staff');

INSERT INTO products (sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty, low_stock_threshold, active, available)
VALUES ('INV-TEST-001', 'Inventory Test Product', 'PACKED', 10000, 1, 'pack', 1, 1, 5, 10, true, true),
       ('INV-TEST-002', 'Low Stock Item', 'PACKED', 5000, 1, 'pack', 1, 1, 2, 5, true, true),
       ('INV-TEST-003', 'Reconciliation Test', 'PACKED', 8000, 1, 'pack', 1, 1, 10, 5, true, true);

-- ========== adjust_stock ==========
SET LOCAL request.jwt.claims.sub = 'kkkkkkkk-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';

-- 1. adjust_stock positive delta succeeds
SELECT lives_ok(
  $sql$SELECT adjust_stock((SELECT id FROM products WHERE sku='INV-TEST-001'), 10, 'manual_adjustment', 'Restock')$sql$,
  'adjust_stock positive delta succeeds');

-- 2. Stock increased correctly
SELECT results_eq(
  $sql$SELECT stock_qty FROM products WHERE sku='INV-TEST-001'$sql$,
  $$VALUES (15)$$,
  'stock increased by delta');

-- 3. Inventory movement recorded
SELECT results_eq(
  $sql$SELECT count(*) FROM inventory_movements WHERE product_id=(SELECT id FROM products WHERE sku='INV-TEST-001') AND reason='manual_adjustment'$sql$,
  $$VALUES (1)$$,
  'inventory movement recorded for adjustment');

-- 4. Audit log created
SELECT results_eq(
  $sql$SELECT count(*) FROM audit_logs WHERE action='ADJUST_STOCK' AND entity_type='products'$sql$,
  $$VALUES (1)$$,
  'audit log created for stock adjustment');

-- 5. Negative delta (stock reduction) succeeds
SELECT lives_ok(
  $sql$SELECT adjust_stock((SELECT id FROM products WHERE sku='INV-TEST-001'), -3, 'manual_adjustment', 'Damage')$sql$,
  'adjust_stock negative delta succeeds');

-- 6. Stock reduced correctly
SELECT results_eq(
  $sql$SELECT stock_qty FROM products WHERE sku='INV-TEST-001'$sql$,
  $$VALUES (12)$$,
  'stock reduced by delta');

-- 7. Negative delta causing negative stock rejected
SELECT throws_ok(
  $sql$SELECT adjust_stock((SELECT id FROM products WHERE sku='INV-TEST-001'), -20, 'manual_adjustment', 'Too much')$sql$,
  null, null,
  'adjust_stock negative stock rejected');

-- 8. Zero delta rejected
SELECT throws_ok(
  $sql$SELECT adjust_stock((SELECT id FROM products WHERE sku='INV-TEST-001'), 0, 'manual_adjustment', 'Zero')$sql$,
  null, null,
  'adjust_stock zero delta rejected');

-- 9. Non-staff/manager rejected
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
SET LOCAL request.jwt.claims.sub = 'cccccccc-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'customer';
SELECT throws_ok(
  $sql$SELECT adjust_stock((SELECT id FROM products WHERE sku='INV-TEST-001'), 5, 'manual_adjustment', 'Test')$sql$,
  null, null,
  'adjust_stock: customer rejected');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- ========== Low Stock List ==========
SET LOCAL request.jwt.claims.sub = 'kkkkkkkk-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';

-- 10. Low stock product appears in list
SELECT lives_ok(
  $sql$SELECT 1 FROM get_low_stock_products() WHERE sku='INV-TEST-002'$sql$,
  'low stock product appears in list');

-- 11. Non-low-stock product not in list
SELECT results_eq(
  $sql$SELECT count(*) FROM get_low_stock_products() WHERE sku='INV-TEST-001'$sql$,
  $$VALUES (0)$$,
  'non-low-stock product not in list');

-- 12. Customer cannot access low stock list
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
SET LOCAL request.jwt.claims.sub = 'cccccccc-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'customer';
SELECT throws_ok(
  $sql$SELECT * FROM get_low_stock_products()$sql$,
  null, null,
  'customer cannot access low stock list');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- ========== Stock Reconciliation ==========
SET LOCAL request.jwt.claims.sub = 'kkkkkkkk-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';

-- 13. Reconciliation check works (no discrepancy for clean products)
SELECT lives_ok(
  $sql$SELECT 1 FROM check_stock_reconciliation() WHERE sku='INV-TEST-001'$sql$,
  'reconciliation check runs');

-- 14. Customer cannot access reconciliation
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
SET LOCAL request.jwt.claims.sub = 'cccccccc-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'customer';
SELECT throws_ok(
  $sql$SELECT * FROM check_stock_reconciliation()$sql$,
  null, null,
  'customer cannot access reconciliation');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- ========== Sales Report ==========
SET LOCAL request.jwt.claims.sub = 'jjjjjjjj-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'manager';

-- Fixtures for sales report
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'llllllll-0000-0000-0000-000000000001',
        'authenticated', 'authenticated',
        'sales-cust@test.invalid', 'x', now(), now(), now());
INSERT INTO profiles (id, role, display_name)
VALUES ('llllllll-0000-0000-0000-000000000001', 'customer', 'Sales Customer');

INSERT INTO orders (public_order_number, customer_id, address_snapshot, status, payment_method, payment_status, grand_total_paise, cod_collected_paise, created_at)
VALUES ('REPORT-001', 'llllllll-0000-0000-0000-000000000001', '{}'::jsonb, 'delivered', 'cod', 'paid', 10000, 10000, now() - interval '1 day'),
       ('REPORT-002', 'llllllll-0000-0000-0000-000000000001', '{}'::jsonb, 'delivered', 'online', 'paid', 20000, null, now() - interval '2 days'),
       ('REPORT-003', 'llllllll-0000-0000-0000-000000000001', '{}'::jsonb, 'cancelled', 'cod', 'unpaid', 5000, null, now() - interval '3 days');

-- 15. Sales report returns aggregates
SELECT lives_ok(
  $sql$SELECT get_sales_report(now() - interval '7 days', now())$sql$,
  'sales report executes');

-- 16. Sales report correct totals
SELECT results_eq(
  $sql$SELECT total_orders, total_revenue_paise, total_cod_paise, total_online_paise FROM get_sales_report(now() - interval '7 days', now())$sql$,
  $$VALUES (2, 30000, 10000, 20000)$$,
  'sales report correct totals (cancelled excluded)');

-- 17. COD collected/pending
SELECT results_eq(
  $sql$SELECT cod_collected_paise, cod_pending_paise FROM get_sales_report(now() - interval '7 days', now())$sql$,
  $$VALUES (10000, 0)$$,
  'COD collected/pending correct');

-- 18. Status breakdown
SELECT lives_ok(
  $sql$SELECT by_status FROM get_sales_report(now() - interval '7 days', now())$sql$,
  'sales report includes status breakdown');

-- 19. Payment method breakdown
SELECT lives_ok(
  $sql$SELECT by_payment_method FROM get_sales_report(now() - interval '7 days', now())$sql$,
  'sales report includes payment method breakdown');

-- 19. Daily breakdown
SELECT lives_ok(
  $sql$SELECT daily_breakdown FROM get_sales_report(now() - interval '7 days', now())$sql$,
  'sales report includes daily breakdown');

-- ========== COD Reconciliation ==========
-- 20. COD reconciliation executes
SELECT lives_ok(
  $sql$SELECT get_cod_reconciliation(now() - interval '7 days', now())$sql$,
  'COD reconciliation executes');

-- 21. COD totals correct
SELECT results_eq(
  $sql$SELECT total_cod_orders, total_cod_paise, collected_paise, pending_paise FROM get_cod_reconciliation(now() - interval '7 days', now())$sql$,
  $$VALUES (2, 30000, 10000, 20000)$$,
  'COD reconciliation correct totals');

-- 21. By rider breakdown
SELECT lives_ok(
  $sql$SELECT by_rider FROM get_cod_reconciliation(now() - interval '7 days', now())$sql$,
  'COD reconciliation includes by-rider breakdown');

-- 22. Daily breakdown
SELECT lives_ok(
  $sql$SELECT daily_breakdown FROM get_cod_reconciliation(now() - interval '7 days', now())$sql$,
  'COD reconciliation includes daily breakdown');

-- ========== Audit Logs ==========
-- 23. Audit log viewer works for manager
SELECT lives_ok(
  $sql$SELECT get_audit_logs()$sql$,
  'audit log viewer executes for manager');

-- 24. Customer cannot access audit logs
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
SET LOCAL request.jwt.claims.sub = 'cccccccc-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'customer';
SELECT throws_ok(
  $sql$SELECT * FROM get_audit_logs()$sql$,
  null, null,
  'customer cannot access audit logs');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 25. Audit log filtering by date
SET LOCAL request.jwt.claims.sub = 'jjjjjjjj-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'manager';
SELECT lives_ok(
  $sql$SELECT get_audit_logs(now() - interval '1 day', now())$sql$,
  'audit log filtering by date works');

-- 25. Audit log filtering by entity type
SELECT lives_ok(
  $sql$SELECT get_audit_logs(NULL, NULL, 'products')$sql$,
  'audit log filtering by entity type works');

-- 26. Audit log filtering by actor
SELECT lives_ok(
  $sql$SELECT get_audit_logs(NULL, NULL, NULL, 'kkkkkkkk-0000-0000-0000-000000000001')$sql$,
  'audit log filtering by actor works');

-- 26. Audit log pagination
SELECT results_eq(
  $sql$SELECT count(*) FROM get_audit_logs(NULL, NULL, NULL, NULL, 1, 0)$sql$,
  $$VALUES (1)$$,
  'audit log pagination limit works');

-- ========== RLS Policies ==========
-- 27. Staff can read inventory_movements
SET LOCAL request.jwt.claims.sub = 'kkkkkkkk-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'staff';
SELECT lives_ok(
  $sql$SELECT 1 FROM inventory_movements LIMIT 1$sql$,
  'staff can read inventory_movements');

-- 28. Customer cannot read inventory_movements
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
SET LOCAL request.jwt.claims.sub = 'cccccccc-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'customer';
SELECT throws_ok(
  $sql$SELECT * FROM inventory_movements LIMIT 1$sql$,
  null, null,
  'customer cannot read inventory_movements');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

-- 29. Manager can read audit_logs
SET LOCAL request.jwt.claims.sub = 'jjjjjjjj-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'manager';
SELECT lives_ok(
  $sql$SELECT 1 FROM audit_logs LIMIT 1$sql$,
  'manager can read audit_logs');

-- 30. Customer cannot read audit_logs
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;
SET LOCAL request.jwt.claims.sub = 'cccccccc-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'customer';
SELECT throws_ok(
  $sql$SELECT * FROM audit_logs LIMIT 1$sql$,
  null, null,
  'customer cannot read audit_logs');
RESET request.jwt.claims.sub;
RESET request.jwt.claims.role;

SELECT * FROM finish();
ROLLBACK;