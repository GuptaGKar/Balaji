-- pgTAP test: Free delivery zero-fee (no discount) and quote_cart without coupon code
BEGIN;
SELECT plan(7);

INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'f1f1f1f1-1111-4111-8111-111111111111',
        'authenticated', 'authenticated', 'updates-manager@test.invalid', 'x', now(), now(), now()),
       ('00000000-0000-0000-0000-000000000000',
        'f2f2f2f2-2222-4222-8222-222222222222',
        'authenticated', 'authenticated', 'updates-customer@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('f1f1f1f1-1111-4111-8111-111111111111', 'manager', 'Updates Manager'),
       ('f2f2f2f2-2222-4222-8222-222222222222', 'customer', 'Updates Customer');

-- Create test product
INSERT INTO public.products (
  id, sku, name, quantity_type, price_paise, price_basis_qty, unit_label, step_qty, min_qty, stock_qty, active, available
) VALUES (
  '33333333-3333-3333-3333-333333333333', 'DELIV-TEST-1', 'Delivery Test Product',
  'PACKED', 20000, 1, 'pack', 1, 1, 100, true, true
);

-- Ensure store settings have standard delivery fee
UPDATE public.store_settings
SET delivery_fee_paise = 3000,
    delivery_charge_threshold_paise = 50000,
    free_delivery_threshold_paise = 100000;

-- Create free delivery coupon
INSERT INTO public.coupons (code, description, discount_type, discount_value, min_order_paise)
VALUES ('TESTFREEDEL', 'Free Delivery Promo', 'free_delivery', 0, 10000);

-- Test 1: quote_cart WITHOUT coupon code calculates bill properly
SELECT is(
  (
    SELECT (public.quote_cart(
      '[{"product_id": "33333333-3333-3333-3333-333333333333", "qty_base": 1}]'::jsonb
    )->>'grand_total_paise')::bigint
  ),
  23000::bigint,
  'quote_cart without coupon code calculates bill cleanly (subtotal 20000 + delivery 3000 = 23000)'
);

-- Test 2: quote_cart WITH free_delivery coupon applies coupon
SELECT ok(
  (
    SELECT (public.quote_cart(
      '[{"product_id": "33333333-3333-3333-3333-333333333333", "qty_base": 1}]'::jsonb,
      'TESTFREEDEL'
    )->>'coupon_applied')::boolean
  ),
  'free_delivery coupon is applied'
);

-- Test 3: delivery_fee_paise is 0 AND discount_paise is 0 (no discount required for free delivery)
SELECT is(
  (
    SELECT (public.quote_cart(
      '[{"product_id": "33333333-3333-3333-3333-333333333333", "qty_base": 1}]'::jsonb,
      'TESTFREEDEL'
    )->>'delivery_fee_paise')::bigint
  ),
  0::bigint,
  'free_delivery coupon makes delivery fee 0 (FREE delivery)'
);

SELECT is(
  (
    SELECT (public.quote_cart(
      '[{"product_id": "33333333-3333-3333-3333-333333333333", "qty_base": 1}]'::jsonb,
      'TESTFREEDEL'
    )->>'discount_paise')::bigint
  ),
  0::bigint,
  'free_delivery coupon requires NO discount (discount_paise is 0)'
);

-- Test 4: Grand total equals item subtotal (20000 paise)
SELECT is(
  (
    SELECT (public.quote_cart(
      '[{"product_id": "33333333-3333-3333-3333-333333333333", "qty_base": 1}]'::jsonb,
      'TESTFREEDEL'
    )->>'grand_total_paise')::bigint
  ),
  20000::bigint,
  'grand total equals item subtotal without double deduction'
);

-- Test 5: Manager can insert app release
SET LOCAL request.jwt.claims = '{"sub": "f1f1f1f1-1111-4111-8111-111111111111", "role": "authenticated"}';
SET LOCAL ROLE authenticated;

SELECT lives_ok(
  $sql$
  INSERT INTO public.app_releases (
    audience, version_code, version_name, apk_url, release_notes, force_update
  ) VALUES (
    'customer', 8, '1.7', 'https://example.com/app-v1.7.apk', 'Added in-app update support', false
  );
  $sql$,
  'manager can create app releases'
);

-- Test 6: get_latest_app_release returns the newly added release
SELECT is(
  (SELECT (public.get_latest_app_release('customer')->>'version_code')::int),
  8,
  'get_latest_app_release returns highest version code'
);

-- Test 7: Customer cannot insert app release
SET LOCAL request.jwt.claims = '{"sub": "f2f2f2f2-2222-4222-8222-222222222222", "role": "authenticated"}';

SELECT throws_ok(
  $sql$
  INSERT INTO public.app_releases (
    audience, version_code, version_name, apk_url
  ) VALUES (
    'customer', 9, '1.8', 'https://example.com/hacked.apk'
  );
  $sql$,
  NULL,
  'customer is blocked from inserting app releases'
);

SELECT * FROM finish();
ROLLBACK;
