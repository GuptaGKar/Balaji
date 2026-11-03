-- pgTAP test: free_item coupons — a milestone unlocks ONE gift chosen from a
-- manager-configured pool. The gift is a ₹0 line, never touches the subtotal,
-- the delivery milestone or the grand total, and it does leave the shelf.
BEGIN;
SELECT plan(25);

-- ─── Fixtures ────────────────────────────────────────────────────────────────
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000',
        'ffffffff-0000-0000-0000-000000000001',
        'authenticated', 'authenticated', 'free-item-buyer@test.invalid', 'x', now(), now(), now());

INSERT INTO public.profiles (id, role, display_name)
VALUES ('ffffffff-0000-0000-0000-000000000001', 'customer', 'Free Item Buyer')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.products (
  id, sku, name, quantity_type, price_paise, price_basis_qty, unit_label,
  step_qty, min_qty, max_qty, stock_qty, active, available
) VALUES
  -- The paid basket item (₹100 a pack).
  ('11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01', 'FI-PAID',  'Free Item Paid Product',
   'PACKED', 10000, 1, 'pack', 1, 1, NULL, 50, true,  true),
  -- Gift A: claimable.
  ('11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02', 'FI-GIFT-A', 'Free Item Gift A',
   'PACKED',  5000, 1, 'pack', 1, 1, NULL, 20, true,  true),
  -- Gift B: in the pool but out of stock.
  ('11111111-aaaa-4aaa-8aaa-aaaaaaaaaa03', 'FI-GIFT-B', 'Free Item Gift B',
   'PACKED',  4000, 1, 'pack', 1, 1, NULL,  0, true,  true),
  -- Gift C: in the pool but inactive.
  ('11111111-aaaa-4aaa-8aaa-aaaaaaaaaa04', 'FI-GIFT-C', 'Free Item Gift C',
   'PACKED',  3000, 1, 'pack', 1, 1, NULL, 10, false, true),
  -- Never part of the pool.
  ('11111111-aaaa-4aaa-8aaa-aaaaaaaaaa05', 'FI-OUTSIDE', 'Free Item Outside Pool',
   'PACKED',  2000, 1, 'pack', 1, 1, NULL, 10, true,  true)
ON CONFLICT (sku) DO UPDATE SET
  stock_qty = EXCLUDED.stock_qty, active = EXCLUDED.active,
  available = EXCLUDED.available, price_paise = EXCLUDED.price_paise;

INSERT INTO public.service_areas (kind, value, active)
VALUES ('pincode', '122001', true)
ON CONFLICT (kind, lower(value)) DO UPDATE SET active = true;

-- The PIN must fall back to the store-wide fee, otherwise a pre-existing
-- per-PIN override would move the delivery line and the assertions with it.
UPDATE public.service_areas
SET delivery_fee_paise = NULL,
    delivery_charge_threshold_paise = NULL,
    free_delivery_threshold_paise = NULL
WHERE kind = 'pincode' AND lower(value) = '122001';

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

INSERT INTO public.coupons (
  code, description, discount_type, discount_value, min_order_paise,
  free_item_product_ids, free_item_qty, active
) VALUES (
  'FREEGIFT', 'One free gift over ₹200', 'free_item', 0, 20000,
  ARRAY[
    '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'::uuid,
    '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa03'::uuid,
    '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa04'::uuid
  ],
  1, true
)
ON CONFLICT (code) DO UPDATE SET
  active = true, times_used = 0, min_order_paise = 20000,
  free_item_product_ids = EXCLUDED.free_item_product_ids,
  free_item_qty = EXCLUDED.free_item_qty;

-- Two packs = ₹200, exactly the milestone. Fee ₹30 (below ₹250 threshold).
-- 1 ─ below the milestone the coupon is refused
SELECT is(
  ((SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',1)),
      'FREEGIFT'))->>'coupon_applied')::boolean,
  false,
  'FI-1: below the milestone the coupon does not apply');

