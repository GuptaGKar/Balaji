-- pgTAP test: per-PIN delivery pricing and PIN-restricted coupons.
BEGIN;
SELECT plan(6);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'abababab-0000-0000-0000-000000000001',
        'authenticated', 'authenticated', 'pin-test@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('abababab-0000-0000-0000-000000000001', 'customer', 'PIN Test')
ON CONFLICT (id) DO UPDATE SET role = 'customer';

INSERT INTO public.products (
  id, sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty, active, available
) VALUES (
  'acacacac-acac-acac-acac-acacacacac01', 'PIN-TEST-1', 'PIN Test Product',
  'PACKED', 15000, 1, 'pack', 1, 1, 50, true, true
)
ON CONFLICT (sku) DO UPDATE SET active = true, available = true, stock_qty = 50, price_paise = 15000;

UPDATE public.store_settings
SET store_open = true,
    delivery_fee_paise = 3000,
    delivery_charge_threshold_paise = 25000,
    free_delivery_threshold_paise = NULL,
    mid_tier_fee_paise = NULL,
    max_order_paise = NULL,
    tax_mode = 'none',
    tax_rate_bps = 0,
    online_payment_enabled = false;

-- PIN 122001 has its own fee (₹40), charge threshold (₹200) and free-delivery point (₹800).
INSERT INTO public.service_areas (kind, value, active, delivery_fee_paise, delivery_charge_threshold_paise, free_delivery_threshold_paise)
VALUES ('pincode', '122001', true, 4000, 20000, 80000)
ON CONFLICT (kind, lower(value)) DO UPDATE SET
  active = true,
  delivery_fee_paise = EXCLUDED.delivery_fee_paise,
  delivery_charge_threshold_paise = EXCLUDED.delivery_charge_threshold_paise,
  free_delivery_threshold_paise = EXCLUDED.free_delivery_threshold_paise;

INSERT INTO public.coupons (code, description, discount_type, discount_value, min_order_paise, pin_codes)
VALUES ('PINONLY', 'PIN restricted', 'percent_off', 10, 0, ARRAY['122001'])
ON CONFLICT (code) DO UPDATE SET active = true, pin_codes = ARRAY['122001'], times_used = 0;

-- 1. The PIN fee applies below its own charge threshold (15000 < 20000).
SELECT is(
  (SELECT (public.quote_cart(
     jsonb_build_array(jsonb_build_object('product_id', 'acacacac-acac-acac-acac-acacacacac01', 'qty_base', 1)),
     NULL, '122001')->>'delivery_fee_paise')::bigint),
  4000::bigint,
  'PIN fee is used below the PIN charge threshold');

-- 2. The PIN free-delivery point applies (subtotal >= 80000 would be free); at 15000 the
--    quote reports how much is still needed instead of using the store-wide threshold.
SELECT is(
  (SELECT (public.quote_cart(
     jsonb_build_array(jsonb_build_object('product_id', 'acacacac-acac-acac-acac-acacacacac01', 'qty_base', 1)),
     NULL, '122001')->>'amount_to_free_delivery')::bigint),
  65000::bigint,
  'amount_to_free_delivery follows the PIN threshold (80000 - 15000)');

-- 3. An unknown PIN falls back to the store-wide fee (₹30).
SELECT is(
  (SELECT (public.quote_cart(
     jsonb_build_array(jsonb_build_object('product_id', 'acacacac-acac-acac-acac-acacacacac01', 'qty_base', 1)),
     NULL, '999999')->>'delivery_fee_paise')::bigint),
  3000::bigint,
  'unknown PIN falls back to the store-wide fee');

-- 4. The PIN-restricted coupon is rejected for a different PIN.
SELECT is(
  (SELECT public.quote_cart(
     jsonb_build_array(jsonb_build_object('product_id', 'acacacac-acac-acac-acac-acacacacac01', 'qty_base', 1)),
     'PINONLY', '122002')->>'coupon_error'),
  'This coupon is not available for your PIN code.',
  'coupon is rejected outside its PIN list');

-- 5. The same coupon applies for the listed PIN.
SELECT is(
  (SELECT (public.quote_cart(
     jsonb_build_array(jsonb_build_object('product_id', 'acacacac-acac-acac-acac-acacacacac01', 'qty_base', 1)),
     'PINONLY', '122001')->>'coupon_applied')::boolean),
  true,
  'coupon applies for the listed PIN');

-- 6. place_order stores the PIN fee on the order.
SET LOCAL request.jwt.claim.sub = 'abababab-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims = '{"sub":"abababab-0000-0000-0000-000000000001","role":"authenticated"}';
SELECT public.place_order(
  jsonb_build_object(
    'items', jsonb_build_array(
      jsonb_build_object('product_id', 'acacacac-acac-acac-acac-acacacacac01', 'qty_base', 2)
    ),
    'address', jsonb_build_object(
      'recipient_name', 'PIN Test', 'phone', '9999999998',
      'address_lines', '1 PIN Road', 'postal_code', '122001'
    ),
    'payment_method', 'cod'
  ),
  'PIN-ORDER-KEY-1'
);

SELECT is(
  (SELECT delivery_fee_paise FROM public.orders WHERE idempotency_key = 'PIN-ORDER-KEY-1'),
  4000::bigint,
  'place_order stores the PIN delivery fee');

SELECT * FROM finish();
ROLLBACK;
