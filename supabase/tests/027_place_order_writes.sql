-- pgTAP test: place_order writes inventory movements and status events, and its
-- idempotency replay is scoped to the signed-in customer.
BEGIN;
SELECT plan(10);

-- Fixtures: two customers and a product.
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'dddddddd-0000-0000-0000-000000000001',
        'authenticated', 'authenticated', 'place-order-a@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'dddddddd-0000-0000-0000-000000000002',
        'authenticated', 'authenticated', 'place-order-b@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('dddddddd-0000-0000-0000-000000000001', 'customer', 'Place Order A'),
       ('dddddddd-0000-0000-0000-000000000002', 'customer', 'Place Order B')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.products (
  id, sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty, active, available
) VALUES (
  '55555555-5555-5555-5555-555555555555', 'FIX-ORDER-1', 'Fix Order Product',
  'PACKED', 10000, 1, 'pack', 1, 1, 10, true, true
)
ON CONFLICT (sku) DO UPDATE SET stock_qty = 10, active = true, available = true;

-- The order address must be inside a configured service area when any exist.
INSERT INTO public.service_areas (kind, value, active)
VALUES ('pincode', '122001', true), ('pincode', '122002', true)
ON CONFLICT DO NOTHING;

UPDATE public.products SET max_qty = NULL WHERE sku = 'FIX-ORDER-1';

UPDATE public.store_settings
SET store_open = true,
    delivery_fee_paise = 3000,
    delivery_charge_threshold_paise = 25000,
    free_delivery_threshold_paise = NULL,
    max_order_paise = NULL,
    tax_mode = 'none',
    tax_rate_bps = 0,
    online_payment_enabled = false;

INSERT INTO public.coupons (code, description, discount_type, discount_value, min_order_paise)
VALUES ('FIXFREEDEL', 'Free delivery for place_order test', 'free_delivery', 0, 10000)
ON CONFLICT (code) DO UPDATE SET active = true, times_used = 0;

-- auth.uid() reads the singular claim first and the JSON claims blob second; set both
-- so the test matches what PostgREST puts on the connection.
SET LOCAL request.jwt.claim.sub = 'dddddddd-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims = '{"sub":"dddddddd-0000-0000-0000-000000000001","role":"authenticated"}';

-- 1. A plain COD order is accepted.
SELECT lives_ok(
  $sql$
    SELECT public.place_order(
      jsonb_build_object(
        'items', jsonb_build_array(
          jsonb_build_object('product_id', '55555555-5555-5555-5555-555555555555', 'qty_base', 2)
        ),
        'address', jsonb_build_object(
          'recipient_name', 'Place Order A', 'phone', '9999999991',
          'address_lines', '1 Test Road', 'postal_code', '122001'
        ),
        'payment_method', 'cod'
      ),
      'FIX-ORDER-KEY-1'
    )
  $sql$,
  'place_order accepts a COD order');

-- 2. Totals come from quote_cart: 20000 items + 3000 delivery fee.
SELECT is(
  (SELECT grand_total_paise FROM public.orders WHERE idempotency_key = 'FIX-ORDER-KEY-1'),
  23000::bigint,
  'order total is 20000 items + 3000 delivery fee');

-- 3. The order line exists.
SELECT is(
  (SELECT count(*) FROM public.order_items oi
   JOIN public.orders o ON o.id = oi.order_id
   WHERE o.idempotency_key = 'FIX-ORDER-KEY-1'),
  1::bigint,
  'one order line is written');

-- 4. Stock is decremented.
SELECT is(
  (SELECT stock_qty FROM public.products WHERE sku = 'FIX-ORDER-1'),
  8::bigint,
  'stock is decremented by the ordered quantity');

-- 5. A matching inventory movement is written (this is the statement that used to
--    point at the non-existent public.stock_movements table).
SELECT is(
  (SELECT count(*) FROM public.inventory_movements im
   JOIN public.orders o ON o.id = im.reference_id
   WHERE o.idempotency_key = 'FIX-ORDER-KEY-1'
     AND im.reason::text = 'order_placed'),
  1::bigint,
  'an order_placed inventory movement is written');

