-- Migration: 20261027000001_free_delivery_fix_and_app_updates.sql
-- Description:
-- 1. Drop the ambiguous single-argument quote_cart(jsonb) overload to resolve PostgREST
--    RPC candidate function collision when called without coupon_code (i.e. {"p_items": [...]}).
-- 2. Update quote_cart so free_delivery coupon simply makes delivery free (0 paise)
--    with NO discount applied to item subtotal.
-- 3. Create app_releases table, RPC and storage bucket for in-app APK update distribution.

BEGIN;

-- 1. Drop redundant quote_cart(jsonb) overload that causes PostgREST ambiguous call error
DROP FUNCTION IF EXISTS public.quote_cart(jsonb);

-- 2. Single canonical quote_cart function with optional coupon code
CREATE OR REPLACE FUNCTION public.quote_cart(
  p_items jsonb,
  p_coupon_code text DEFAULT NULL
)
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
  v_category_subtotal bigint := 0;
  v_discount_paise bigint := 0;
  v_delivery_fee_paise bigint := 0;
  v_original_delivery_fee_paise bigint := 0;
  v_tax_paise bigint := 0;
  v_grand_total bigint := 0;
  v_fee_info record;
  v_settings public.store_settings%ROWTYPE;

  -- Coupon fields
  v_clean_code text;
  v_coupon public.coupons%ROWTYPE;
  v_coupon_applied boolean := false;
  v_coupon_error text := NULL;
