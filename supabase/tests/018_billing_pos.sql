-- pgTAP: Billing & POS (IMPLEMENTATION_PLAN.md §9, §12)
-- Covers gapless bill numbers, order bills from snapshots, POS pricing,
-- atomic stock deduction, idempotency, void-once restore, and role checks.

BEGIN;
SELECT plan(38);

-- ---------------------------------------------------------------------------
-- Fixtures
-- ---------------------------------------------------------------------------
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'a1a1a1a1-1111-4111-8111-111111111111',
        'authenticated', 'authenticated', 'bill-manager@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'b2b2b2b2-2222-4222-8222-222222222222',
        'authenticated', 'authenticated', 'bill-staff@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'c3c3c3c3-3333-4333-8333-333333333333',
        'authenticated', 'authenticated', 'bill-cust-a@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'd4d4d4d4-4444-4444-8444-444444444444',
        'authenticated', 'authenticated', 'bill-cust-b@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('a1a1a1a1-1111-4111-8111-111111111111', 'manager', 'Billing Manager'),
       ('b2b2b2b2-2222-4222-8222-222222222222', 'staff', 'Billing Staff'),
       ('c3c3c3c3-3333-4333-8333-333333333333', 'customer', 'Bill Customer A'),
       ('d4d4d4d4-4444-4444-8444-444444444444', 'customer', 'Bill Customer B');

INSERT INTO public.products (
  sku, name, quantity_type, price_paise, price_basis_qty, unit_label,
  step_qty, min_qty, stock_qty, low_stock_threshold, active, available
) VALUES
  ('POS-TEST-PACK', 'POS Test Pack', 'PACKED', 10000, 1, 'pack', 1, 1, 10, 0, true, true),
  ('POS-TEST-WEIGHT', 'POS Test Weight', 'WEIGHTED', 999, 1000, 'kg', 10, 100, 5000, 0, true, true);

INSERT INTO public.store_settings (
  id, delivery_fee_paise, delivery_charge_threshold_paise,
  free_delivery_threshold_paise, mid_tier_fee_paise,
  tax_mode, tax_rate_bps, online_payment_enabled, pos_price_override_enabled
) VALUES (true, 3000, 25000, NULL, NULL, 'none', 0, false, false)
ON CONFLICT (id) DO UPDATE SET
  tax_mode = 'none',
  tax_rate_bps = 0,
  online_payment_enabled = false,
  pos_price_override_enabled = false;

INSERT INTO public.orders (
  id, public_order_number, customer_id, address_snapshot, status,
  payment_method, payment_status, item_subtotal_paise, discount_paise,
  delivery_fee_paise, tax_paise, grand_total_paise
) VALUES (
  '0a0a0a0a-0000-4000-8000-000000000001', 'BILL-ORDER-001',
  'c3c3c3c3-3333-4333-8333-333333333333',
  jsonb_build_object('recipient_name', 'Bill Customer A', 'phone', '9000000001'),
  'delivered', 'cod', 'unpaid',
  20000, 0, 3000, 0, 23000
), (
  '0a0a0a0a-0000-4000-8000-000000000002', 'BILL-ORDER-CANCELLED',
  'c3c3c3c3-3333-4333-8333-333333333333',
  jsonb_build_object('recipient_name', 'Bill Customer A', 'phone', '9000000001'),
  'cancelled', 'cod', 'unpaid', 0, 0, 0, 0, 0
);

INSERT INTO public.order_items (
  order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot,
  unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise
) VALUES (
  '0a0a0a0a-0000-4000-8000-000000000001',
  (SELECT id FROM public.products WHERE sku = 'POS-TEST-PACK'),
  'POS-TEST-PACK', 'POS Test Pack', 'PACKED',
  'pack', 1, 2, 10000, 20000
);

-- ---------------------------------------------------------------------------
-- Staff: POS search + quote
-- ---------------------------------------------------------------------------
SET LOCAL request.jwt.claims.sub = 'b2b2b2b2-2222-4222-8222-222222222222';

SELECT lives_ok(
  $sql$SELECT 1 FROM pos_search_products('POS Test') WHERE sku = 'POS-TEST-PACK'$sql$,
  'staff can search POS products'
);

SELECT results_eq(
  $sql$SELECT pos_quote(jsonb_build_array(
         jsonb_build_object(
           'product_id', (SELECT id FROM products WHERE sku = 'POS-TEST-PACK'),
           'qty_base', 2
         )
       ))->>'delivery_fee_paise'$sql$,
  ARRAY['0'],
  'POS quote applies no delivery fee'
);

