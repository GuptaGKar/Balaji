-- pgTAP 041: delivery-zone geometry, zone-selected pricing and the pre-order preview.
-- Contract under test (docs/DECISIONS.md, 2026-11-02): the PIN code decides whether an
-- order is accepted; a boundary only decides which configured price applies, with
-- precedence PIN row -> covering zone row -> store settings.
-- Requires PostGIS installed in the "extensions" schema (migration 20261102000009).

BEGIN;
SELECT plan(41);

-- ─── Fixtures ────────────────────────────────────────────────────────────────
DELETE FROM public.service_areas;

UPDATE public.store_settings
SET store_open = true,
    delivery_fee_paise = 3000,
    delivery_charge_threshold_paise = 25000,
    free_delivery_threshold_paise = 50000,
    mid_tier_fee_paise = NULL,
    tax_mode = 'none',
    tax_rate_bps = 0,
    online_payment_enabled = false;

-- Two PIN areas: one plain, one with its own fee override. Zone geometry is attached
-- separately below so the precedence can be observed.
INSERT INTO public.service_areas (kind, value, active, delivery_fee_paise)
VALUES ('pincode', '900001', true, NULL),      -- no override -> may take the zone price
       ('pincode', '900002', true, 1500);      -- own override -> must beat the zone price

-- The two overlapping boundaries: Z1 (priority 10, fee 700) inside Z2 (priority 20, fee 900).
INSERT INTO public.service_areas (kind, value, name, active, priority, delivery_fee_paise, boundary)
VALUES
  ('neighbourhood', 'Z1-INNER', 'Inner belt', true, 10, 700,
   extensions.ST_GeomFromText(
     'POLYGON((77.50 12.90, 77.70 12.90, 77.70 13.05, 77.50 13.05, 77.50 12.90))', 4326)),
  ('neighbourhood', 'Z2-OUTER', 'Outer belt', true, 20, 900,
   extensions.ST_GeomFromText(
     'POLYGON((77.40 12.80, 77.80 12.80, 77.80 13.20, 77.40 13.20, 77.40 12.80))', 4326));

-- A point inside both boundaries, one inside neither.
--   inside  = 12.97 N, 77.59 E     outside = 13.50 N, 78.50 E

-- ─── The boundary column really constrains what can be stored ────────────────
SELECT is(
  (SELECT convalidated FROM pg_constraint WHERE conname = 'service_areas_boundary_is_closed_polygon'),
  true, 'Z-37 the boundary CHECK exists and is validated, not just declared');

SELECT throws_ok($q$INSERT INTO public.service_areas (kind, value, boundary)
  VALUES ('pincode', '900099',
    extensions.ST_GeomFromText('POLYGON((77 12, 78 12, 78 13, 77 13, 77 12))', 3857))$q$,
  NULL, NULL, 'Z-38 a boundary in the wrong SRID cannot be stored');

SELECT throws_ok($q$INSERT INTO public.service_areas (kind, value, boundary)
  VALUES ('pincode', '900098',
    extensions.ST_GeomFromText('POINT(77.5 12.9)', 4326))$q$,
  NULL, NULL, 'Z-39 a non-polygon geometry cannot be stored as a boundary');

SELECT lives_ok($q$INSERT INTO public.service_areas (kind, value, boundary)
  VALUES ('pincode', '900097',
    extensions.ST_GeomFromText('POLYGON((77.0 12.0, 77.1 12.0, 77.1 12.1, 77.0 12.1, 77.0 12.0))', 4326))$q$,
  'Z-40 a valid closed WGS84 polygon is accepted');

-- ─── Signatures: old ones dropped, new ones present ──────────────────────────
SELECT has_function('public', 'resolve_delivery_zone',
  array['double precision','double precision'], 'Z-1 resolve_delivery_zone exists');
SELECT has_function('public', 'compute_delivery_fee_for_pin',
  array['bigint','text','double precision','double precision'], 'Z-2 fee fn takes coordinates');
