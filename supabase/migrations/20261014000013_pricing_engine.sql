-- M4: Pricing engine, quote_cart, place_order, idempotency, stock locking, movements
-- IMPLEMENTATION_PLAN.md §4.4, §4.5, §4.6, §4.7, §4.9

-- 1) eligible_subtotal — merchandise subtotal before discount/tax, excl. delivery fee
CREATE OR REPLACE FUNCTION public.eligible_subtotal(p_items jsonb)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_item jsonb;
  v_product public.products%ROWTYPE;
  v_qty_base bigint;
  v_unit_price_paise bigint;
  v_line_total bigint;
  v_subtotal bigint := 0;
BEGIN
  FOR v_item IN SELECT * FROM jsonb_array_elements(p_items)
  LOOP
    -- Fetch product with FOR SHARE to allow concurrent reads but block updates
    SELECT * INTO v_product
    FROM public.products
    WHERE id = (v_item->>'product_id')::uuid
    FOR SHARE;

    IF NOT FOUND THEN
      CONTINUE; -- invalid product, will be caught in place_order
    END IF;

    v_qty_base := (v_item->>'qty_base')::bigint;
    v_unit_price_paise := v_product.price_paise;

    -- Line pricing: round half up per line (plan §4.5)
    -- line_total = (2 * price * qty + basis) / (2 * basis)
    v_line_total := (2 * v_unit_price_paise * v_qty_base + v_product.price_basis_qty)
                      / (2 * v_product.price_basis_qty);

    v_subtotal := v_subtotal + v_line_total;
  END LOOP;

  RETURN v_subtotal;
END;
$$;