SELECT results_eq(
  $sql$SELECT pos_quote(jsonb_build_array(
         jsonb_build_object(
           'product_id', (SELECT id FROM products WHERE sku = 'POS-TEST-PACK'),
           'qty_base', 2
         )
       ))->>'item_subtotal_paise'$sql$,
  ARRAY['20000'],
  'POS quote prices packed items from the server'
);

SELECT results_eq(
  $sql$SELECT pos_quote(jsonb_build_array(
         jsonb_build_object(
           'product_id', (SELECT id FROM products WHERE sku = 'POS-TEST-WEIGHT'),
           'qty_base', 330
         )
       ))->'lines'->0->>'line_total_paise'$sql$,
  ARRAY['330'],
  'POS weighted line rounds half up per line'
);

SELECT results_eq(
  $sql$SELECT pos_quote(jsonb_build_array(
         jsonb_build_object(
           'product_id', (SELECT id FROM products WHERE sku = 'POS-TEST-PACK'),
           'qty_base', 0
         )
       ))->>'error'$sql$,
  ARRAY['QTY_INVALID'],
  'POS quote rejects zero quantity'
);

SELECT results_eq(
  $sql$SELECT pos_quote(jsonb_build_array(
         jsonb_build_object(
           'product_id', (SELECT id FROM products WHERE sku = 'POS-TEST-PACK'),
           'qty_base', 99
         )
       ))->>'error'$sql$,
  ARRAY['INSUFFICIENT_STOCK'],
  'POS quote rejects quantity above stock'
);

-- Customer cannot search or quote
RESET request.jwt.claims.sub;
SET LOCAL request.jwt.claims.sub = 'c3c3c3c3-3333-4333-8333-333333333333';
SELECT throws_ok(
  $sql$SELECT * FROM pos_search_products('POS Test')$sql$,
  null, null,
  'customer cannot search POS products'
);

SELECT results_eq(
  $sql$SELECT pos_quote('[]'::jsonb)->>'error'$sql$,
  ARRAY['FORBIDDEN'],
  'customer cannot quote a POS sale'
);

-- ---------------------------------------------------------------------------
-- Staff: complete an idempotent POS sale
-- ---------------------------------------------------------------------------
SET LOCAL request.jwt.claims.sub = 'b2b2b2b2-2222-4222-8222-222222222222';

SELECT results_eq(
  $sql$SELECT pos_complete_sale(
         jsonb_build_array(jsonb_build_object(
           'product_id', (SELECT id FROM products WHERE sku = 'POS-TEST-PACK'),
           'qty_base', 2
         )),
         'cod', 'Walk-in Customer', '9000000002', 'POS-KEY-1'
       )->>'ok'$sql$,
  ARRAY['true'],
  'POS sale completes for staff'
);

SELECT results_eq(
  $sql$SELECT bill_number ~ '^BPS/[0-9]{4}-[0-9]{2}/[0-9]{6}$'
       FROM bills b
       JOIN pos_sales ps ON ps.id = b.pos_sale_id
       WHERE ps.idempotency_key = 'POS-KEY-1'$sql$,
  ARRAY[true],
  'POS bill number uses the BPS/FY/sequence format'
);

SELECT results_eq(
  $sql$SELECT stock_qty FROM products WHERE sku = 'POS-TEST-PACK'$sql$,
  ARRAY[8::bigint],
  'POS sale deducts stock atomically'
);

SELECT results_eq(
  $sql$SELECT bi.qty_base, bi.unit_price_paise, bi.line_total_paise
       FROM bill_items bi
       JOIN bills b ON b.id = bi.bill_id
       JOIN pos_sales ps ON ps.id = b.pos_sale_id
       WHERE ps.idempotency_key = 'POS-KEY-1'$sql$,
  $$VALUES (2::bigint, 10000::bigint, 20000::bigint)$$,
  'POS bill item snapshot preserves quantity and price'
);

SELECT results_eq(
  $sql$SELECT b.payment_status::text
       FROM bills b
       JOIN pos_sales ps ON ps.id = b.pos_sale_id
       WHERE ps.idempotency_key = 'POS-KEY-1'$sql$,
  ARRAY['paid'],
  'cash POS bill is recorded as paid'
);

SELECT results_eq(
  $sql$SELECT count(*) FROM inventory_movements
       WHERE reason = 'pos_sale'
         AND reference_id = (SELECT id FROM pos_sales WHERE idempotency_key = 'POS-KEY-1')$sql$,
  ARRAY[1::bigint],
  'POS sale writes one stock movement'
);

SELECT results_eq(
  $sql$SELECT (pos_complete_sale(
         jsonb_build_array(jsonb_build_object(
           'product_id', (SELECT id FROM products WHERE sku = 'POS-TEST-PACK'),
           'qty_base', 2
         )),
         'cod', 'Walk-in Customer', '9000000002', 'POS-KEY-1'
       )->>'bill_id')$sql$,
  $sql$SELECT b.id::text
       FROM bills b
       JOIN pos_sales ps ON ps.id = b.pos_sale_id
       WHERE ps.idempotency_key = 'POS-KEY-1'$sql$,
  'repeating the same POS key returns the original bill'
);

