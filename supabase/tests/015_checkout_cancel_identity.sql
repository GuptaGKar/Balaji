-- pgTAP: new-user identity, customer cancellation, and unavailable-item edits
-- Covers migration 20261014000018_checkout_cancel_order_edit.sql

BEGIN;
SELECT plan(11);

-- 1. New auth users are customers, never managers (D-ROLES, no self-escalation)
INSERT INTO auth.users (
  instance_id, id, aud, role, email, encrypted_password,
  email_confirmed_at, raw_user_meta_data, created_at, updated_at
) VALUES (
  '00000000-0000-0000-0000-000000000000',
  'eeeeeeee-0000-0000-0000-000000000001',
  'authenticated', 'authenticated', 'new-customer@test.invalid', 'x',
  now(), '{"role":"manager"}'::jsonb, now(), now()
);

SELECT results_eq(
  $$SELECT role::text FROM profiles WHERE id = 'eeeeeeee-0000-0000-0000-000000000001'$$,
  $$VALUES ('customer')$$,
  'new auth user is provisioned as customer even with role metadata'
);

-- Fixtures: usable catalogue + settings
UPDATE store_settings SET
  store_open = true,
  online_payment_enabled = false,
  delivery_fee_paise = 3000,
  delivery_charge_threshold_paise = 25000,
  free_delivery_threshold_paise = NULL;

INSERT INTO products (
  sku, name, quantity_type, price_paise, price_basis_qty, unit_label,
  step_qty, min_qty, stock_qty, active, available
) VALUES (
  'CANCEL-TEST-1KG', 'Cancel Test 1 kg', 'PACKED', 10000, 1, 'pack',
  1, 1, 10, true, true
)
ON CONFLICT (sku) DO UPDATE SET stock_qty = 10, active = true, available = true;

-- 2. Customer can place an order
SET LOCAL request.jwt.claims.sub = 'eeeeeeee-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims.role = 'authenticated';
SELECT lives_ok(
  $sql$
    SELECT place_order(
      jsonb_build_object(
        'items', jsonb_build_array(jsonb_build_object(
          'product_id', (SELECT id FROM products WHERE sku = 'CANCEL-TEST-1KG'),
          'qty_base', 2
        )),
        'address', jsonb_build_object(
          'recipient_name', 'Test Customer', 'phone', '9999999999',
          'address_lines', '1 Test Street', 'postal_code', '122001'
        ),
        'payment_method', 'cod'
      ),
      'CANCEL-TEST-IDEMP-1'
    )
  $sql$,
  'customer checkout succeeds'
);

-- 3. Customer cancellation restores stock exactly once
SELECT lives_ok(
  $sql$
    SELECT cancel_my_order(
      (SELECT id FROM orders WHERE idempotency_key = 'CANCEL-TEST-IDEMP-1'),
      'Changed my mind'
    )
  $sql$,
  'customer cancels own pending order'
);

SELECT results_eq(
  $$SELECT status::text, stock_qty FROM orders o, products p
    WHERE o.idempotency_key = 'CANCEL-TEST-IDEMP-1' AND p.sku = 'CANCEL-TEST-1KG'$$,
  $$VALUES ('cancelled', 10::bigint)$$,
  'cancellation restores stock once'
);

SELECT results_eq(
  $$SELECT count(*)::int FROM inventory_movements
    WHERE reference_id = (SELECT id FROM orders WHERE idempotency_key = 'CANCEL-TEST-IDEMP-1')
      AND reason = 'order_cancelled'$$,
  $$VALUES (1)$$,
  'cancellation writes one stock-restore movement'
);

-- 4. Another customer cannot cancel it
SET LOCAL request.jwt.claims.sub = 'aaaaaaaa-0000-0000-0000-000000000002';
SELECT results_eq(
  $$SELECT cancel_my_order(
      (SELECT id FROM orders WHERE idempotency_key = 'CANCEL-TEST-IDEMP-1'),
      'Not mine'
    )->>'error'$$,
  $$VALUES ('FORBIDDEN')$$,
  'customer cannot cancel another customer order'
);

