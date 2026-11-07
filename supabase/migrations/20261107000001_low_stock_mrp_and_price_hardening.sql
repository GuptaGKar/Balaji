-- Staff price editing, follow-up to 20261026000005_product_mrp.sql.
--
-- 1) get_low_stock_products() must return mrp_paise. The staff adjust dialog prefills both
--    prices from this RPC; without the column, MRP opened blank and saving the price cleared
--    a real MRP. The return type changes, so the old function is dropped in this migration.
-- 2) update_product_price() was created after the security-hardening pass and therefore still
--    carried PostgreSQL's default PUBLIC EXECUTE grant. Revoke it from PUBLIC and anon; the
--    function keeps its own staff/manager check but must not be callable by anonymous clients.

DROP FUNCTION IF EXISTS public.get_low_stock_products();

CREATE FUNCTION public.get_low_stock_products()
RETURNS TABLE (
  id uuid,
  sku text,
  name text,
  stock_qty bigint,
  low_stock_threshold bigint,
  quantity_type public.quantity_type,
  unit_label text,
  price_basis_qty bigint,
  price_paise bigint,
  mrp_paise bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN;
  END IF;

  RETURN QUERY
  SELECT p.id, p.sku, p.name, p.stock_qty, p.low_stock_threshold,
         p.quantity_type, p.unit_label, p.price_basis_qty, p.price_paise, p.mrp_paise
  FROM public.products p
  WHERE p.active = true
    AND p.stock_qty <= p.low_stock_threshold
  ORDER BY p.low_stock_threshold - p.stock_qty DESC, p.name;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.get_low_stock_products() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_low_stock_products() TO authenticated;

-- 3) A live product must never be made free. The old function only rejected negative
--    prices, so a stale or malicious client could set 0 and every order for it would total
--    zero. The app blocks ₹0 too, but the server is the authority.
CREATE OR REPLACE FUNCTION public.update_product_price(
  p_product_id uuid,
  p_price_paise bigint,
  p_mrp_paise bigint DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_role public.user_role;
  v_product public.products%ROWTYPE;
BEGIN
  v_role := public.current_role();
  IF v_role NOT IN ('manager'::public.user_role, 'staff'::public.user_role) THEN
    RAISE EXCEPTION 'Only staff or managers can update product prices.';
  END IF;

  IF p_price_paise IS NULL OR p_price_paise <= 0 THEN
    RAISE EXCEPTION 'Selling price must be greater than zero.';
  END IF;

  IF p_mrp_paise IS NOT NULL AND p_mrp_paise < 0 THEN
    RAISE EXCEPTION 'MRP cannot be negative.';
  END IF;

  UPDATE public.products
  SET price_paise = p_price_paise,
      mrp_paise = p_mrp_paise,
      updated_at = now()
  WHERE id = p_product_id
  RETURNING * INTO v_product;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Product not found.';
  END IF;

  RETURN jsonb_build_object(
    'success', true,
    'id', v_product.id,
    'price_paise', v_product.price_paise,
    'mrp_paise', v_product.mrp_paise
  );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.update_product_price(uuid, bigint, bigint) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_product_price(uuid, bigint, bigint) TO authenticated;

NOTIFY pgrst, 'reload schema';
