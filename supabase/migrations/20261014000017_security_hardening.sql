-- Security hardening: close the public RLS hole, restore canonical policies,
-- lock admin RPCs, protect catalogue views, and add the audited settings RPC.
--
-- Context: supabase/fix_rls_catalogue.sql (run manually on the live project)
-- created FOR ALL TO public USING (true) policies on identity, catalogue,
-- orders and settings. That script's replacement below is intentionally safe:
-- customers/riders get owner-scoped policies, anon gets read-only published
-- catalogue, and every write stays manager/staff scoped.

-- ---------------------------------------------------------------------------
-- 1) Remove the permissive policies created by fix_rls_catalogue.sql
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS "profiles_all_public" ON public.profiles;
DROP POLICY IF EXISTS "categories_read_public" ON public.categories;
DROP POLICY IF EXISTS "categories_write_public" ON public.categories;
DROP POLICY IF EXISTS "products_read_public" ON public.products;
DROP POLICY IF EXISTS "products_write_public" ON public.products;
DROP POLICY IF EXISTS "store_settings_read_public" ON public.store_settings;
DROP POLICY IF EXISTS "store_settings_write_public" ON public.store_settings;
DROP POLICY IF EXISTS "addresses_all_public" ON public.addresses;
DROP POLICY IF EXISTS "orders_all_public" ON public.orders;
DROP POLICY IF EXISTS "order_items_all_public" ON public.order_items;

-- The staff UPDATE policy on products allows changing price/stock directly.
-- Stock changes must go through adjust_stock so movements stay auditable.
DROP POLICY IF EXISTS "products_stock_adjust_staff" ON public.products;

-- Direct order UPDATE bypasses transition_order (state machine, stock restore,
-- timestamps, audit). All status changes must use the RPCs.
DROP POLICY IF EXISTS "orders_update_status_staff" ON public.orders;

-- ---------------------------------------------------------------------------
-- 2) Restore canonical owner/staff policies (idempotent)
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS "profiles_read_own" ON public.profiles;
CREATE POLICY "profiles_read_own" ON public.profiles
  FOR SELECT TO authenticated USING (id = auth.uid());

DROP POLICY IF EXISTS "profiles_read_staff" ON public.profiles;
CREATE POLICY "profiles_read_staff" ON public.profiles
  FOR SELECT TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'));

DROP POLICY IF EXISTS "profiles_update_role_active" ON public.profiles;
CREATE POLICY "profiles_update_role_active" ON public.profiles
  FOR UPDATE TO authenticated
  USING (public.current_role() = 'manager'::public.user_role)
  WITH CHECK (public.current_role() = 'manager'::public.user_role);

DROP POLICY IF EXISTS "store_settings_read" ON public.store_settings;
CREATE POLICY "store_settings_read" ON public.store_settings
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "store_settings_write" ON public.store_settings;
CREATE POLICY "store_settings_write" ON public.store_settings
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::public.user_role)
  WITH CHECK (public.current_role() = 'manager'::public.user_role);

DROP POLICY IF EXISTS "service_areas_read_active" ON public.service_areas;
CREATE POLICY "service_areas_read_active" ON public.service_areas
  FOR SELECT TO authenticated USING (active = true);

DROP POLICY IF EXISTS "service_areas_write" ON public.service_areas;
CREATE POLICY "service_areas_write" ON public.service_areas
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::public.user_role)
  WITH CHECK (public.current_role() = 'manager'::public.user_role);

DROP POLICY IF EXISTS "categories_read_active" ON public.categories;
CREATE POLICY "categories_read_active" ON public.categories
  FOR SELECT TO authenticated USING (active = true);

DROP POLICY IF EXISTS "categories_write" ON public.categories;
CREATE POLICY "categories_write" ON public.categories
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::public.user_role)
  WITH CHECK (public.current_role() = 'manager'::public.user_role);

DROP POLICY IF EXISTS "products_read_published" ON public.products;
CREATE POLICY "products_read_published" ON public.products
  FOR SELECT TO authenticated USING (active = true AND available = true);

DROP POLICY IF EXISTS "products_write_manager" ON public.products;
CREATE POLICY "products_write_manager" ON public.products
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::public.user_role)
  WITH CHECK (public.current_role() = 'manager'::public.user_role);

DROP POLICY IF EXISTS "addresses_own_crud" ON public.addresses;
CREATE POLICY "addresses_own_crud" ON public.addresses
  FOR ALL TO authenticated
  USING (customer_id = auth.uid())
  WITH CHECK (customer_id = auth.uid());