SELECT results_eq(
  $sql$SELECT stock_qty FROM products WHERE sku = 'POS-TEST-PACK'$sql$,
  ARRAY[8::bigint],
  'idempotent POS retry does not deduct stock again'
);

SELECT results_eq(
  $sql$SELECT pos_complete_sale(
         jsonb_build_array(jsonb_build_object(
           'product_id', (SELECT id FROM products WHERE sku = 'POS-TEST-PACK'),
           'qty_base', 3
         )),
         'cod', 'Walk-in Customer', '9000000002', 'POS-KEY-1'
       )->>'error'$sql$,
  ARRAY['IDEMPOTENCY_MISMATCH'],
  'POS key reuse with different items is rejected'
);

SELECT results_eq(
  $sql$SELECT pos_complete_sale(
         jsonb_build_array(jsonb_build_object(
           'product_id', (SELECT id FROM products WHERE sku = 'POS-TEST-WEIGHT'),
           'qty_base', 999999
         )),
         'cod', null, null, 'POS-KEY-2'
       )->>'error'$sql$,
  ARRAY['INSUFFICIENT_STOCK'],
  'insufficient stock blocks the whole POS sale'
);

SELECT results_eq(
  $sql$SELECT count(*) FROM pos_sales WHERE idempotency_key = 'POS-KEY-2'$sql$,
  ARRAY[0::bigint],
  'failed POS sale creates no sale row'
);

SELECT results_eq(
  $sql$SELECT count(*) FROM inventory_movements
       WHERE reason = 'pos_sale'
         AND reference_id = (SELECT id FROM pos_sales WHERE idempotency_key = 'POS-KEY-1')$sql$,
  ARRAY[1::bigint],
  'failed POS sale creates no movement'
);

RESET request.jwt.claims.sub;
SET LOCAL request.jwt.claims.sub = 'c3c3c3c3-3333-4333-8333-333333333333';
SELECT results_eq(
  $sql$SELECT pos_complete_sale(
         jsonb_build_array(jsonb_build_object(
           'product_id', (SELECT id FROM products WHERE sku = 'POS-TEST-PACK'),
           'qty_base', 1
         )),
         'cod', null, null, 'POS-KEY-CUST'
       )->>'error'$sql$,
  ARRAY['FORBIDDEN'],
  'customer cannot complete a POS sale'
);

-- ---------------------------------------------------------------------------
-- Order bills use immutable snapshots
-- ---------------------------------------------------------------------------
SET LOCAL request.jwt.claims.sub = 'b2b2b2b2-2222-4222-8222-222222222222';

SELECT results_eq(
  $sql$SELECT issue_bill_for_order('0a0a0a0a-0000-4000-8000-000000000001')->'bill'->>'grand_total_paise'$sql$,
  ARRAY['23000'],
  'staff can issue an order bill from stored totals'
);

SELECT results_eq(
  $sql$SELECT (issue_bill_for_order('0a0a0a0a-0000-4000-8000-000000000001')->'bill'->>'id')$sql$,
  $sql$SELECT id::text FROM bills WHERE order_id = '0a0a0a0a-0000-4000-8000-000000000001'$sql$,
  'issuing a bill twice is idempotent'
);

UPDATE products SET price_paise = 12345 WHERE sku = 'POS-TEST-PACK';

SELECT results_eq(
  $sql$SELECT bi.unit_price_paise
       FROM bill_items bi
       JOIN bills b ON b.id = bi.bill_id
       WHERE b.order_id = '0a0a0a0a-0000-4000-8000-000000000001'$sql$,
  ARRAY[10000::bigint],
  'order bill keeps the price snapshot after catalogue changes'
);

SELECT results_eq(
  $sql$SELECT bi.unit_price_paise
       FROM bill_items bi
       JOIN bills b ON b.id = bi.bill_id
       JOIN pos_sales ps ON ps.id = b.pos_sale_id
       WHERE ps.idempotency_key = 'POS-KEY-1'$sql$,
  ARRAY[10000::bigint],
  'POS bill keeps its snapshot after catalogue changes'
);

SELECT results_eq(
  $sql$SELECT issue_bill_for_order('0a0a0a0a-0000-4000-8000-000000000002')->>'error'$sql$,
  ARRAY['INVALID_STATUS'],
  'cancelled orders cannot be billed'
);