-- 2) compute_delivery_fee — single source of truth for fee logic (plan §4.4)
CREATE OR REPLACE FUNCTION public.compute_delivery_fee(p_eligible_subtotal bigint)
RETURNS TABLE (
  fee_paise bigint,
  rule_note text,
  below_charge_threshold boolean,
  amount_to_free_delivery bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_settings public.store_settings%ROWTYPE;
  v_fee bigint := 0;
  v_note text := '';
  v_below boolean := false;
  v_to_free bigint := 0;
BEGIN
  SELECT * INTO v_settings FROM public.store_settings;

  IF v_settings.delivery_charge_threshold_paise IS NULL OR v_settings.delivery_charge_threshold_paise = 0 THEN
    RETURN QUERY SELECT 0, 'No delivery-charge threshold configured', false, 0;
    RETURN;
  END IF;

  IF p_eligible_subtotal < v_settings.delivery_charge_threshold_paise THEN
    v_fee := v_settings.delivery_fee_paise;
    v_below := true;
    v_note := 'Delivery fee applied (below delivery-charge threshold)';
    IF v_settings.free_delivery_threshold_paise IS NOT NULL AND v_settings.free_delivery_threshold_paise > 0 THEN
      v_to_free := GREATEST(v_settings.free_delivery_threshold_paise - p_eligible_subtotal, 0);
      v_note := v_note || '; add ' || public.paise_to_rupees(v_to_free) || ' for free delivery';
    END IF;
  ELSIF v_settings.free_delivery_threshold_paise IS NOT NULL
        AND v_settings.free_delivery_threshold_paise > 0
        AND p_eligible_subtotal < v_settings.free_delivery_threshold_paise THEN
    v_fee := COALESCE(v_settings.mid_tier_fee_paise, v_settings.delivery_fee_paise);
    v_below := true;
    v_note := 'Mid-tier delivery fee applied';
    v_to_free := GREATEST(v_settings.free_delivery_threshold_paise - p_eligible_subtotal, 0);
    v_note := v_note || '; add ' || public.paise_to_rupees(v_to_free) || ' for free delivery';
  ELSE
    v_fee := 0;
    v_below := false;
    v_note := 'Free delivery (at/above threshold)';
    v_to_free := 0;
  END IF;

  RETURN QUERY SELECT v_fee, v_note, v_below, v_to_free;
END;
$$;

-- Helper: paise_to_rupees for note formatting
CREATE OR REPLACE FUNCTION public.paise_to_rupees(p_paise bigint)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $sql$
  SELECT to_char(p_paise / 100.0, 'FM999999990.00');
$sql$;

-- 3) quote_cart — read-only breakdown for cart/checkout display (plan §4.6)
CREATE OR REPLACE FUNCTION public.quote_cart(p_items jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_item jsonb;
  v_product public.products%ROWTYPE;
  v_qty_base bigint;
  v_unit_price_paise bigint;
  v_line_total bigint;
  v_display_qty text;
  v_available boolean;
  v_error text;
  v_lines jsonb := '[]'::jsonb;
  v_item_subtotal bigint := 0;
  v_discount_paise bigint := 0;
  v_delivery_fee_paise bigint := 0;
  v_tax_paise bigint := 0;
  v_grand_total bigint := 0;
  v_fee_info record;
  v_settings public.store_settings%ROWTYPE;
BEGIN
  SELECT * INTO v_settings FROM public.store_settings;

  FOR v_item IN SELECT * FROM jsonb_array_elements(p_items)
  LOOP
    v_available := true;
    v_error := NULL;

    SELECT * INTO v_product
    FROM public.products
    WHERE id = (v_item->>'product_id')::uuid;

    IF NOT FOUND THEN
      v_available := false;
      v_error := 'Product not found';
      v_qty_base := 0;
      v_unit_price_paise := 0;
      v_line_total := 0;
      v_display_qty := '0';
    ELSE
      v_qty_base := (v_item->>'qty_base')::bigint;

      -- Validate qty per §4.5
      IF v_product.quantity_type = 'PACKED' THEN
        IF v_qty_base < v_product.min_qty OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
          v_available := false;
          v_error := 'Invalid pack quantity';
        END IF;
      ELSE
        -- WEIGHTED: check step_qty precision and bounds
        IF v_qty_base % v_product.step_qty <> 0 THEN
          v_available := false;
          v_error := 'Quantity must be multiple of step (' || v_product.step_qty || 'g)';
        ELSIF v_qty_base < v_product.min_qty OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
          v_available := false;
          v_error := 'Quantity out of range';
        END IF;
      END IF;

      IF v_product.stock_qty < v_qty_base THEN
        v_available := false;
        v_error := COALESCE(v_error, '') || 'Insufficient stock';
      END IF;

      IF NOT v_product.active OR NOT v_product.available THEN
        v_available := false;
        v_error := COALESCE(v_error, '') || 'Product unavailable';
      END IF;

      v_unit_price_paise := v_product.price_paise;
      v_line_total := (2 * v_unit_price_paise * v_qty_base + v_product.price_basis_qty)
                        / (2 * v_product.price_basis_qty);

      -- Display qty formatting
      IF v_product.quantity_type = 'WEIGHTED' THEN
        IF v_product.price_basis_qty = 1000 THEN
          v_display_qty := to_char(v_qty_base / 1000.0, 'FM999999990.000') || ' kg';
        ELSE
          v_display_qty := v_qty_base || ' g';
        END IF;
      ELSE
        v_display_qty := v_qty_base || ' ' || v_product.unit_label;
      END IF;

      v_item_subtotal := v_item_subtotal + v_line_total;

    END IF;

    v_lines := v_lines || jsonb_build_object(
      'product_id', v_item->>'product_id',
      'name', COALESCE(v_product.name, 'Unknown'),
      'qty_base', v_qty_base,
      'display_qty', v_display_qty,
      'unit_price_paise', v_unit_price_paise,
      'line_total_paise', v_line_total,
      'available', v_available,
      'error', v_error
    );
  END LOOP;

  -- Delivery fee
  SELECT * INTO v_fee_info FROM public.compute_delivery_fee(v_item_subtotal);
  v_delivery_fee_paise := v_fee_info.fee_paise;

  -- Tax (if enabled)
  IF v_settings.tax_mode = 'exclusive' AND v_settings.tax_rate_bps > 0 THEN
    v_tax_paise := ((v_item_subtotal - v_discount_paise + v_delivery_fee_paise) * v_settings.tax_rate_bps) / 10000;
  ELSIF v_settings.tax_mode = 'inclusive' AND v_settings.tax_rate_bps > 0 THEN
    -- Tax already included in prices; no additional tax line
    v_tax_paise := 0;
  END IF;

  v_grand_total := v_item_subtotal - v_discount_paise + v_delivery_fee_paise + v_tax_paise;

  RETURN jsonb_build_object(
    'lines', v_lines,
    'item_subtotal_paise', v_item_subtotal,
    'discount_paise', v_discount_paise,
    'delivery_fee_paise', v_delivery_fee_paise,
    'fee_rule_note', v_fee_info.rule_note,
    'tax_paise', v_tax_paise,
    'grand_total_paise', v_grand_total,
    'below_charge_threshold', v_fee_info.below_charge_threshold,
    'amount_to_free_delivery', v_fee_info.amount_to_free_delivery
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb) TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.compute_delivery_fee(bigint) TO authenticated, anon;
GRANT EXECUTE ON FUNCTION public.eligible_subtotal(jsonb) TO authenticated, anon;