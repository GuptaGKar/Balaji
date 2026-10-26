-- Add MRP (Maximum Retail Price) to products table and support price updates by staff/managers.
--
-- Selling price remains `price_paise`.
-- `mrp_paise` is the Maximum Retail Price in integer paise.
-- When `mrp_paise` is NULL or <= `price_paise`, MRP is not shown as discounted/cut.
-- When `mrp_paise` > `price_paise`, customer apps display MRP strikethrough next to selling price.

ALTER TABLE public.products
  ADD COLUMN IF NOT EXISTS mrp_paise bigint CHECK (mrp_paise IS NULL OR mrp_paise >= 0);

COMMENT ON COLUMN public.products.mrp_paise IS
  'Maximum Retail Price in integer paise. NULL or <= price_paise means no printed discount.';

-- Column permissions for authenticated role
GRANT INSERT (mrp_paise) ON public.products TO authenticated;
GRANT UPDATE (mrp_paise, price_paise) ON public.products TO authenticated;

-- Recreate view to include mrp_paise
CREATE OR REPLACE VIEW public.catalog_products AS SELECT * FROM public.products;
ALTER VIEW public.catalog_products SET (security_invoker = true);

-- Allow staff and managers to update products
DROP POLICY IF EXISTS "products_update_staff_manager" ON public.products;
CREATE POLICY "products_update_staff_manager" ON public.products
  FOR UPDATE TO authenticated
  USING (public.current_role() IN ('manager'::public.user_role, 'staff'::public.user_role))
  WITH CHECK (public.current_role() IN ('manager'::public.user_role, 'staff'::public.user_role));

-- Secure function for staff/managers to update product prices
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

  IF p_price_paise < 0 THEN
    RAISE EXCEPTION 'Selling price cannot be negative.';
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

GRANT EXECUTE ON FUNCTION public.update_product_price(uuid, bigint, bigint) TO authenticated;