DROP POLICY IF EXISTS "addresses_staff_read" ON public.addresses;
CREATE POLICY "addresses_staff_read" ON public.addresses
  FOR SELECT TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'));

DROP POLICY IF EXISTS "orders_read_own" ON public.orders;
CREATE POLICY "orders_read_own" ON public.orders
  FOR SELECT TO authenticated USING (customer_id = auth.uid());

DROP POLICY IF EXISTS "orders_read_staff" ON public.orders;
CREATE POLICY "orders_read_staff" ON public.orders
  FOR SELECT TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'));

DROP POLICY IF EXISTS "order_items_read_own" ON public.order_items;
CREATE POLICY "order_items_read_own" ON public.order_items
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.orders o
    WHERE o.id = order_items.order_id AND o.customer_id = auth.uid()
  ));

DROP POLICY IF EXISTS "order_items_read_staff" ON public.order_items;
CREATE POLICY "order_items_read_staff" ON public.order_items
  FOR SELECT TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'));

DROP POLICY IF EXISTS "order_events_read_own" ON public.order_status_events;
CREATE POLICY "order_events_read_own" ON public.order_status_events
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.orders o
    WHERE o.id = order_status_events.order_id AND o.customer_id = auth.uid()
  ));

DROP POLICY IF EXISTS "order_events_read_staff" ON public.order_status_events;
CREATE POLICY "order_events_read_staff" ON public.order_status_events
  FOR SELECT TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'));

DROP POLICY IF EXISTS "order_assignments_read_staff" ON public.order_assignments;
CREATE POLICY "order_assignments_read_staff" ON public.order_assignments
  FOR SELECT TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'));

-- The security_invoker rider_orders view reads orders/order_items as the
-- rider, so the underlying tables need scoped rider policies too.
DROP POLICY IF EXISTS "orders_read_assigned_delivery" ON public.orders;
CREATE POLICY "orders_read_assigned_delivery" ON public.orders
  FOR SELECT TO authenticated
  USING (
    public.current_role() = 'delivery'::public.user_role
    AND EXISTS (
      SELECT 1 FROM public.order_assignments oa
      WHERE oa.order_id = orders.id
        AND oa.delivery_user_id = auth.uid()
        AND oa.unassigned_at IS NULL
    )
  );

DROP POLICY IF EXISTS "order_items_read_assigned_delivery" ON public.order_items;
CREATE POLICY "order_items_read_assigned_delivery" ON public.order_items
  FOR SELECT TO authenticated
  USING (
    public.current_role() = 'delivery'::public.user_role
    AND EXISTS (
      SELECT 1
      FROM public.order_assignments oa
      WHERE oa.order_id = order_items.order_id
        AND oa.delivery_user_id = auth.uid()
        AND oa.unassigned_at IS NULL
    )
  );

DROP POLICY IF EXISTS "order_assignments_read_delivery" ON public.order_assignments;
CREATE POLICY "order_assignments_read_delivery" ON public.order_assignments
  FOR SELECT TO authenticated
  USING (public.current_role() = 'delivery'::public.user_role AND delivery_user_id = auth.uid());

DROP POLICY IF EXISTS "order_assignments_write_staff" ON public.order_assignments;
CREATE POLICY "order_assignments_write_staff" ON public.order_assignments
  FOR ALL TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'))
  WITH CHECK (public.current_role()::text IN ('staff', 'manager'));

DROP POLICY IF EXISTS "order_transitions_read" ON public.order_transitions;
CREATE POLICY "order_transitions_read" ON public.order_transitions
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "inventory_movements_read" ON public.inventory_movements;
CREATE POLICY "inventory_movements_read" ON public.inventory_movements
  FOR SELECT TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'));

-- ---------------------------------------------------------------------------
-- 3) Anonymous storefront read access (published rows only, no PII)
-- ---------------------------------------------------------------------------
DROP POLICY IF EXISTS "categories_read_anon" ON public.categories;
CREATE POLICY "categories_read_anon" ON public.categories
  FOR SELECT TO anon USING (active = true);

DROP POLICY IF EXISTS "products_read_anon" ON public.products;
CREATE POLICY "products_read_anon" ON public.products
  FOR SELECT TO anon USING (active = true AND available = true);