-- 2
SELECT ok(
  (SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',1)),
      'FREEGIFT'))->>'coupon_error' LIKE 'Minimum order%',
  'FI-2: below the milestone the error names the minimum order');

-- 3 ─ milestone reached, nothing picked yet
SELECT is(
  ((SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
      'FREEGIFT'))->>'coupon_applied')::boolean,
  true,
  'FI-3: at the milestone the coupon applies');

-- 4
SELECT is(
  ((SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
      'FREEGIFT'))->>'free_item_selection_required')::boolean,
  true,
  'FI-4: the customer is asked to choose a gift');

-- 5 ─ the whole pool is advertised
SELECT is(
  jsonb_array_length(
    ((SELECT public.quote_cart(
        jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
        'FREEGIFT'))->'free_item_options')::jsonb),
  3,
  'FI-5: all three pool products are returned for the chooser');

-- 6/7/8 ─ claimability per product
SELECT is(
  (SELECT (opt->>'claimable')::boolean
     FROM jsonb_array_elements(
       ((SELECT public.quote_cart(
           jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
           'FREEGIFT'))->'free_item_options')::jsonb) opt
    WHERE opt->>'product_id' = '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'),
  true,
  'FI-6: an in-stock active gift is claimable');

SELECT is(
  (SELECT (opt->>'claimable')::boolean
     FROM jsonb_array_elements(
       ((SELECT public.quote_cart(
           jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
           'FREEGIFT'))->'free_item_options')::jsonb) opt
    WHERE opt->>'product_id' = '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa03'),
  false,
  'FI-7: an out-of-stock gift is not claimable');

SELECT is(
  (SELECT (opt->>'claimable')::boolean
     FROM jsonb_array_elements(
       ((SELECT public.quote_cart(
           jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
           'FREEGIFT'))->'free_item_options')::jsonb) opt
    WHERE opt->>'product_id' = '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa04'),
  false,
  'FI-8: an inactive gift is not claimable');

-- 9..15 ─ a valid selection adds exactly one ₹0 line and changes no totals
SELECT is(
  ((SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
      'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'))->>'free_item_error')::text,
  NULL,
  'FI-9: a valid gift produces no error');

SELECT is(
  (SELECT count(*)::int
     FROM jsonb_array_elements(
       ((SELECT public.quote_cart(
           jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
           'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'))->'lines')::jsonb) l
    WHERE (l->>'is_free_item')::boolean),
  1,
  'FI-10: exactly one gift line is appended');

SELECT is(
  (SELECT (l->>'line_total_paise')::bigint
     FROM jsonb_array_elements(
       ((SELECT public.quote_cart(
           jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
           'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'))->'lines')::jsonb) l
    WHERE (l->>'is_free_item')::boolean),
  0::bigint,
  'FI-11: the gift line totals 0 paise');

SELECT is(
  ((SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
      'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'))->>'item_subtotal_paise')::bigint,
  20000::bigint,
  'FI-12: the gift is excluded from the item subtotal');

SELECT is(
  ((SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
      'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'))->>'grand_total_paise')::bigint,
  23000::bigint,
  'FI-13: grand total is subtotal + delivery only (₹200 + ₹30)');

SELECT is(
  ((SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
      'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'))->>'free_item_selected_id')::text,
  '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02',
  'FI-14: the chosen gift is echoed back');

SELECT is(
  ((SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
      'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'))->>'free_item_selection_required')::boolean,
  false,
  'FI-15: once a valid gift is picked nothing more is required');

-- 16/17 ─ a product outside the pool is refused and adds no line
SELECT ok(
  (SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
      'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa05'))->>'free_item_error' IS NOT NULL,
  'FI-16: a product outside the coupon pool cannot be claimed');

SELECT is(
  (SELECT count(*)::int
     FROM jsonb_array_elements(
       ((SELECT public.quote_cart(
           jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
           'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa05'))->'lines')::jsonb) l
    WHERE (l->>'is_free_item')::boolean),
  0,
  'FI-17: a refused gift adds no line');