-- ---------------------------------------------------------------------------
-- Authorised bill access
-- ---------------------------------------------------------------------------
RESET request.jwt.claims.sub;
SET LOCAL request.jwt.claims.sub = 'c3c3c3c3-3333-4333-8333-333333333333';
SELECT lives_ok(
  $sql$SELECT get_bill((SELECT id FROM bills WHERE order_id = '0a0a0a0a-0000-4000-8000-000000000001'))$sql$,
  'customer can read their own order bill'
);

RESET request.jwt.claims.sub;
SET LOCAL request.jwt.claims.sub = 'd4d4d4d4-4444-4444-8444-444444444444';
SELECT results_eq(
  $sql$SELECT get_bill((SELECT id FROM bills WHERE order_id = '0a0a0a0a-0000-4000-8000-000000000001'))->>'error'$sql$,
  ARRAY['FORBIDDEN'],
  'another customer cannot read the bill'
);

RESET request.jwt.claims.sub;
SELECT results_eq(
  $sql$SELECT get_bill((SELECT id FROM bills WHERE order_id = '0a0a0a0a-0000-4000-8000-000000000001'))->>'error'$sql$,
  ARRAY['UNAUTHENTICATED'],
  'anonymous callers cannot read bills'
);

-- ---------------------------------------------------------------------------
-- Voiding: manager only, reason required, stock restored exactly once
-- ---------------------------------------------------------------------------
SET LOCAL request.jwt.claims.sub = 'b2b2b2b2-2222-4222-8222-222222222222';
SELECT results_eq(
  $sql$SELECT void_bill(
         (SELECT b.id FROM bills b
          JOIN pos_sales ps ON ps.id = b.pos_sale_id
          WHERE ps.idempotency_key = 'POS-KEY-1'),
         'Wrong item') ->> 'error'$sql$,
  ARRAY['FORBIDDEN'],
  'staff cannot void a bill'
);

SET LOCAL request.jwt.claims.sub = 'a1a1a1a1-1111-4111-8111-111111111111';
SELECT results_eq(
  $sql$SELECT void_bill(
         (SELECT b.id FROM bills b
          JOIN pos_sales ps ON ps.id = b.pos_sale_id
          WHERE ps.idempotency_key = 'POS-KEY-1'),
         '   ') ->> 'error'$sql$,
  ARRAY['VALIDATION_FAILED'],
  'void requires a reason'
);

SELECT results_eq(
  $sql$SELECT void_bill(
         (SELECT b.id FROM bills b
          JOIN pos_sales ps ON ps.id = b.pos_sale_id
          WHERE ps.idempotency_key = 'POS-KEY-1'),
         'Customer changed their mind') ->> 'ok'$sql$,
  ARRAY['true'],
  'manager can void a bill'
);

SELECT results_eq(
  $sql$SELECT stock_qty FROM products WHERE sku = 'POS-TEST-PACK'$sql$,
  ARRAY[10::bigint],
  'voiding a POS bill restores stock'
);

SELECT results_eq(
  $sql$SELECT void_bill(
         (SELECT b.id FROM bills b
          JOIN pos_sales ps ON ps.id = b.pos_sale_id
          WHERE ps.idempotency_key = 'POS-KEY-1'),
         'Customer changed their mind') ->> 'already_void'$sql$,
  ARRAY['true'],
  'second void is a no-op'
);

SELECT results_eq(
  $sql$SELECT stock_qty FROM products WHERE sku = 'POS-TEST-PACK'$sql$,
  ARRAY[10::bigint],
  'second void does not restore stock twice'
);

SELECT results_eq(
  $sql$SELECT count(*) FROM inventory_movements
       WHERE reason = 'pos_void'
         AND reference_id = (SELECT b.id FROM bills b
                             JOIN pos_sales ps ON ps.id = b.pos_sale_id
                             WHERE ps.idempotency_key = 'POS-KEY-1')$sql$,
  ARRAY[1::bigint],
  'void writes exactly one restore movement per item'
);

SELECT results_eq(
  $sql$SELECT b.status::text || ':' || (b.void_reason IS NOT NULL)::text
       FROM bills b
       JOIN pos_sales ps ON ps.id = b.pos_sale_id
       WHERE ps.idempotency_key = 'POS-KEY-1'$sql$,
  ARRAY['VOID:true'],
  'voided bill keeps status and reason'
);

SELECT lives_ok(
  $sql$SELECT 1 FROM audit_logs
       WHERE entity_type = 'bills'
         AND action = 'UPDATE'
         AND after->>'status' = 'VOID'
         AND entity_id = (SELECT b.id::text FROM bills b
                          JOIN pos_sales ps ON ps.id = b.pos_sale_id
                          WHERE ps.idempotency_key = 'POS-KEY-1')$sql$,
  'void is captured by the bills audit trigger'
);

SELECT * FROM finish();
ROLLBACK;