SELECT hasnt_function('public', 'compute_delivery_fee_for_pin', array['bigint','text'],
  'Z-3 old two-argument fee signature is dropped, so PostgREST sees one candidate');
SELECT has_function('public', 'quote_cart',
  array['jsonb','text','text','uuid','double precision','double precision'], 'Z-4 quote_cart exists');
SELECT hasnt_function('public', 'quote_cart', array['jsonb','text','text','uuid'],
  'Z-5 previous quote_cart signature is dropped');
SELECT has_function('public', 'check_delivery_availability',
  array['text','double precision','double precision','bigint'], 'Z-6 preview RPC exists');
SELECT has_index('public', 'service_areas', 'idx_service_areas_boundary_gist',
  'Z-7 boundaries are spatially indexed');

-- ─── resolve_delivery_zone ───────────────────────────────────────────────────
SELECT is(
  (SELECT value FROM public.resolve_delivery_zone(12.97, 77.59)), 'Z1-INNER',
  'Z-8 the lowest-priority covering boundary wins when two overlap');
SELECT is_empty($q$SELECT 1 FROM public.resolve_delivery_zone(13.50, 78.50)$q$,
  'Z-9 a point outside every boundary resolves to no zone');
SELECT is_empty($q$SELECT 1 FROM public.resolve_delivery_zone(0, 0)$q$,
  'Z-10 (0,0) is treated as no location, not as a point in the Gulf of Guinea');
SELECT is_empty($q$SELECT 1 FROM public.resolve_delivery_zone(NULL, NULL)$q$,
  'Z-11 NULL coordinates resolve to no zone');
SELECT is_empty($q$SELECT 1 FROM public.resolve_delivery_zone(95, 200)$q$,
  'Z-12 out-of-range coordinates resolve to no zone');

-- ─── Fee precedence: PIN row -> zone row -> store settings ───────────────────
SELECT is(
  (SELECT fee_paise FROM public.compute_delivery_fee_for_pin(10000, '900002', 12.97, 77.59)),
  1500::bigint,
  'Z-13 a PIN with its own override keeps it even inside a cheaper zone');
SELECT is(
  (SELECT fee_paise FROM public.compute_delivery_fee_for_pin(10000, '900001', 12.97, 77.59)),
  700::bigint,
  'Z-14 a PIN with no override takes the covering zone price');
SELECT is(
  (SELECT fee_paise FROM public.compute_delivery_fee_for_pin(10000, '900001', 13.50, 78.50)),
  3000::bigint,
  'Z-15 outside every boundary the store-wide fee still applies');
SELECT is(
  (SELECT fee_paise FROM public.compute_delivery_fee_for_pin(10000, NULL, 12.97, 77.59)),
  700::bigint,
  'Z-16 no PIN at all still resolves through the zone when the point is covered');
SELECT is(
  (SELECT rule_note FROM public.compute_delivery_fee_for_pin(10000, '900001', 12.97, 77.59))
    LIKE '%Inner belt%',
  true, 'Z-17 the note names the zone that priced the cart');
SELECT is(
  (SELECT rule_note FROM public.compute_delivery_fee_for_pin(10000, '900001', 13.50, 78.50))
    LIKE '%zone%',
  false, 'Z-18 the store-rule note does not claim a zone');
SELECT is(
  (SELECT fee_paise FROM public.compute_delivery_fee_for_pin(60000, '900001', 12.97, 77.59)),
  0::bigint,
  'Z-19 the free-delivery branch is unaffected by coordinates');
SELECT is(
  (SELECT amount_to_free_delivery FROM public.compute_delivery_fee_for_pin(10000, '900001', 12.97, 77.59)),
  40000::bigint,
  'Z-20 distance-to-free-delivery still measures against the free threshold');

