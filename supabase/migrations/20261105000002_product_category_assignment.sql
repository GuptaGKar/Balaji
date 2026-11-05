-- ============================================================
-- 20261105000002  Atomic assignment of a product's categories
-- ============================================================
-- The dashboard must be able to set a product's primary category and its additional
-- membership in one step. Doing it with two client round trips lets products.category_id and
-- product_categories disagree on partial failure, and the disagreement is invisible: reports
-- and coupon scope read the primary while listings read the join table.
--
-- Ordering matters because of the trigger added in 20261105000001: deleting the pair that is
-- still the primary is refused. So the primary is moved FIRST, and only then are the rows the
-- manager deselected removed.
--
-- The RPC is exposed to authenticated clients; RLS on products and product_categories decides
-- who can actually get through, which today means managers only.

CREATE OR REPLACE FUNCTION public.set_product_categories(
  p_product_id           uuid,
  p_primary_category_id  uuid,
  p_category_ids         uuid[] DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_wanted       uuid[];
  v_exists       integer;
  v_missing      integer;
  v_primary_name text;
BEGIN
  IF p_product_id IS NULL THEN
    RAISE EXCEPTION 'A product id is required.';
  END IF;

  IF p_primary_category_id IS NULL THEN
    RAISE EXCEPTION 'A product must have a primary category. Choose one from the list.';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM public.products WHERE id = p_product_id) THEN
    RAISE EXCEPTION 'Product % does not exist.', p_product_id;
  END IF;

  SELECT count(*) INTO v_exists
    FROM public.categories WHERE id = p_primary_category_id;
  IF v_exists <> 1 THEN
    RAISE EXCEPTION 'Category % does not exist, so it cannot be the primary category.', p_primary_category_id;
  END IF;

  -- The primary is always a member. A caller that omitted it gets it added rather than a
  -- rejected save, because the dashboard and the API should never disagree about this.
  v_wanted := coalesce(p_category_ids, ARRAY[]::uuid[]);
  IF NOT (p_primary_category_id = ANY (v_wanted)) THEN
    v_wanted := array_append(v_wanted, p_primary_category_id);
  END IF;

  SELECT count(*) INTO v_missing
    FROM unnest(v_wanted) w
   WHERE NOT EXISTS (SELECT 1 FROM public.categories c WHERE c.id = w);
  IF v_missing > 0 THEN
    RAISE EXCEPTION '% of the selected categories no longer exist. Reload the product and choose again.', v_missing;
  END IF;

  SELECT name INTO v_primary_name
    FROM public.categories WHERE id = p_primary_category_id;

  -- Primary first, so the deselection below is never trying to remove the current primary.
  UPDATE public.products
     SET category_id = p_primary_category_id
   WHERE id = p_product_id;

  DELETE FROM public.product_categories
   WHERE product_id = p_product_id
     AND category_id <> ALL (v_wanted);

  INSERT INTO public.product_categories (product_id, category_id, sort_order)
  SELECT p_product_id, w, ord
    FROM unnest(v_wanted) WITH ORDINALITY AS t(w, ord)
  ON CONFLICT (product_id, category_id) DO UPDATE
    SET sort_order = EXCLUDED.sort_order;

  RETURN jsonb_build_object(
    'product_id',           p_product_id,
    'primary_category_id',  p_primary_category_id,
    'primary_category_name', v_primary_name,
    'category_ids',         (SELECT array_agg(category_id ORDER BY sort_order)
                               FROM public.product_categories
                              WHERE product_id = p_product_id)
  );
END;
$$;

COMMENT ON FUNCTION public.set_product_categories IS
  'Sets a product''s primary category and its full membership atomically. SECURITY INVOKER, so RLS decides authority - which today means managers, matching product writes.';

-- The join table is already granted to managers by 20261105000001, which is the right scope:
-- products_write_manager (20261014000017_security_hardening.sql:85) restricts product writes
-- to managers alone. Membership deliberately does NOT widen that to staff. If it did, a staff
-- user could rewrite product_categories while every UPDATE on products.category_id was still
-- refused by RLS - the precise primary-versus-membership disagreement this function exists to
-- make impossible. So no new policy is added here, and authority stays exactly as it is for
-- the product row itself.

GRANT EXECUTE ON FUNCTION public.set_product_categories(uuid, uuid, uuid[]) TO authenticated;

NOTIFY pgrst, 'reload schema';