BEGIN
  SELECT * INTO v_settings FROM public.store_settings;

  -- Process line items
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

      -- Quantity rules stay server-side: pack limits and weight step multiples.
      IF v_qty_base <= 0 THEN
        v_available := false;
        v_error := 'Quantity must be positive';
      ELSIF v_product.quantity_type = 'PACKED' THEN
        IF v_qty_base < v_product.min_qty
           OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
          v_available := false;
          v_error := 'Invalid pack quantity';
        END IF;
      ELSE
        IF v_product.step_qty > 0 AND v_qty_base % v_product.step_qty <> 0 THEN
          v_available := false;
          v_error := 'Quantity must be multiple of step (' || v_product.step_qty || 'g)';
        ELSIF v_qty_base < v_product.min_qty
              OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base) THEN
          v_available := false;
          v_error := 'Quantity out of range';
        END IF;
      END IF;

      IF v_product.stock_qty < v_qty_base THEN
        v_available := false;
        v_error := COALESCE(v_error, '') || 'Only ' || v_product.stock_qty || ' in stock';
      END IF;

      IF NOT v_product.active OR NOT v_product.available THEN
        v_available := false;
        v_error := COALESCE(v_error, '') || 'Product unavailable';
      END IF;

      v_unit_price_paise := v_product.price_paise;
      v_line_total := (2 * v_unit_price_paise * v_qty_base + v_product.price_basis_qty)
                        / (2 * v_product.price_basis_qty);

      -- Display quantity formatting
      IF v_product.quantity_type = 'WEIGHTED' THEN
        IF v_product.price_basis_qty = 1000 THEN
          v_display_qty := to_char(v_qty_base / 1000.0, 'FM999999990.000') || ' kg';
        ELSE
          v_display_qty := v_qty_base || ' g';
        END IF;
      ELSE
        v_display_qty := v_qty_base || ' ' || v_product.unit_label;
      END IF;

      IF v_available THEN
        v_item_subtotal := v_item_subtotal + v_line_total;
      END IF;

    END IF;

    v_lines := v_lines || jsonb_build_object(
      'product_id', v_item->>'product_id',
      'name', COALESCE(v_product.name, 'Unknown'),
      'category_id', v_product.category_id,
      'qty_base', v_qty_base,
      'display_qty', v_display_qty,
      'unit_price_paise', v_unit_price_paise,
      'line_total_paise', v_line_total,
      'available', v_available,
      'error', v_error
    );
  END LOOP;

  -- Compute standard delivery fee
  SELECT * INTO v_fee_info FROM public.compute_delivery_fee(v_item_subtotal);
  v_delivery_fee_paise := v_fee_info.fee_paise;
  v_original_delivery_fee_paise := v_delivery_fee_paise;

  -- Validate and calculate Coupon
  IF p_coupon_code IS NOT NULL AND btrim(p_coupon_code) <> '' THEN
    v_clean_code := upper(btrim(p_coupon_code));

    SELECT * INTO v_coupon
    FROM public.coupons
    WHERE code = v_clean_code;

    IF NOT FOUND THEN
      v_coupon_error := 'Coupon code not found.';
    ELSIF NOT v_coupon.active THEN
      v_coupon_error := 'This coupon is no longer active.';
    ELSIF v_coupon.starts_at > now() THEN
      v_coupon_error := 'This coupon is not valid yet.';
    ELSIF v_coupon.expires_at IS NOT NULL AND v_coupon.expires_at <= now() THEN
      v_coupon_error := 'This coupon has expired.';
    ELSIF v_coupon.usage_limit IS NOT NULL AND v_coupon.times_used >= v_coupon.usage_limit THEN
      v_coupon_error := 'This coupon has reached its usage limit.';
    ELSIF v_item_subtotal < v_coupon.min_order_paise THEN
      v_coupon_error := 'Minimum order of ₹' || to_char(v_coupon.min_order_paise / 100.0, 'FM999999990.00') || ' required for this coupon.';
    ELSE
      -- Calculate eligible subtotal for coupon scope
      IF (v_coupon.applies_to = 'categories' OR cardinality(v_coupon.category_ids) > 0 OR v_coupon.category_id IS NOT NULL)
         AND v_coupon.applies_to <> 'products' AND (v_coupon.product_ids IS NULL OR cardinality(v_coupon.product_ids) = 0) THEN
        
        SELECT COALESCE(sum((elem->>'line_total_paise')::bigint), 0) INTO v_category_subtotal
        FROM jsonb_array_elements(v_lines) elem
        WHERE (elem->>'available')::boolean = true
          AND (
            (v_coupon.category_ids IS NOT NULL AND cardinality(v_coupon.category_ids) > 0 AND (elem->>'category_id')::uuid = ANY(v_coupon.category_ids))
            OR (v_coupon.category_id IS NOT NULL AND (elem->>'category_id')::uuid = v_coupon.category_id)
          );

        IF v_category_subtotal <= 0 THEN
          v_coupon_error := 'No eligible items from the selected coupon categories found in cart.';
        END IF;

      ELSIF v_coupon.applies_to = 'products' OR (v_coupon.product_ids IS NOT NULL AND cardinality(v_coupon.product_ids) > 0) THEN
        
        SELECT COALESCE(sum((elem->>'line_total_paise')::bigint), 0) INTO v_category_subtotal
        FROM jsonb_array_elements(v_lines) elem
        WHERE (elem->>'available')::boolean = true
          AND (elem->>'product_id')::uuid = ANY(v_coupon.product_ids);

        IF v_category_subtotal <= 0 THEN
          v_coupon_error := 'No eligible items from the selected coupon products found in cart.';
        END IF;

      ELSE
        v_category_subtotal := v_item_subtotal;
      END IF;

      IF v_coupon_error IS NULL THEN
        IF v_coupon.discount_type = 'percent_off' THEN
          v_discount_paise := (v_category_subtotal * v_coupon.discount_value) / 100;
          IF v_coupon.max_discount_paise IS NOT NULL AND v_discount_paise > v_coupon.max_discount_paise THEN
            v_discount_paise := v_coupon.max_discount_paise;
          END IF;
          v_coupon_applied := true;

        ELSIF v_coupon.discount_type = 'rupees_off' THEN
          v_discount_paise := LEAST(v_coupon.discount_value, v_category_subtotal);
          v_coupon_applied := true;

        ELSIF v_coupon.discount_type = 'free_delivery' THEN
          IF v_original_delivery_fee_paise <= 0 THEN
            v_coupon_error := 'Free delivery already applies to this order.';
          ELSE
            -- Free delivery: Just make the delivery free (0 paise), no discount required
            v_discount_paise := 0;
            v_delivery_fee_paise := 0;
            v_coupon_applied := true;
          END IF;
        END IF;
      END IF;
    END IF;
  END IF;

  -- Ensure discount doesn't exceed subtotal
  IF v_discount_paise > v_item_subtotal THEN
    v_discount_paise := v_item_subtotal;
  END IF;

  -- Tax calculation
  IF v_settings.tax_mode = 'exclusive' AND v_settings.tax_rate_bps > 0 THEN
    v_tax_paise := ((v_item_subtotal - v_discount_paise + v_delivery_fee_paise) * v_settings.tax_rate_bps) / 10000;
  ELSIF v_settings.tax_mode = 'inclusive' AND v_settings.tax_rate_bps > 0 THEN
    v_tax_paise := 0;
  END IF;

  v_grand_total := GREATEST(0, v_item_subtotal - v_discount_paise + v_delivery_fee_paise + v_tax_paise);

  RETURN jsonb_build_object(
    'lines', v_lines,
    'item_subtotal_paise', v_item_subtotal,
    'discount_paise', v_discount_paise,
    'delivery_fee_paise', v_delivery_fee_paise,
    'fee_rule_note', v_fee_info.rule_note,
    'tax_paise', v_tax_paise,
    'grand_total_paise', v_grand_total,
    'below_charge_threshold', v_fee_info.below_charge_threshold,
    'amount_to_free_delivery', v_fee_info.amount_to_free_delivery,
    'coupon_code', CASE WHEN v_coupon_applied THEN v_clean_code ELSE NULL END,
    'coupon_applied', v_coupon_applied,
    'coupon_error', v_coupon_error
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb, text) TO authenticated, anon;