-- ─── Pre-order preview ───────────────────────────────────────────────────────
SELECT is(
  (SELECT (public.check_delivery_availability('900001', 12.97, 77.59, 10000)->>'deliverable')::boolean),
  true, 'Z-21 a configured PIN is deliverable');
SELECT is(
  (SELECT (public.check_delivery_availability('900009', 12.97, 77.59, 10000)->>'deliverable')::boolean),
  false,
  'Z-22 an unconfigured PIN is NOT deliverable even though the point sits inside a zone');
SELECT is(
  (SELECT (public.check_delivery_availability('900001', 12.97, 77.59, 10000)->>'fee_paise')::bigint),
  (SELECT fee_paise FROM public.compute_delivery_fee_for_pin(10000, '900001', 12.97, 77.59)),
  'Z-23 the preview fee is the same function the order uses, so they cannot disagree');
SELECT is(
  (SELECT public.check_delivery_availability('900001', 12.97, 77.59, 10000)->>'zone_label'),
  'Inner belt', 'Z-24 the preview names the zone that will price the order');
SELECT is(
  (SELECT public.check_delivery_availability('', NULL, NULL, 0)->>'error'),
  'VALIDATION_FAILED', 'Z-25 an empty PIN is rejected instead of guessing');

-- ─── place_order: the gate is unchanged, the price follows the zone ───────────
INSERT INTO auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000', '04100000-0000-0000-0000-000000000001',
        'authenticated', 'authenticated', 'zone-test@test.invalid', 'x', now(), now());
INSERT INTO public.profiles (id, role, display_name)
VALUES ('04100000-0000-0000-0000-000000000001', 'customer', 'Zone Test')
ON CONFLICT (id) DO UPDATE SET role = EXCLUDED.role;
INSERT INTO public.products (sku, name, quantity_type, price_paise, price_basis_qty,
                             unit_label, step_qty, min_qty, stock_qty, active, available)
VALUES ('ZONE-PACKED', 'Zone Packed', 'PACKED', 10000, 1, 'pack', 1, 1, 50, true, true)
ON CONFLICT (sku) DO UPDATE SET stock_qty = EXCLUDED.stock_qty;

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claims = '{"sub":"04100000-0000-0000-0000-000000000001","role":"authenticated"}';

-- Below the 25000 charge threshold, so the fee rule is exercised.
SELECT is(
  (SELECT public.place_order(jsonb_build_object(
      'items', jsonb_build_array(jsonb_build_object(
        'product_id', (SELECT id FROM public.products WHERE sku = 'ZONE-PACKED'),
        'qty_base', 1)),
      'address', jsonb_build_object(
        'recipient_name', 'Zone Test', 'phone', '9000000001',
        'address_lines', '1 Test Street', 'city', 'Test', 'state', 'Test',
        'postal_code', '900009', 'lat', 12.97, 'lng', 77.59),
      'payment_method', 'cod'),
    'zone-041-out-of-area')->>'error'),
  'OUT_OF_AREA',
  'Z-26 place_order still refuses an unlisted PIN that sits inside a drawn zone');

SELECT lives_ok($q$SELECT public.place_order(jsonb_build_object(
  'items', jsonb_build_array(jsonb_build_object(
    'product_id', (SELECT id FROM public.products WHERE sku = 'ZONE-PACKED'),
    'qty_base', 1)),
  'address', jsonb_build_object(
    'recipient_name', 'Zone Test', 'phone', '9000000001',
    'address_lines', '1 Test Street', 'city', 'Test', 'state', 'Test',
    'postal_code', '900001', 'lat', 12.97, 'lng', 77.59),
  'payment_method', 'cod'), 'zone-041-in-area')$q$,
  'Z-27a an in-zone order for a configured PIN is accepted');

SELECT is(
  (SELECT o.delivery_fee_paise FROM public.orders o
    WHERE o.idempotency_key = 'zone-041-in-area'),
  700::bigint,
  'Z-27b the accepted order is charged the zone price, not the store price');

