-- pgTAP 046: product_categories - backfill, primary sync, and primary protection.
--
-- Contract under test, in the order it matters:
--   1. the join table is never allowed to contradict products.category_id, because reports,
--      coupon scope and variant grouping all still read the primary;
--   2. the backfill gave every existing product exactly one membership row, its primary;
--   3. setting or moving a primary automatically keeps that pair present;
--   4. the primary pair cannot be deleted out from under the product;
--   5. membership is public-read, so an unmigrated client cannot be blocked by it.
--
-- Self-contained: creates pgTAP and fixtures inside this transaction, which the trailing
-- ROLLBACK undoes entirely.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

DO $boot$
BEGIN
  IF to_regclass('public.product_categories') IS NULL THEN
    RAISE EXCEPTION 'Migration 20261105000001_storefront_sections.sql has not been applied. Apply it before running suite 046.';
  END IF;
END;
$boot$;

SELECT plan(23);

-- ---------------------------------------------------------------- shape
SELECT has_table('public', 'product_categories', 'S46-01 product_categories exists');
SELECT col_is_pk('public', 'product_categories', ARRAY['product_id','category_id'],
  'S46-02 the primary key is the pair, so the same membership cannot be stored twice');
SELECT has_index('public', 'product_categories', 'product_categories_category_idx',
  'S46-03 category_id is indexed, the direction a category listing filters on');
SELECT is(
  (SELECT count(*) FROM pg_constraint
     WHERE conrelid = 'public.product_categories'::regclass AND contype = 'f'),
  2::bigint,
  'S46-04 both sides are real foreign keys');
SELECT is(
  (SELECT confdeltype FROM pg_constraint
     WHERE conrelid = 'public.product_categories'::regclass
       AND confrelid = 'public.categories'::regclass),
  'c'::"char",
  'S46-05 deleting a category removes the membership rows rather than stranding them');

-- ---------------------------------------------------------------- backfill
SELECT is(
  (SELECT count(*) FROM public.product_categories pc
     JOIN public.products p ON p.id = pc.product_id
    WHERE pc.category_id = p.category_id),
  (SELECT count(*) FROM public.products WHERE category_id IS NOT NULL)::bigint,
  'S46-06 every product with a primary category has that pair backfilled');

SELECT is(
  (SELECT count(*) FROM public.products p
     WHERE p.category_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM public.product_categories pc
                        WHERE pc.product_id = p.id AND pc.category_id = p.category_id)),
  0::bigint,
  'S46-07 no product with a primary is missing from the join table');

SELECT is(
  (SELECT count(*) FROM public.products p
     WHERE p.category_id IS NULL
       AND EXISTS (SELECT 1 FROM public.product_categories pc WHERE pc.product_id = p.id)),
  0::bigint,
  'S46-08 a product with no primary invented no membership rows during backfill');

-- ---------------------------------------------------------------- fixtures for the trigger tests
INSERT INTO public.categories (name, slug) VALUES ('QA46 A', 'qa46-a'), ('QA46 B', 'qa46-b');
INSERT INTO public.products (sku, name, quantity_type, price_paise, price_basis_qty,
                             unit_label, step_qty, min_qty, active, available)
     VALUES ('QA46-P1', 'QA46 Product One', 'PACKED', 100, 1, '1 pack', 1, 1, true, true);

SELECT is(
  (SELECT count(*) FROM public.product_categories pc
     JOIN public.products p ON p.id = pc.product_id
    WHERE p.sku = 'QA46-P1'),
  0::bigint,
  'S46-09 a product created with no category has no membership rows yet');

-- ---------------------------------------------------------------- primary sync on insert
DO $sync$
BEGIN
  UPDATE public.products SET category_id = (SELECT id FROM public.categories WHERE slug = 'qa46-a')
   WHERE sku = 'QA46-P1';
END;
$sync$;

SELECT is(
  (SELECT count(*) FROM public.product_categories pc
     JOIN public.products p ON p.id = pc.product_id
     JOIN public.categories c ON c.id = pc.category_id
    WHERE p.sku = 'QA46-P1' AND c.slug = 'qa46-a'),
  1::bigint,
  'S46-10 setting the primary automatically created the matching membership row');

-- ---------------------------------------------------------------- additional membership
INSERT INTO public.product_categories (product_id, category_id, sort_order)
SELECT p.id, c.id, 1 FROM public.products p, public.categories c
 WHERE p.sku = 'QA46-P1' AND c.slug = 'qa46-b';

