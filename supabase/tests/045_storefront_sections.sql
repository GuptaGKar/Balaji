-- pgTAP 045: storefront_sections and storefront_section_items - shape, caps and visibility.
--
-- Contract under test, in the order it matters:
--   1. with zero sections the read RPC returns [], so both apps keep their built-in layout;
--   2. an anonymous shopper sees ACTIVE sections only, and the payload carries no money;
--   3. the caps (20 per page, 30 per section) are enforced in the database, not the dashboard;
--   4. an item is exactly one of product/category and must agree with its parent's type;
--   5. only a manager can write - a customer's edit changes no row.
--
-- Self-contained: creates pgTAP and the fixture helper inside this transaction, which the
-- trailing ROLLBACK undoes entirely, so it runs in the SQL editor, via `supabase db query`
-- and under `supabase test db` alike.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

-- Fail with instructions instead of a mid-file abort deep inside a test.
DO $boot$
BEGIN
  IF to_regclass('public.storefront_sections') IS NULL
     OR to_regclass('public.storefront_section_items') IS NULL THEN
    RAISE EXCEPTION 'Migration 20261105000001_storefront_sections.sql has not been applied. Apply it before running suite 045.';
  END IF;
END;
$boot$;

-- Test-only, invoker-security fixture helper; created inside this rollback transaction.
CREATE FUNCTION pg_temp.make_profile(p_id uuid, p_role public.user_role, p_name text)
RETURNS void LANGUAGE plpgsql SET search_path = public, extensions AS $fn$
BEGIN
  INSERT INTO auth.users(id, email, aud, role)
    VALUES (p_id, 'fixture-' || p_id::text || '@test.invalid', 'authenticated', 'authenticated');
  UPDATE public.profiles
     SET role = p_role, display_name = p_name, is_active = true, must_change_password = false
   WHERE id = p_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Signup trigger did not create the fixture profile';
  END IF;
END;
$fn$;

SELECT plan(30);

-- ---------------------------------------------------------------- shape
SELECT has_table('public', 'storefront_sections', 'S45-01 storefront_sections exists');
SELECT has_table('public', 'storefront_section_items', 'S45-02 storefront_section_items exists');
SELECT has_column('public', 'storefront_section_items', 'section_id', 'S45-03 items point at a section');
SELECT col_is_pk('public', 'storefront_sections', 'id', 'S45-04 section id is the primary key');
SELECT has_index('public', 'storefront_sections', 'storefront_sections_page_order_idx',
  'S45-05 (page, sort_order) is indexed, the shape every read uses');

-- ---------------------------------------------------------------- empty storefront
SELECT lives_ok($$SELECT public.get_storefront('home')$$,
  'S45-06 get_storefront runs against an unmigrated-looking empty storefront');
SELECT is(public.get_storefront('home'), '[]'::jsonb,
  'S45-07 zero sections returns [], not null - the apps key their fallback off this');

-- ---------------------------------------------------------------- heading and type checks
SELECT throws_ok($x$INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
                  VALUES ('checkout', 'Wrong page', 'products', 1)$x$,
  NULL, NULL, 'S45-08 a page outside home/categories is rejected');

SELECT throws_ok($x$INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
                  VALUES ('home', 'Wrong type', 'banners', 1)$x$,
  NULL, NULL, 'S45-09 a section_type outside products/categories is rejected');

SELECT throws_ok($x$INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
                  VALUES ('home', '', 'products', 1)$x$,
  NULL, NULL, 'S45-10 an empty heading is rejected');

SELECT throws_ok($x$INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
                  VALUES ('home', repeat('x', 41), 'products', 1)$x$,
  NULL, NULL, 'S45-11 a heading longer than 40 characters is rejected');

SELECT throws_ok($x$INSERT INTO public.storefront_sections (page, title, subtitle, section_type, sort_order)
                  VALUES ('home', 'Long subtitle', repeat('y', 81), 'products', 1)$x$,
  NULL, NULL, 'S45-12 a subtitle longer than 80 characters is rejected');

SELECT throws_ok($x$INSERT INTO public.storefront_sections (page, title, subtitle, section_type, sort_order)
                  VALUES ('home', NULL, NULL, 'products', 1)$x$,
  NULL, NULL, 'S45-13 a null heading is rejected');

-- ---------------------------------------------------------------- item XOR and type agreement
SELECT throws_ok($x$
  DO $i1$
  DECLARE v_sec uuid;
  BEGIN
    INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
         VALUES ('home', 'XOR neither', 'products', 1) RETURNING id INTO v_sec;
    INSERT INTO public.storefront_section_items (section_id, sort_order) VALUES (v_sec, 1);
  END;
  $i1$$x$,
  NULL, NULL, 'S45-14 an item with neither product_id nor category_id is rejected');

