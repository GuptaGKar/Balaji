-- pgTAP 048: set_product_categories - atomic primary plus membership, in the right order.
--
-- The single most important behaviour here is ordering. 20261105000001 added a trigger that
-- refuses deleting the pair which is still a product's primary. So moving a product from
-- category A to category B must set the primary FIRST and only then drop A. A naive
-- delete-then-update implementation fails S48-05, and that failure is the whole point of the
-- function existing.
--
-- Self-contained: creates pgTAP and fixtures inside this transaction, which the trailing
-- ROLLBACK undoes entirely.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SET LOCAL search_path = public, extensions;

DO $boot$
BEGIN
  IF to_regprocedure('public.set_product_categories(uuid,uuid,uuid[])') IS NULL THEN
    RAISE EXCEPTION 'Migration 20261105000002_product_category_assignment.sql has not been applied. Apply it before running suite 048.';
  END IF;
END;
$boot$;

CREATE FUNCTION pg_temp.make_profile(p_id uuid, p_role public.user_role, p_name text)
RETURNS void LANGUAGE plpgsql SET search_path = public, extensions AS $fn$
BEGIN
  INSERT INTO auth.users(id, email, aud, role)
    VALUES (p_id, 'fixture-' || p_id::text || '@test.invalid', 'authenticated', 'authenticated');
  UPDATE public.profiles SET role = p_role, display_name = p_name, is_active = true,
         must_change_password = false
   WHERE id = p_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Signup trigger did not create the fixture profile'; END IF;
END;
$fn$;

SELECT plan(20);

INSERT INTO public.categories (name, slug)
     VALUES ('QA48 A', 'qa48-a'), ('QA48 B', 'qa48-b'), ('QA48 C', 'qa48-c');
INSERT INTO public.products (sku, name, quantity_type, price_paise, price_basis_qty,
                             unit_label, step_qty, min_qty, active, available)
     VALUES ('QA48-P', 'QA48 Product', 'PACKED', 6000, 1, '1 pack', 1, 1, true, true);

CREATE TEMP TABLE q48 AS
SELECT p.id AS product,
       (SELECT id FROM public.categories WHERE slug = 'qa48-a') AS a,
       (SELECT id FROM public.categories WHERE slug = 'qa48-b') AS b,
       (SELECT id FROM public.categories WHERE slug = 'qa48-c') AS c
  FROM public.products p WHERE p.sku = 'QA48-P';

-- The manager / customer blocks below read this table while SET LOCAL ROLE is in effect, and a
-- temp table is private to its owner unless granted.
GRANT SELECT ON q48 TO PUBLIC;

SELECT pg_temp.make_profile('04800000-0000-0000-0000-000000000001', 'manager',  'QA48 Manager');
SELECT pg_temp.make_profile('04800000-0000-0000-0000-000000000002', 'customer', 'QA48 Customer');

-- ---------------------------------------------------------------- starting state
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '04800000-0000-0000-0000-000000000001';

SELECT lives_ok($$SELECT public.set_product_categories(
        (SELECT product FROM q48), (SELECT a FROM q48), ARRAY[(SELECT a FROM q48)])$$,
  'S48-01 a manager can assign the primary category on its own');

SELECT is(
  (SELECT count(*) FROM public.product_categories WHERE product_id = (SELECT product FROM q48)),
  1::bigint,
  'S48-02 exactly one membership row, no duplicates invented');

-- ---------------------------------------------------------------- multi membership
SELECT is(
  (SELECT public.set_product_categories(
          (SELECT product FROM q48), (SELECT a FROM q48),
          ARRAY[(SELECT a FROM q48), (SELECT b FROM q48), (SELECT c FROM q48)]) ->> 'primary_category_name'),
  'QA48 A'::text,
  'S48-03 the result names the primary category so the dashboard can echo it back');

SELECT is(
  (SELECT count(*) FROM public.product_categories WHERE product_id = (SELECT product FROM q48)),
  3::bigint,
  'S48-04 all three memberships are stored');

-- ---------------------------------------------------------------- THE ORDERING TEST
-- Moving the primary from A to B while deselecting A. The trigger refuses deleting the
-- current primary pair, so this only succeeds if the primary was moved first.
SELECT lives_ok($$SELECT public.set_product_categories(
        (SELECT product FROM q48), (SELECT b FROM q48), ARRAY[(SELECT b FROM q48)])$$,
  'S48-05 moving the primary and dropping the old category in ONE call succeeds - the primary is set before the deselection');