DROP POLICY IF EXISTS "store_settings_read_anon" ON public.store_settings;
CREATE POLICY "store_settings_read_anon" ON public.store_settings
  FOR SELECT TO anon USING (true);

DROP POLICY IF EXISTS "service_areas_read_anon" ON public.service_areas;
CREATE POLICY "service_areas_read_anon" ON public.service_areas
  FOR SELECT TO anon USING (active = true);

-- ---------------------------------------------------------------------------
-- 4) Views must respect the caller's RLS (they are owned by postgres)
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'catalog_products' AND c.relkind = 'v'
  ) THEN
    ALTER VIEW public.catalog_products SET (security_invoker = true);
  END IF;
  IF EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'catalog_categories' AND c.relkind = 'v'
  ) THEN
    ALTER VIEW public.catalog_categories SET (security_invoker = true);
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 5) current_role() must ignore deactivated staff/rider accounts
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.current_role()
RETURNS public.user_role
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $sql$
  SELECT COALESCE(
    (SELECT role FROM public.profiles WHERE id = auth.uid() AND is_active),
    'customer'::public.user_role
  );
$sql$;

-- ---------------------------------------------------------------------------
-- 6) Lock down sensitive RPCs (default PUBLIC execute must be revoked)
-- ---------------------------------------------------------------------------
REVOKE EXECUTE ON FUNCTION public.place_order(jsonb, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.adjust_stock(uuid, bigint, public.inventory_reason, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.get_low_stock_products() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.check_stock_reconciliation() FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.get_sales_report(timestamptz, timestamptz, public.order_status) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.get_cod_reconciliation(timestamptz, timestamptz) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.get_audit_logs(timestamptz, timestamptz, text, uuid, int, int, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.transition_order(uuid, public.order_status, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.assign_order(uuid, uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.rider_update_status(uuid, public.order_status, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.set_rider_active(uuid, boolean) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.remove_order_item(uuid, uuid, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.create_rider(text, text, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.reset_rider_password(uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.get_rider_orders() FROM PUBLIC, anon;

-- Quotes are read-only and server-priced; anonymous carts still need them.
REVOKE EXECUTE ON FUNCTION public.quote_cart(jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb) TO anon, authenticated;
REVOKE EXECUTE ON FUNCTION public.compute_delivery_fee(bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.compute_delivery_fee(bigint) TO anon, authenticated;

-- ---------------------------------------------------------------------------
-- 7) save_settings(patch) — manager only, validated, audited by trigger
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.save_settings(p_patch jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_settings public.store_settings%ROWTYPE;
  v_store_open boolean;
  v_delivery_fee bigint;
  v_charge_threshold bigint;
  v_free_threshold bigint;
  v_mid_fee bigint;
  v_max_order bigint;
  v_tax_rate integer;
  v_online boolean;
BEGIN
  IF public.current_role() <> 'manager'::public.user_role THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Manager required');
  END IF;

  IF p_patch IS NULL OR jsonb_typeof(p_patch) <> 'object' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Patch must be a JSON object');
  END IF;

  SELECT * INTO v_settings FROM public.store_settings WHERE id = true FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Store settings row not found');
  END IF;

  BEGIN
    v_store_open := COALESCE((p_patch->>'store_open')::boolean, v_settings.store_open);
    v_delivery_fee := COALESCE((p_patch->>'delivery_fee_paise')::bigint, v_settings.delivery_fee_paise);
    v_charge_threshold := COALESCE((p_patch->>'delivery_charge_threshold_paise')::bigint, v_settings.delivery_charge_threshold_paise);
    v_free_threshold := CASE
      WHEN p_patch ? 'free_delivery_threshold_paise'
        THEN NULLIF(p_patch->>'free_delivery_threshold_paise', '')::bigint
      ELSE v_settings.free_delivery_threshold_paise
    END;
    v_mid_fee := CASE
      WHEN p_patch ? 'mid_tier_fee_paise'
        THEN NULLIF(p_patch->>'mid_tier_fee_paise', '')::bigint
      ELSE v_settings.mid_tier_fee_paise
    END;
    v_max_order := CASE
      WHEN p_patch ? 'max_order_paise'
        THEN NULLIF(p_patch->>'max_order_paise', '')::bigint
      ELSE v_settings.max_order_paise
    END;
    v_tax_rate := COALESCE((p_patch->>'tax_rate_bps')::integer, v_settings.tax_rate_bps);
    v_online := COALESCE((p_patch->>'online_payment_enabled')::boolean, v_settings.online_payment_enabled);
  EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Invalid setting value type');
  END;

  IF v_online THEN
    RETURN jsonb_build_object(
      'error', 'PAYMENT_GATE_CLOSED',
      'message', 'Online payments are disabled until the payment gate (M11) passes'
    );
  END IF;
  IF v_delivery_fee < 0 OR v_charge_threshold < 0 OR v_tax_rate < 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Fees, thresholds and tax rate must be non-negative');
  END IF;
  IF v_free_threshold IS NOT NULL AND v_free_threshold < v_charge_threshold THEN
    RETURN jsonb_build_object(
      'error', 'VALIDATION_FAILED',
      'message', 'Free-delivery threshold must be empty or greater than/equal to the delivery-charge threshold'
    );
  END IF;
  IF v_mid_fee IS NOT NULL AND v_mid_fee < 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Mid-tier fee must be non-negative');
  END IF;
  IF v_max_order IS NOT NULL AND v_max_order < 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Maximum order amount must be non-negative');
  END IF;
  IF v_tax_rate > 10000 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Tax rate cannot exceed 100%');
  END IF;

  UPDATE public.store_settings
  SET store_open = v_store_open,
      timezone = COALESCE(NULLIF(p_patch->>'timezone', ''), timezone),
      weekly_hours = CASE WHEN p_patch ? 'weekly_hours' THEN p_patch->'weekly_hours' ELSE weekly_hours END,
      contact_phone = COALESCE(p_patch->>'contact_phone', contact_phone),
      estimated_window_text = CASE WHEN p_patch ? 'estimated_window_text' THEN p_patch->>'estimated_window_text' ELSE estimated_window_text END,
      delivery_fee_paise = v_delivery_fee,
      delivery_charge_threshold_paise = v_charge_threshold,
      free_delivery_threshold_paise = v_free_threshold,
      mid_tier_fee_paise = v_mid_fee,
      max_order_paise = v_max_order,
      cancellation_policy_text = CASE WHEN p_patch ? 'cancellation_policy_text' THEN p_patch->>'cancellation_policy_text' ELSE cancellation_policy_text END,
      tax_mode = COALESCE((p_patch->>'tax_mode')::public.tax_mode, tax_mode),
      tax_rate_bps = v_tax_rate,
      online_payment_enabled = false,
      pos_price_override_enabled = CASE WHEN p_patch ? 'pos_price_override_enabled' THEN (p_patch->>'pos_price_override_enabled')::boolean ELSE pos_price_override_enabled END
  WHERE id = true
  RETURNING * INTO v_settings;

  RETURN jsonb_build_object('success', true, 'settings', to_jsonb(v_settings));
EXCEPTION
  WHEN check_violation OR invalid_text_representation THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Settings violate a validation constraint');
END;
$$;

REVOKE EXECUTE ON FUNCTION public.save_settings(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.save_settings(jsonb) TO authenticated;

-- ---------------------------------------------------------------------------
-- 8) complete_password_change() — lets the signed-in user clear the
--    must_change_password flag after updating their password via Supabase Auth.
--    Profiles are not self-updatable under RLS, so this needs a definer RPC.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.complete_password_change()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := auth.uid();
BEGIN
  IF v_uid IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Sign in required');
  END IF;

  UPDATE public.profiles
  SET must_change_password = false
  WHERE id = v_uid AND is_active;

  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Active profile required');
  END IF;

  RETURN jsonb_build_object('success', true);
END;
$$;

REVOKE EXECUTE ON FUNCTION public.complete_password_change() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.complete_password_change() TO authenticated;

-- ---------------------------------------------------------------------------
-- 9) Stock is only writable through SECURITY DEFINER functions that also
--    record an inventory movement. Table-level UPDATE/INSERT is replaced by a
--    column allow-list that excludes stock_qty.
-- ---------------------------------------------------------------------------
REVOKE INSERT, UPDATE ON public.products FROM anon, authenticated;
GRANT INSERT (
  sku, name, description, category_id, image_path, quantity_type, price_paise,
  price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, max_qty,
  low_stock_threshold, active, available
) ON public.products TO authenticated;
GRANT UPDATE (
  sku, name, description, category_id, image_path, quantity_type, price_paise,
  price_basis_qty, unit_label, pack_size_label, step_qty, min_qty, max_qty,
  low_stock_threshold, active, available
) ON public.products TO authenticated;