SELECT throws_ok($x$
  DO $i2$
  DECLARE v_sec uuid; v_prod uuid; v_cat uuid;
  BEGIN
    INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
         VALUES ('home', 'XOR both', 'products', 1) RETURNING id INTO v_sec;
    INSERT INTO public.products (sku, name, quantity_type, price_paise, price_basis_qty,
                                 unit_label, step_qty, min_qty, active, available)
         VALUES ('QA-045-BOTH', 'QA Both Ids', 'PACKED', 100, 1, '1 pack', 1, 1, true, true)
         RETURNING id INTO v_prod;
    INSERT INTO public.categories (name, slug) VALUES ('QA 045 cat', 'qa-045-cat') RETURNING id INTO v_cat;
    INSERT INTO public.storefront_section_items (section_id, product_id, category_id, sort_order)
         VALUES (v_sec, v_prod, v_cat, 1);
  END;
  $i2$$x$,
  NULL, NULL, 'S45-15 an item with BOTH product_id and category_id is rejected');

SELECT throws_ok($x$
  DO $i3$
  DECLARE v_sec uuid; v_prod uuid;
  BEGIN
    INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
         VALUES ('home', 'Type mismatch', 'categories', 1) RETURNING id INTO v_sec;
    INSERT INTO public.products (sku, name, quantity_type, price_paise, price_basis_qty,
                                 unit_label, step_qty, min_qty, active, available)
         VALUES ('QA-045-TM', 'QA Type Mismatch', 'PACKED', 100, 1, '1 pack', 1, 1, true, true)
         RETURNING id INTO v_prod;
    INSERT INTO public.storefront_section_items (section_id, product_id, sort_order)
         VALUES (v_sec, v_prod, 1);
  END;
  $i3$$x$,
  NULL, NULL, 'S45-16 a product inside a "categories" section is refused by the type trigger');

SELECT throws_ok($x$
  DO $i4$
  DECLARE v_sec uuid; v_cat uuid;
  BEGIN
    INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
         VALUES ('home', 'Type mismatch 2', 'products', 1) RETURNING id INTO v_sec;
    INSERT INTO public.categories (name, slug) VALUES ('QA 045 cat2', 'qa-045-cat2') RETURNING id INTO v_cat;
    INSERT INTO public.storefront_section_items (section_id, category_id, sort_order)
         VALUES (v_sec, v_cat, 1);
  END;
  $i4$$x$,
  NULL, NULL, 'S45-17 a category inside a "products" section is refused by the type trigger');

SELECT throws_ok($x$
  DO $i5$
  DECLARE v_sec uuid; v_prod uuid;
  BEGIN
    INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
         VALUES ('home', 'Duplicate item', 'products', 1) RETURNING id INTO v_sec;
    INSERT INTO public.products (sku, name, quantity_type, price_paise, price_basis_qty,
                                 unit_label, step_qty, min_qty, active, available)
         VALUES ('QA-045-DUP', 'QA Duplicate', 'PACKED', 100, 1, '1 pack', 1, 1, true, true)
         RETURNING id INTO v_prod;
    INSERT INTO public.storefront_section_items (section_id, product_id, sort_order)
         VALUES (v_sec, v_prod, 1), (v_sec, v_prod, 2);
  END;
  $i5$$x$,
  NULL, NULL, 'S45-18 the same product twice in one section is rejected');

-- ---------------------------------------------------------------- caps
SELECT throws_ok($x$
  DO $c1$
  DECLARE v_sec uuid; v_cat uuid; i integer;
  BEGIN
    INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
         VALUES ('home', 'Item cap', 'categories', 1) RETURNING id INTO v_sec;
    FOR i IN 1..31 LOOP
      INSERT INTO public.categories (name, slug)
           VALUES ('QA Cap ' || i, 'qa-cap-' || i) RETURNING id INTO v_cat;
      INSERT INTO public.storefront_section_items (section_id, category_id, sort_order)
           VALUES (v_sec, v_cat, i);
    END LOOP;
  END;
  $c1$$x$,
  NULL, 'A section can hold at most 30 items%',
  'S45-19 a 31st item is refused with the readable cap message');

SELECT throws_ok($x$
  DO $c2$
  DECLARE i integer;
  BEGIN
    FOR i IN 1..21 LOOP
      INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
           VALUES ('categories', 'Cap section ' || i, 'products', i);
    END LOOP;
  END;
  $c2$$x$,
  NULL, 'A page can have at most 20 sections%',
  'S45-20 a 21st section on one page is refused with the readable cap message');