SELECT is(
  (SELECT category_id FROM public.products WHERE id = (SELECT product FROM q48)),
  (SELECT b FROM q48),
  'S48-06 the primary really moved');

SELECT is(
  (SELECT count(*) FROM public.product_categories
    WHERE product_id = (SELECT product FROM q48)
      AND category_id = (SELECT a FROM q48)),
  0::bigint,
  'S48-07 the deselected old primary is gone, not left dangling');

SELECT is(
  (SELECT count(*) FROM public.product_categories pc
    WHERE pc.product_id = (SELECT product FROM q48)
      AND pc.category_id = (SELECT category_id FROM public.products WHERE id = (SELECT product FROM q48))),
  1::bigint,
  'S48-08 after every reshuffle the primary is still present in the membership set');

-- ---------------------------------------------------------------- primary is always a member
SELECT lives_ok($$SELECT public.set_product_categories(
        (SELECT product FROM q48), (SELECT c FROM q48), ARRAY[]::uuid[])$$,
  'S48-09 an empty membership list is accepted when a primary is given');

SELECT is(
  (SELECT count(*) FROM public.product_categories WHERE product_id = (SELECT product FROM q48)),
  1::bigint,
  'S48-10 the primary was added to the membership rather than leaving the product with none');

SELECT is(
  (SELECT category_id FROM public.product_categories
    WHERE product_id = (SELECT product FROM q48)),
  (SELECT c FROM q48),
  'S48-11 and it is the primary that got added');

-- ---------------------------------------------------------------- validation
SELECT throws_like($$SELECT public.set_product_categories(
        (SELECT product FROM q48), NULL, ARRAY[]::uuid[])$$,
  '%must have a primary category%',
  'S48-12 a null primary is refused with a plain-language message');

SELECT throws_like($$SELECT public.set_product_categories(
        (SELECT product FROM q48), gen_random_uuid(), ARRAY[]::uuid[])$$,
  '%cannot be the primary category%',
  'S48-13 a primary that does not exist is refused');

SELECT throws_like($$SELECT public.set_product_categories(
        (SELECT product FROM q48), (SELECT a FROM q48),
        ARRAY[(SELECT a FROM q48), gen_random_uuid()])$$,
  '%no longer exist%',
  'S48-14 a deleted category in the membership list is refused, not silently dropped');

SELECT throws_like($$SELECT public.set_product_categories(
        gen_random_uuid(), (SELECT a FROM q48), ARRAY[]::uuid[])$$,
  '%does not exist%',
  'S48-15 an unknown product is refused');

-- ---------------------------------------------------------------- authority
RESET ROLE;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '04800000-0000-0000-0000-000000000002';

SELECT throws_ok($$SELECT public.set_product_categories(
        (SELECT product FROM q48), (SELECT a FROM q48), ARRAY[(SELECT a FROM q48)])$$,
  '42501', NULL,
  'S48-16 a signed-in customer cannot reassign a product''s categories');

RESET ROLE;
SET LOCAL ROLE authenticated;
SET LOCAL request.jwt.claim.sub = '04800000-0000-0000-0000-000000000001';

SELECT is(
  (SELECT category_id FROM public.products WHERE id = (SELECT product FROM q48)),
  (SELECT c FROM q48),
  'S48-17 the refused customer call changed no primary at all');

-- ---------------------------------------------------------------- no money touched
SELECT is(
  (SELECT price_paise FROM public.products WHERE id = (SELECT product FROM q48)),
  6000::bigint,
  'S48-18 reassigning categories never touches the price');

SELECT is(
  (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public' AND p.proname = 'set_product_categories'
      AND p.prosecdef = false),
  1::bigint,
  'S48-19 the function is SECURITY INVOKER, so RLS - not a bypass - decides who can write');

SELECT is(
  (SELECT coalesce(array_to_string(proconfig, ','), '') LIKE '%search_path=%'
     FROM pg_proc WHERE proname = 'set_product_categories' LIMIT 1),
  true,
  'S48-20 it pins search_path like every other function here');

SELECT * FROM finish();

ROLLBACK;