-- 6. A status event with the acting customer is written (previously referenced the
--    non-existent note/created_by columns).
SELECT is(
  (SELECT count(*) FROM public.order_status_events se
   JOIN public.orders o ON o.id = se.order_id
   WHERE o.idempotency_key = 'FIX-ORDER-KEY-1'
     AND se.to_status::text = 'pending_review'
     AND se.actor_id = 'dddddddd-0000-0000-0000-000000000001'),
  1::bigint,
  'an order_status_event is written for the customer');

-- 7. Replaying the same key returns the same order instead of creating another.
SELECT is(
  (SELECT public.place_order(
     jsonb_build_object(
       'items', jsonb_build_array(
         jsonb_build_object('product_id', '55555555-5555-5555-5555-555555555555', 'qty_base', 2)
       ),
       'address', jsonb_build_object(
         'recipient_name', 'Place Order A', 'phone', '9999999991',
         'address_lines', '1 Test Road', 'postal_code', '122001'
       ),
       'payment_method', 'cod'
     ),
     'FIX-ORDER-KEY-1'
   )->>'order_id'),
  (SELECT id::text FROM public.orders WHERE idempotency_key = 'FIX-ORDER-KEY-1'),
  'idempotent replay returns the original order id');

-- 8. The replay lookup is scoped to the caller: a second customer using the same
--    key gets their own order, not the first customer's order number.
SET LOCAL request.jwt.claim.sub = 'dddddddd-0000-0000-0000-000000000002';
SET LOCAL request.jwt.claims = '{"sub":"dddddddd-0000-0000-0000-000000000002","role":"authenticated"}';
SELECT lives_ok(
  $sql$
    SELECT public.place_order(
      jsonb_build_object(
        'items', jsonb_build_array(
          jsonb_build_object('product_id', '55555555-5555-5555-5555-555555555555', 'qty_base', 1)
        ),
        'address', jsonb_build_object(
          'recipient_name', 'Place Order B', 'phone', '9999999992',
          'address_lines', '2 Test Road', 'postal_code', '122002'
        ),
        'payment_method', 'cod'
      ),
      'FIX-ORDER-KEY-1'
    )
  $sql$,
  'another customer can use the same key for their own order');

SELECT is(
  (SELECT count(*) FROM public.orders WHERE idempotency_key = 'FIX-ORDER-KEY-1'),
  2::bigint,
  'the key is scoped per customer, not global');

-- 9. A free-delivery coupon waives the fee and nothing else.
SET LOCAL request.jwt.claim.sub = 'dddddddd-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims = '{"sub":"dddddddd-0000-0000-0000-000000000001","role":"authenticated"}';
SELECT is(
  (SELECT public.place_order(
     jsonb_build_object(
       'items', jsonb_build_array(
         jsonb_build_object('product_id', '55555555-5555-5555-5555-555555555555', 'qty_base', 2)
       ),
       'address', jsonb_build_object(
         'recipient_name', 'Place Order A', 'phone', '9999999991',
         'address_lines', '1 Test Road', 'postal_code', '122001'
       ),
       'payment_method', 'cod',
       'coupon_code', 'FIXFREEDEL'
     ),
     'FIX-ORDER-KEY-2'
   )->>'grand_total_paise')::bigint,
  20000::bigint,
  'free-delivery coupon removes the fee but does not discount the items');

-- 10. The coupon is recorded on the order and its usage counter is incremented.
SELECT is(
  (SELECT o.coupon_code || '|' || c.times_used::text
   FROM public.orders o
   JOIN public.coupons c ON c.id = o.coupon_id
   WHERE o.idempotency_key = 'FIX-ORDER-KEY-2'),
  'FIXFREEDEL|1',
  'the applied coupon is stored and counted');

SELECT * FROM finish();
ROLLBACK;