-- 18 ─ out-of-stock gift fails with the customer-facing wording
SELECT is(
  (SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
      'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa03'))->>'free_item_error',
  'This coupon''s free item is currently unavailable.',
  'FI-18: an out-of-stock gift reports it is unavailable');

-- 19 ─ inactive gift also refused
SELECT ok(
  (SELECT public.quote_cart(
      jsonb_build_array(jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2)),
      'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa04'))->>'free_item_error' IS NOT NULL,
  'FI-19: an inactive gift is refused');

-- 20 ─ no stacking: a gift already bought in the cart cannot also be free
SELECT ok(
  (SELECT public.quote_cart(
      jsonb_build_array(
        jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01','qty_base',2),
        jsonb_build_object('product_id','11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02','qty_base',1)
      ),
      'FREEGIFT', NULL, '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'))->>'free_item_error'
    LIKE '%already in your cart%',
  'FI-20: a cart item cannot also be claimed free');

-- ─── place_order ─────────────────────────────────────────────────────────────
SET LOCAL request.jwt.claim.sub = 'ffffffff-0000-0000-0000-000000000001';
SET LOCAL request.jwt.claims = '{"sub":"ffffffff-0000-0000-0000-000000000001","role":"authenticated"}';

-- 21 ─ the order goes through with the gift
SELECT lives_ok(
  $sql$
    SELECT public.place_order(
      jsonb_build_object(
        'items', jsonb_build_array(
          jsonb_build_object('product_id', '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01', 'qty_base', 2)
        ),
        'address', jsonb_build_object(
          'recipient_name', 'Free Item Buyer', 'phone', '9999999999',
          'address_lines', '1 Gift Road', 'postal_code', '122001'
        ),
        'payment_method', 'cod',
        'coupon_code', 'FREEGIFT',
        'free_item_product_id', '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa02'
      ),
      'FI-ORDER-1'
    )
  $sql$,
  'FI-21: place_order accepts the order with its gift');

-- 22 ─ the gift line is stored as a ₹0 free line
SELECT is(
  (SELECT count(*)::int FROM public.order_items oi
     JOIN public.orders o ON o.id = oi.order_id
    WHERE o.idempotency_key = 'FI-ORDER-1'
      AND oi.is_free_item AND oi.unit_price_paise = 0 AND oi.line_total_paise = 0),
  1,
  'FI-22: the gift is persisted as one ₹0 free line');

-- 23 ─ totals charged to the customer ignore the gift
SELECT is(
  (SELECT grand_total_paise FROM public.orders WHERE idempotency_key = 'FI-ORDER-1'),
  23000::bigint,
  'FI-23: the order total is ₹200 of goods plus ₹30 delivery');

-- 24 ─ the gift still leaves the shelf
SELECT is(
  (SELECT stock_qty FROM public.products WHERE sku = 'FI-GIFT-A'),
  19::bigint,
  'FI-24: the gifted pack is decremented from stock');

-- 25 ─ ordering without naming a gift is rejected
SELECT is(
  ((SELECT public.place_order(
      jsonb_build_object(
        'items', jsonb_build_array(
          jsonb_build_object('product_id', '11111111-aaaa-4aaa-8aaa-aaaaaaaaaa01', 'qty_base', 2)
        ),
        'address', jsonb_build_object(
          'recipient_name', 'Free Item Buyer', 'phone', '9999999999',
          'address_lines', '1 Gift Road', 'postal_code', '122001'
        ),
        'payment_method', 'cod',
        'coupon_code', 'FREEGIFT'
      ),
      'FI-ORDER-2'
    ))->>'error')::text,
  'FREE_ITEM_REQUIRED',
  'FI-25: a free-item coupon cannot be redeemed without choosing a gift');

SELECT * FROM finish();
ROLLBACK;