SELECT lives_ok($x$INSERT INTO public.storefront_sections (page, title, section_type, sort_order)
                 VALUES ('home', 'Other page still open', 'products', 1)$x$,
  'S45-21 the cap is per page, so the other page is unaffected by a full one');

-- ---------------------------------------------------------------- integrity of the shape
SELECT is(
  (SELECT count(*) FROM pg_constraint
    WHERE conrelid = 'public.storefront_section_items'::regclass
      AND confrelid = 'public.storefront_sections'::regclass
      AND contype = 'f' AND confdeltype = 'c'),
  1::bigint,
  'S45-22 deleting a section takes its items with it (ON DELETE CASCADE)');

SELECT is(
  (SELECT count(*) FROM pg_indexes WHERE tablename = 'storefront_section_items'
     AND indexname IN ('storefront_section_items_product_uniq', 'storefront_section_items_category_uniq')),
  2::bigint,
  'S45-23 duplicate protection is two PARTIAL uniques, so the NULL side cannot collide');

SELECT is(
  (SELECT count(*) FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'storefront_sections'
       AND column_name IN ('price_paise', 'stock_qty', 'delivery_fee_paise', 'mrp_paise')),
  0::bigint,
  'S45-24 sections store no money or stock - those stay server-side decisions');

SELECT is(
  (SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
    WHERE c.relname = 'storefront_sections' AND t.tgname = 'set_updated_at_storefront_sections'),
  1::bigint,
  'S45-25 updated_at is maintained by the shared trigger, which edit-conflict detection depends on');

-- ---------------------------------------------------------------- visibility and write control
-- Seed two sections with known ids. Earlier tests leave another active home section
-- ('Other page still open'), so every assertion below is pinned to these ids rather than
-- to a global count or to created_at ordering, either of which would silently drift.
CREATE TEMP TABLE s45_seed AS
SELECT (array_agg(id ORDER BY sort_order))[1] AS visible,
       (array_agg(id ORDER BY sort_order))[2] AS hidden
  FROM (INSERT INTO public.storefront_sections (page, title, section_type, sort_order, active)
             VALUES ('home', 'QA Visible', 'products', 900, true),
                    ('home', 'QA Hidden',  'products', 901, false)
        RETURNING id, sort_order) x;

SELECT pg_temp.make_profile('04500000-0000-0000-0000-000000000001', 'customer', 'QA Customer');
SELECT pg_temp.make_profile('04500000-0000-0000-0000-000000000002', 'manager',  'QA Manager');

SET LOCAL ROLE anon;
SELECT is(
  (SELECT count(*) FROM jsonb_array_elements(public.get_storefront('home')) e
    WHERE (e.value ->> 'id')::uuid = (SELECT visible FROM s45_seed)),
  1::bigint,
  'S45-26 anon sees the active seeded section');
SELECT is(
  (SELECT count(*) FROM jsonb_array_elements(public.get_storefront('home')) e
    WHERE (e.value ->> 'id')::uuid = (SELECT hidden FROM s45_seed)),
  0::bigint,
  'S45-27 an inactive section never reaches an anonymous shopper');
SELECT is(
  (SELECT bool_and((e.value -> 'item_ids') IS NOT NULL
                   AND jsonb_typeof(e.value -> 'item_ids') = 'array')
     FROM jsonb_array_elements(public.get_storefront('home')) e),
  true,
  'S45-28 every section returned carries an item_ids ARRAY, empty rather than missing');
RESET ROLE;

-- A customer's edit must change no row. RLS filters the UPDATE to zero rows rather than
-- raising, which is exactly what the app would otherwise misread as success.
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '04500000-0000-0000-0000-000000000001';
UPDATE public.storefront_sections SET title = 'TAMPERED BY CUSTOMER'
 WHERE id = (SELECT visible FROM s45_seed);
RESET ROLE;
SELECT is(
  (SELECT title FROM public.storefront_sections WHERE id = (SELECT visible FROM s45_seed)),
  'QA Visible'::text,
  'S45-29 a customer edit changes no section');

SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '04500000-0000-0000-0000-000000000002';
UPDATE public.storefront_sections SET title = 'QA Visible Renamed'
 WHERE id = (SELECT visible FROM s45_seed);
RESET ROLE;
SELECT is(
  (SELECT title FROM public.storefront_sections WHERE id = (SELECT visible FROM s45_seed)),
  'QA Visible Renamed'::text,
  'S45-30 a manager edit does land, so the policy is not simply blocking everyone');

SELECT * FROM finish();

ROLLBACK;