-- 3. Create app_releases table
CREATE TABLE IF NOT EXISTS public.app_releases (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  audience text NOT NULL DEFAULT 'customer' CHECK (audience IN ('customer', 'staff')),
  version_code integer NOT NULL CHECK (version_code > 0),
  version_name text NOT NULL CHECK (char_length(btrim(version_name)) > 0),
  min_version_code integer NOT NULL DEFAULT 1 CHECK (min_version_code > 0),
  apk_url text NOT NULL CHECK (char_length(btrim(apk_url)) > 0),
  release_notes text NOT NULL DEFAULT '',
  force_update boolean NOT NULL DEFAULT false,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_app_releases_audience_active
  ON public.app_releases (audience, is_active, version_code DESC);

ALTER TABLE public.app_releases ENABLE ROW LEVEL SECURITY;

-- Anonymous and authenticated users can view active releases
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'app_releases' AND policyname = 'Anyone can view active app releases'
  ) THEN
    CREATE POLICY "Anyone can view active app releases"
      ON public.app_releases
      FOR SELECT
      TO anon, authenticated
      USING (is_active = true);
  END IF;
END $$;

-- Staff and managers can view all app releases
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'app_releases' AND policyname = 'Staff can view all app releases'
  ) THEN
    CREATE POLICY "Staff can view all app releases"
      ON public.app_releases
      FOR SELECT
      TO authenticated
      USING (
        EXISTS (
          SELECT 1 FROM public.profiles
          WHERE profiles.id = auth.uid()
            AND profiles.role IN ('staff', 'manager')
        )
      );
  END IF;
END $$;

-- Managers can insert/update/delete releases
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'app_releases' AND policyname = 'Managers can manage app releases'
  ) THEN
    CREATE POLICY "Managers can manage app releases"
      ON public.app_releases
      FOR ALL
      TO authenticated
      USING (
        EXISTS (
          SELECT 1 FROM public.profiles
          WHERE profiles.id = auth.uid()
            AND profiles.role = 'manager'
        )
      )
      WITH CHECK (
        EXISTS (
          SELECT 1 FROM public.profiles
          WHERE profiles.id = auth.uid()
            AND profiles.role = 'manager'
        )
      );
  END IF;
END $$;

GRANT SELECT ON public.app_releases TO anon, authenticated;
GRANT ALL ON public.app_releases TO authenticated;

-- Helper function to fetch the latest release for an audience
CREATE OR REPLACE FUNCTION public.get_latest_app_release(p_audience text DEFAULT 'customer')
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_release record;
BEGIN
  SELECT * INTO v_release
  FROM public.app_releases
  WHERE audience = p_audience AND is_active = true
  ORDER BY version_code DESC, created_at DESC
  LIMIT 1;

  IF NOT FOUND THEN
    RETURN NULL;
  END IF;

  RETURN jsonb_build_object(
    'id', v_release.id,
    'audience', v_release.audience,
    'version_code', v_release.version_code,
    'version_name', v_release.version_name,
    'min_version_code', v_release.min_version_code,
    'apk_url', v_release.apk_url,
    'release_notes', v_release.release_notes,
    'force_update', v_release.force_update,
    'created_at', v_release.created_at
  );
END;
$$;

-- Only the text-argument signature exists: the function above is declared with a
-- DEFAULT on p_coupon_code, so GRANT ... ON FUNCTION public.get_latest_app_release()
-- would fail with 42883. Callers may still omit the argument.
GRANT EXECUTE ON FUNCTION public.get_latest_app_release(text) TO anon, authenticated;

-- 4. Storage bucket for app APKs
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'app-releases',
  'app-releases',
  true,
  157286400, -- 150MB limit
  ARRAY[
    'application/vnd.android.package-archive',
    'application/octet-stream',
    'application/x-zip-compressed'
  ]::text[]
)
ON CONFLICT (id) DO UPDATE SET
  public = true,
  file_size_limit = 157286400,
  allowed_mime_types = ARRAY[
    'application/vnd.android.package-archive',
    'application/octet-stream',
    'application/x-zip-compressed'
  ]::text[];

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'objects' AND policyname = 'Public read for app-releases'
  ) THEN
    CREATE POLICY "Public read for app-releases"
      ON storage.objects
      FOR SELECT
      TO anon, authenticated
      USING (bucket_id = 'app-releases');
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'objects' AND policyname = 'Manager upload to app-releases'
  ) THEN
    CREATE POLICY "Manager upload to app-releases"
      ON storage.objects
      FOR INSERT
      TO authenticated
      WITH CHECK (
        bucket_id = 'app-releases'
        AND EXISTS (
          SELECT 1 FROM public.profiles
          WHERE profiles.id = auth.uid()
            AND profiles.role = 'manager'
        )
      );
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'objects' AND policyname = 'Manager delete from app-releases'
  ) THEN
    CREATE POLICY "Manager delete from app-releases"
      ON storage.objects
      FOR DELETE
      TO authenticated
      USING (
        bucket_id = 'app-releases'
        AND EXISTS (
          SELECT 1 FROM public.profiles
          WHERE profiles.id = auth.uid()
            AND profiles.role = 'manager'
        )
      );
  END IF;
END $$;

-- PostgREST caches the RPC catalogue. Without this, a client can keep getting the
-- old "function is not unique" ambiguity for quote_cart until the cache expires.
NOTIFY pgrst, 'reload schema';

COMMIT;