SELECT is(
  (SELECT count(*) FROM public.product_categories pc
     JOIN public.products p ON p.id = pc.product_id
    WHERE p.sku = 'QA46-P1'),
  2::bigint,
  'S46-11 a second category can be added, so multi-category membership really works');

-- ---------------------------------------------------------------- primary sync on move
UPDATE public.products
   SET category_id = (SELECT id FROM public.categories WHERE slug = 'qa46-b')
 WHERE sku = 'QA46-P1';

SELECT is(
  (SELECT count(*) FROM public.product_categories pc
     JOIN public.products p ON p.id = pc.product_id
     JOIN public.categories c ON c.id = pc.category_id
    WHERE p.sku = 'QA46-P1' AND c.slug = 'qa46-b'),
  1::bigint,
  'S46-12 moving the primary keeps the new pair present');

SELECT is(
  (SELECT count(*) FROM public.product_categories pc
     JOIN public.products p ON p.id = pc.product_id
    WHERE p.sku = 'QA46-P1'),
  2::bigint,
  'S46-13 moving the primary did not delete the old membership - it stays a member');

-- ---------------------------------------------------------------- primary protection
SELECT throws_ok($x$
  DELETE FROM public.product_categories pc
   USING public.products p, public.categories c
   WHERE pc.product_id = p.id AND pc.category_id = c.id
     AND p.sku = 'QA46-P1' AND c.slug = 'qa46-b'$x$,
  NULL, '%is the primary category of product%',
  'S46-14 deleting the PRIMARY pair is refused with a message naming the product');

SELECT lives_ok($x$
  DELETE FROM public.product_categories pc
   USING public.products p, public.categories c
   WHERE pc.product_id = p.id AND pc.category_id = c.id
     AND p.sku = 'QA46-P1' AND c.slug = 'qa46-a'$x$,
  'S46-15 deleting a NON-primary membership is allowed');

SELECT is(
  (SELECT count(*) FROM public.products p
     WHERE p.sku = 'QA46-P1'
       AND NOT EXISTS (SELECT 1 FROM public.product_categories pc
                        WHERE pc.product_id = p.id AND pc.category_id = p.category_id)),
  0::bigint,
  'S46-16 after that delete the product still carries its primary membership');

SELECT throws_ok($x$
  INSERT INTO public.product_categories (product_id, category_id)
  SELECT p.id, c.id FROM public.products p, public.categories c
   WHERE p.sku = 'QA46-P1' AND c.slug = 'qa46-b'$x$,
  '23505', NULL,
  'S46-17 the same membership cannot be inserted twice');

SELECT lives_ok($x$
  INSERT INTO public.product_categories (product_id, category_id)
  SELECT p.id, c.id FROM public.products p, public.categories c
   WHERE p.sku = 'QA46-P1' AND c.slug = 'qa46-a'$x$,
  'S46-17b a membership deleted at S46-15 can be added back, so the rejection above was duplication, not a broken delete');

SELECT throws_ok($x$
  INSERT INTO public.product_categories (product_id, category_id)
  SELECT p.id, gen_random_uuid() FROM public.products p WHERE p.sku = 'QA46-P1'$x$,
  '23503', NULL,
  'S46-18 membership to a category that does not exist is rejected by the foreign key');

-- ---------------------------------------------------------------- visibility
SELECT is(
  (SELECT count(*) FROM pg_policies
     WHERE tablename = 'product_categories' AND policyname = 'product_categories_read'),
  1::bigint,
  'S46-19 membership is public-read so the catalogue can resolve categories');

SELECT is(
  (SELECT count(*) FROM pg_policies
     WHERE tablename = 'product_categories' AND polcmd::text = 'A'),
  1::bigint,
  'S46-20 exactly one manager write policy governs membership');

SET LOCAL ROLE anon;
SELECT lives_ok($$SELECT count(*) FROM public.product_categories$$,
  'S46-21 anon can read membership, so an unmigrated client is never blocked by it');
RESET ROLE;

-- ---------------------------------------------------------------- no money
SELECT is(
  (SELECT count(*) FROM information_schema.columns
     WHERE table_schema = 'public' AND table_name = 'product_categories'
       AND column_name IN ('price_paise','mrp_paise','stock_qty','delivery_fee_paise')),
  0::bigint,
  'S46-22 the join table stores no money or stock - membership is structure, not price');

SELECT * FROM finish();

ROLLBACK;