SELECT is(
  (SELECT (o.fee_breakdown->>'zone_label') FROM public.orders o
    WHERE o.idempotency_key = 'zone-041-in-area'),
  'Inner belt', 'Z-28 the placed order records which zone priced it');

SELECT is(
  (SELECT o.address_snapshot->>'lat' FROM public.orders o
    WHERE o.idempotency_key = 'zone-041-in-area'),
  '12.97', 'Z-29 the validated coordinate is stored on the order snapshot');

SELECT lives_ok($q$SELECT public.place_order(jsonb_build_object(
  'items', jsonb_build_array(jsonb_build_object(
    'product_id', (SELECT id FROM public.products WHERE sku = 'ZONE-PACKED'),
    'qty_base', 1)),
  'address', jsonb_build_object(
    'recipient_name', 'Zone Test', 'phone', '9000000001',
    'address_lines', '1 Test Street', 'city', 'Test', 'state', 'Test',
    'postal_code', '900001', 'lat', 'not-a-number', 'lng', '77.59'),
  'payment_method', 'cod'), 'zone-041-bad-coord')$q$,
  'Z-30 a garbage coordinate cannot cast-fail the order RPC');

SELECT is(
  (SELECT o.address_snapshot->>'lat' FROM public.orders o
    WHERE o.idempotency_key = 'zone-041-bad-coord'),
  NULL, 'Z-31 the garbage coordinate is stored as absent, not as a bogus value');

RESET ROLE;

-- ─── Map read shape and privilege boundary ───────────────────────────────────
SELECT is(
  (SELECT count(*)::integer FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'delivery_zone_map'
      AND (column_name ILIKE '%paise%' OR column_name ILIKE '%fee%'
           OR column_name ILIKE '%threshold%' OR column_name ILIKE '%discount%')),
  0::integer, 'Z-32 the draw-only map view exposes no amount column at all');

SELECT is(
  (SELECT count(*)::integer FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'delivery_zone_map'
      -- A violation is: public to anon while still reading with the owner's RLS-bypassing rights.
      AND COALESCE(array_to_string(c.reloptions, ','), '') NOT LIKE '%security_invoker=true%'
      AND EXISTS (
        SELECT 1 FROM information_schema.role_table_grants g
        WHERE g.table_name = 'delivery_zone_map' AND g.grantee = 'anon')),
  0::integer,
  'Z-33 the map view is never granted to anon unless it respects the caller RLS');

-- The mobile DTOs parse quote_cart by key, so its shape must not drift.
SELECT is(
  ARRAY(SELECT jsonb_object_keys(public.quote_cart(
        jsonb_build_array(jsonb_build_object(
          'product_id', (SELECT id FROM public.products WHERE sku = 'ZONE-PACKED'),
          'qty_base', 1)),
        NULL, '900001', NULL, 12.97, 77.59))
       ORDER BY 1),
  ARRAY['amount_to_free_delivery','below_charge_threshold','coupon_applied','coupon_code',
        'coupon_error','delivery_fee_paise','discount_paise','fee_rule_note','free_item_error',
        'free_item_options','free_item_selected_id','free_item_selection_required',
        'grand_total_paise','item_subtotal_paise','lines','tax_paise']::text[],
  'Z-34 quote_cart returns exactly the 16 keys the clients already parse, no more and no fewer');

-- ─── Role-scoped checks, run last because they switch LOCAL ROLE ─────────────
SET LOCAL ROLE anon;

SELECT throws_ok(
  $q$SELECT 1 FROM public.resolve_delivery_zone(12.97, 77.59)$q$, '42501', NULL,
  'Z-35 anon cannot call the zone resolver directly');

SELECT lives_ok(
  $q$SELECT count(*) FROM public.delivery_zone_map$q$,
  'Z-36 anon can read the map view for drawing');

RESET ROLE;

SELECT * FROM finish();
ROLLBACK;