-- 5. Accepted orders cannot be cancelled by the customer
SET LOCAL request.jwt.claims.sub = 'eeeeeeee-0000-0000-0000-000000000001';
SELECT place_order(
  jsonb_build_object(
    'items', jsonb_build_array(jsonb_build_object(
      'product_id', (SELECT id FROM products WHERE sku = 'CANCEL-TEST-1KG'),
      'qty_base', 1
    )),
    'address', jsonb_build_object(
      'recipient_name', 'Test Customer', 'phone', '9999999999',
      'address_lines', '1 Test Street', 'postal_code', '122001'
    ),
    'payment_method', 'cod'
  ),
  'CANCEL-TEST-IDEMP-2'
);

SET LOCAL request.jwt.claims.sub = 'aaaaaaaa-0000-0000-0000-000000000006';
SET LOCAL request.jwt.claims.role = 'authenticated';
SELECT transition_order(
  (SELECT id FROM orders WHERE idempotency_key = 'CANCEL-TEST-IDEMP-2'),
  'accepted', 'Accepted for test'
);

SET LOCAL request.jwt.claims.sub = 'eeeeeeee-0000-0000-0000-000000000001';
SELECT results_eq(
  $$SELECT cancel_my_order(
      (SELECT id FROM orders WHERE idempotency_key = 'CANCEL-TEST-IDEMP-2'),
      'Too late'
    )->>'error'$$,
  $$VALUES ('INVALID_STATUS')$$,
  'customer cannot cancel an accepted order'
);

-- 6. Staff direct order status updates are blocked; RPC still works
SET LOCAL request.jwt.claims.sub = 'aaaaaaaa-0000-0000-0000-000000000005';
SELECT results_eq(
  $$WITH updated AS (
      UPDATE orders SET status = 'cancelled'
      WHERE idempotency_key = 'CANCEL-TEST-IDEMP-2'
      RETURNING 1
    )
    SELECT count(*)::int FROM updated$$,
  $$VALUES (0)$$,
  'staff cannot bypass transition_order with a direct UPDATE'
);

-- 7. Removing the final active item cancels the order and zeroes the bill
SET LOCAL request.jwt.claims.sub = 'aaaaaaaa-0000-0000-0000-000000000006';
SET LOCAL request.jwt.claims.role = 'authenticated';
SELECT results_eq(
  $$SELECT remove_order_item(
      (SELECT id FROM orders WHERE idempotency_key = 'CANCEL-TEST-IDEMP-2'),
      (SELECT id FROM order_items WHERE order_id = (
        SELECT id FROM orders WHERE idempotency_key = 'CANCEL-TEST-IDEMP-2'
      )),
      'Item unavailable'
    )->>'order_cancelled'$$,
  $$VALUES ('true')$$,
  'removing the last item cancels the order'
);

SELECT results_eq(
  $$SELECT status::text, item_subtotal_paise, delivery_fee_paise, tax_paise, grand_total_paise
    FROM orders WHERE idempotency_key = 'CANCEL-TEST-IDEMP-2'$$,
  $$VALUES ('cancelled', 0::bigint, 0::bigint, 0::bigint, 0::bigint)$$,
  'zero-item order is cancelled with a zero bill'
);

-- 8. Idempotency key cannot be reused with a different payload
SELECT results_eq(
  $$SELECT place_order(
      jsonb_build_object(
        'items', jsonb_build_array(jsonb_build_object(
          'product_id', (SELECT id FROM products WHERE sku = 'CANCEL-TEST-1KG'),
          'qty_base', 3
        )),
        'address', jsonb_build_object(
          'recipient_name', 'Test Customer', 'phone', '9999999999',
          'address_lines', '1 Test Street', 'postal_code', '122001'
        ),
        'payment_method', 'cod'
      ),
      'CANCEL-TEST-IDEMP-1'
    )->>'error'$$,
  $$VALUES ('IDEMPOTENCY_MISMATCH')$$,
  'idempotency key reuse with changed payload is rejected'
);

SELECT * FROM finish();
ROLLBACK;
