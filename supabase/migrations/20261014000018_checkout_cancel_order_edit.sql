-- ===========================================================================
-- Migration 20261014000018: Checkout hardening, customer cancellation,
-- unavailable-item editing, and safe new-user provisioning
-- ===========================================================================
-- Fixes regressions introduced by 20261014000017_security_rls_fix.sql:
--   * new auth users were provisioned as managers (privilege escalation and
--     "Customer access required" at checkout)
--   * place_order lost stock checks, inventory movements, address/store
--     validation, and idempotency response persistence
-- Adds:
--   * cancel_my_order() for customers (pending_review only, per D policy)
--   * shared, exactly-once stock restoration helper
--   * safer remove_order_item() that recomputes the bill from active lines
--     and cancels instead of leaving an emptied order open
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1) New auth users are customers. Managers are provisioned manually
--    (docs/DECISIONS.md D-ROLES). Never trust raw_user_meta_data for roles.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  INSERT INTO public.profiles (id, role, display_name, phone, is_active, must_change_password)
  VALUES (
    NEW.id,
    'customer'::public.user_role,
    COALESCE(
      NULLIF(NEW.raw_user_meta_data->>'display_name', ''),
      NULLIF(split_part(COALESCE(NEW.email, ''), '@', 1), ''),
      'Customer'
    ),
    NULLIF(to_jsonb(NEW)->>'phone', ''),
    true,
    false
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Status changes must go through transition_order()/cancel_my_order() so the
-- state machine, stock restoration, payment state, and audit trail run.
-- 017_security_rls_fix accidentally allowed direct staff UPDATEs on orders.
DROP POLICY IF EXISTS "orders_update_status_staff" ON public.orders;

-- ---------------------------------------------------------------------------
-- 2) Shared stock restoration helper. Restores the remaining net quantity
--    once per order, accounting for items already removed individually.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.restore_order_stock(
  p_order_id uuid,
  p_reason public.inventory_reason,
  p_note text
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_already_restored boolean;
BEGIN
  IF p_reason NOT IN ('order_cancelled'::public.inventory_reason,
                      'order_rejected'::public.inventory_reason) THEN
    RAISE EXCEPTION 'Invalid stock restoration reason: %', p_reason;
  END IF;

  SELECT EXISTS (
    SELECT 1
    FROM public.inventory_movements
    WHERE reference_type = 'order'
      AND reference_id = p_order_id
      AND reason IN ('order_cancelled'::public.inventory_reason,
                     'order_rejected'::public.inventory_reason)
  ) INTO v_already_restored;

  IF v_already_restored THEN
    RETURN false;
  END IF;

  WITH movements AS (
    SELECT product_id, SUM(delta_qty) AS net_delta
    FROM public.inventory_movements
    WHERE reference_type = 'order'
      AND reference_id = p_order_id
      AND reason IN ('order_placed'::public.inventory_reason,
                     'item_removed'::public.inventory_reason)
    GROUP BY product_id
    HAVING SUM(delta_qty) < 0
  ),
  restored AS (
    UPDATE public.products p
    SET stock_qty = p.stock_qty - m.net_delta
    FROM movements m
    WHERE p.id = m.product_id
    RETURNING p.id AS product_id, m.net_delta
  )
  INSERT INTO public.inventory_movements (
    product_id, delta_qty, reason, reference_type, reference_id, actor_id, note
  )
  SELECT product_id, -net_delta, p_reason, 'order', p_order_id, auth.uid(), p_note
  FROM restored;

  RETURN true;
END;
$$;

REVOKE ALL ON FUNCTION public.restore_order_stock(uuid, public.inventory_reason, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.restore_order_stock(uuid, public.inventory_reason, text) FROM anon, authenticated;

-- ---------------------------------------------------------------------------
-- 3) Hardened place_order: authoritative client identity, server pricing,
--    stock locking, movements, validation, and durable idempotency.
--    Active staff/manager accounts may also order for themselves; customer_id
--    always comes from auth.uid(), never the payload.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.place_order(
  p_payload jsonb,
  p_idempotency_key text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_profile public.profiles%ROWTYPE;
  v_items jsonb;
  v_address jsonb;
  v_customer_note text;
  v_payment_text text;
  v_payment_method public.payment_method;
  v_settings public.store_settings%ROWTYPE;
  v_quote jsonb;
  v_item jsonb;
  v_item2 jsonb;
  v_product public.products%ROWTYPE;
  v_qty_text text;
  v_qty_base bigint;
  v_unit_price_paise bigint;
  v_line_total_paise bigint;
  v_display_qty text;
  v_new_stock bigint;
  v_order_id uuid;
  v_public_order_number text;
  v_address_snapshot jsonb;
  v_fee_breakdown jsonb;
  v_payment_status public.payment_status := 'unpaid';
  v_cached_response jsonb;
  v_cached_hash text;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  SELECT * INTO v_profile FROM public.profiles WHERE id = v_user_id;
  IF NOT FOUND OR NOT v_profile.is_active THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Active account required');
  END IF;

  IF NULLIF(btrim(COALESCE(p_idempotency_key, '')), '') IS NULL THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Idempotency key required');
  END IF;

  -- Serialize retries for the same customer + idempotency key.
  PERFORM pg_advisory_xact_lock(
    hashtextextended(v_user_id::text || ':' || p_idempotency_key, 0)
  );

  SELECT request_hash, response
  INTO v_cached_hash, v_cached_response
  FROM public.idempotency_keys
  WHERE user_id = v_user_id
    AND scope = 'place_order'
    AND key = p_idempotency_key;

  IF v_cached_hash IS NOT NULL THEN
    IF v_cached_hash <> md5(p_payload::text) THEN
      RETURN jsonb_build_object(
        'error', 'IDEMPOTENCY_MISMATCH',
        'message', 'Idempotency key reused with different payload'
      );
    END IF;
    IF v_cached_response IS NOT NULL THEN
      RETURN v_cached_response;
    END IF;
  END IF;

  v_items := p_payload->'items';
  v_address := p_payload->'address';
  v_customer_note := NULLIF(btrim(COALESCE(p_payload->>'customer_note', '')), '');
  v_payment_text := COALESCE(NULLIF(p_payload->>'payment_method', ''), 'cod');

  IF v_items IS NULL OR jsonb_typeof(v_items) <> 'array' OR jsonb_array_length(v_items) = 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Cart is empty');
  END IF;

  IF (SELECT count(DISTINCT value->>'product_id') FROM jsonb_array_elements(v_items))
     <> jsonb_array_length(v_items) THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Duplicate products in cart');
  END IF;

  IF v_address IS NULL OR jsonb_typeof(v_address) <> 'object'
     OR NULLIF(btrim(COALESCE(v_address->>'recipient_name', '')), '') IS NULL
     OR NULLIF(btrim(COALESCE(v_address->>'phone', '')), '') IS NULL
     OR NULLIF(btrim(COALESCE(v_address->>'address_lines', '')), '') IS NULL
     OR NULLIF(btrim(COALESCE(v_address->>'postal_code', '')), '') IS NULL THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Complete delivery address required');
  END IF;

  SELECT * INTO v_settings FROM public.store_settings;
  IF NOT FOUND OR NOT v_settings.store_open THEN
    RETURN jsonb_build_object('error', 'STORE_CLOSED', 'message', 'Store is currently closed');
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.service_areas
    WHERE active = true
      AND kind = 'pincode'
      AND lower(value) = lower(v_address->>'postal_code')
  ) THEN
    RETURN jsonb_build_object('error', 'OUT_OF_AREA',
      'message', 'Delivery not available to this PIN code');
  END IF;

  FOR v_item IN SELECT value FROM jsonb_array_elements(v_items)
                ORDER BY (value->>'product_id')::uuid
  LOOP
    IF COALESCE(v_item->>'product_id', '')
       !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
      RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Invalid product identifier');
    END IF;

    SELECT * INTO v_product
    FROM public.products
    WHERE id = (v_item->>'product_id')::uuid
    FOR UPDATE;

    IF NOT FOUND THEN
      RETURN jsonb_build_object('error', 'PRODUCT_UNAVAILABLE', 'message', 'Product not found');
    END IF;
    IF NOT v_product.active OR NOT v_product.available THEN
      RETURN jsonb_build_object('error', 'PRODUCT_UNAVAILABLE',
        'message', v_product.name || ' is not available');
    END IF;

    v_qty_text := COALESCE(v_item->>'qty_base', '');
    IF v_qty_text !~ '^\d+$' THEN
      RETURN jsonb_build_object('error', 'QTY_INVALID',
        'message', 'Quantity must be a positive integer for ' || v_product.name);
    END IF;
    v_qty_base := v_qty_text::bigint;

    IF v_qty_base < v_product.min_qty
       OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base)
       OR (v_product.quantity_type = 'WEIGHTED'
           AND v_qty_base % v_product.step_qty <> 0) THEN
      RETURN jsonb_build_object('error', 'QTY_INVALID',
        'message', 'Invalid quantity for ' || v_product.name);
    END IF;

    IF v_product.stock_qty < v_qty_base THEN
      RETURN jsonb_build_object('error', 'INSUFFICIENT_STOCK',
        'message', v_product.name || ' has only ' || v_product.stock_qty || ' available');
    END IF;
  END LOOP;

  v_quote := public.quote_cart(v_items);

  IF v_settings.max_order_paise IS NOT NULL AND v_settings.max_order_paise > 0
     AND (v_quote->>'grand_total_paise')::bigint > v_settings.max_order_paise THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Order exceeds maximum allowed value');
  END IF;

  IF v_payment_text NOT IN ('cod', 'online') THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Invalid payment method');
  END IF;
  v_payment_method := v_payment_text::public.payment_method;

  IF v_payment_method = 'online' THEN
    IF NOT v_settings.online_payment_enabled THEN
      RETURN jsonb_build_object('error', 'ONLINE_PAYMENT_DISABLED', 'message', 'Online payments not enabled');
    END IF;
    v_payment_status := 'pending';
  END IF;

  v_public_order_number := 'BPS-' || to_char(now(), 'YYMMDD') || '-'
    || lpad(floor(random() * 10000)::text, 4, '0');
  WHILE EXISTS (SELECT 1 FROM public.orders WHERE public_order_number = v_public_order_number) LOOP
    v_public_order_number := 'BPS-' || to_char(now(), 'YYMMDD') || '-'
      || lpad(floor(random() * 10000)::text, 4, '0');
  END LOOP;

  v_address_snapshot := jsonb_build_object(
    'recipient_name', v_address->>'recipient_name',
    'phone', v_address->>'phone',
    'address_lines', v_address->>'address_lines',
    'landmark', v_address->>'landmark',
    'area', v_address->>'area',
    'city', v_address->>'city',
    'state', v_address->>'state',
    'postal_code', v_address->>'postal_code'
  );

  v_fee_breakdown := jsonb_build_object(
    'delivery_charge_threshold_paise', v_settings.delivery_charge_threshold_paise,
    'delivery_fee_paise', v_settings.delivery_fee_paise,
    'free_delivery_threshold_paise', v_settings.free_delivery_threshold_paise,
    'mid_tier_fee_paise', v_settings.mid_tier_fee_paise,
    'eligible_subtotal_paise', v_quote->>'item_subtotal_paise',
    'delivery_fee_applied_paise', v_quote->>'delivery_fee_paise',
    'rule_note', v_quote->>'fee_rule_note'
  );

  INSERT INTO public.orders (
    public_order_number, customer_id, address_snapshot, status, payment_method,
    payment_status, item_subtotal_paise, discount_paise, delivery_fee_paise,
    tax_paise, grand_total_paise, fee_breakdown, customer_note, idempotency_key
  ) VALUES (
    v_public_order_number, v_user_id, v_address_snapshot, 'pending_review', v_payment_method,
    v_payment_status,
    (v_quote->>'item_subtotal_paise')::bigint,
    (v_quote->>'discount_paise')::bigint,
    (v_quote->>'delivery_fee_paise')::bigint,
    (v_quote->>'tax_paise')::bigint,
    (v_quote->>'grand_total_paise')::bigint,
    v_fee_breakdown, v_customer_note, p_idempotency_key
  ) RETURNING id INTO v_order_id;

  FOR v_item2 IN SELECT value FROM jsonb_array_elements(v_items)
  LOOP
    SELECT * INTO v_product
    FROM public.products
    WHERE id = (v_item2->>'product_id')::uuid;

    v_qty_base := (v_item2->>'qty_base')::bigint;
    v_unit_price_paise := v_product.price_paise;
    v_line_total_paise := (2 * v_unit_price_paise * v_qty_base + v_product.price_basis_qty)
                            / (2 * v_product.price_basis_qty);

    IF v_product.quantity_type = 'WEIGHTED' THEN
      IF v_product.price_basis_qty = 1000 THEN
        v_display_qty := to_char(v_qty_base / 1000.0, 'FM999999990.000') || ' kg';
      ELSE
        v_display_qty := v_qty_base || ' g';
      END IF;
    ELSE
      v_display_qty := v_qty_base || ' ' || v_product.unit_label;
    END IF;

    INSERT INTO public.order_items (
      order_id, product_id, sku_snapshot, name_snapshot, quantity_type_snapshot,
      unit_label_snapshot, price_basis_qty_snapshot, qty_base, unit_price_paise, line_total_paise
    ) VALUES (
      v_order_id, v_product.id, v_product.sku, v_product.name, v_product.quantity_type,
      v_product.unit_label, v_product.price_basis_qty, v_qty_base, v_unit_price_paise, v_line_total_paise
    );

    WITH updated AS (
      UPDATE public.products
      SET stock_qty = stock_qty - v_qty_base
      WHERE id = v_product.id
      RETURNING stock_qty
    )
    SELECT stock_qty FROM updated INTO v_new_stock;

    INSERT INTO public.inventory_movements (
      product_id, delta_qty, reason, reference_type, reference_id, actor_id
    ) VALUES (
      v_product.id, -v_qty_base, 'order_placed', 'order', v_order_id, v_user_id
    );
  END LOOP;

  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (v_order_id, NULL, 'pending_review', v_user_id, 'Order placed by customer');

  INSERT INTO public.notification_outbox (order_id, channel, template, status)
  VALUES (v_order_id, 'in_app', 'order_placed', 'pending');

  v_cached_response := jsonb_build_object(
    'order_id', v_order_id,
    'public_order_number', v_public_order_number,
    'breakdown', v_quote
  );

  INSERT INTO public.idempotency_keys (user_id, scope, key, request_hash, response)
  VALUES (v_user_id, 'place_order', p_idempotency_key, md5(p_payload::text), v_cached_response)
  ON CONFLICT (user_id, scope, key)
  DO UPDATE SET request_hash = EXCLUDED.request_hash, response = EXCLUDED.response;

  RETURN v_cached_response;
END;
$$;

REVOKE ALL ON FUNCTION public.place_order(jsonb, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;

-- ---------------------------------------------------------------------------
-- 4) Customer cancellation (pending_review only; see DECISIONS cancellation
--    policy D-CANCEL). Ownership is enforced from auth.uid().
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.cancel_my_order(
  p_order_id uuid,
  p_reason text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_order public.orders%ROWTYPE;
  v_reason text := COALESCE(NULLIF(btrim(p_reason), ''), 'Cancelled by customer');
  v_stock_restored boolean := false;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.profiles WHERE id = v_user_id AND is_active
  ) THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Active account required');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  IF v_order.customer_id IS DISTINCT FROM v_user_id THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'You can only cancel your own order');
  END IF;

  IF v_order.status <> 'pending_review' THEN
    RETURN jsonb_build_object('error', 'INVALID_STATUS',
      'message', 'This order can no longer be cancelled online. Contact the store.');
  END IF;

  UPDATE public.orders
  SET status = 'cancelled',
      payment_status = CASE
        WHEN payment_method = 'online'
             AND payment_status IN ('paid'::public.payment_status,
                                    'pending'::public.payment_status)
          THEN 'refund_pending'::public.payment_status
        ELSE payment_status
      END
  WHERE id = p_order_id;

  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (p_order_id, v_order.status, 'cancelled', v_user_id, v_reason);

  v_stock_restored := public.restore_order_stock(
    p_order_id,
    'order_cancelled'::public.inventory_reason,
    'Customer cancellation: ' || v_reason
  );

  INSERT INTO public.audit_logs (
    actor_id, action, entity_type, entity_id, before, after, request_id
  ) VALUES (
    v_user_id, 'CANCEL_MY_ORDER', 'orders', p_order_id::text,
    jsonb_build_object('status', v_order.status),
    jsonb_build_object('status', 'cancelled', 'reason', v_reason),
    current_setting('request.id', true)
  );

  RETURN jsonb_build_object(
    'success', true,
    'order_id', p_order_id,
    'status', 'cancelled',
    'stock_restored', v_stock_restored
  );
END;
$$;

REVOKE ALL ON FUNCTION public.cancel_my_order(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.cancel_my_order(uuid, text) TO authenticated;

-- ---------------------------------------------------------------------------
-- 5) Staff transitions, now using the shared exactly-once stock restore and
--    requiring an auditable reason for terminal failure states.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.transition_order(
  p_order_id uuid,
  p_to_status public.order_status,
  p_reason text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order public.orders%ROWTYPE;
  v_from_status public.order_status;
  v_allowed boolean;
  v_restore_stock boolean := false;
  v_reason text := NULLIF(btrim(COALESCE(p_reason, '')), '');
BEGIN
  IF public.current_role() NOT IN ('staff', 'manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  IF p_to_status IN ('cancelled'::public.order_status,
                     'rejected'::public.order_status,
                     'delivery_failed'::public.order_status)
     AND v_reason IS NULL THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED',
      'message', 'A reason is required for this status change');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  v_from_status := v_order.status;

  SELECT EXISTS (
    SELECT 1 FROM public.order_transitions
    WHERE from_status = v_from_status
      AND to_status = p_to_status
      AND allowed_roles @> ARRAY[public.current_role()]::public.user_role[]
  ) INTO v_allowed;

  IF NOT v_allowed THEN
    RETURN jsonb_build_object('error', 'INVALID_TRANSITION',
      'message', 'Invalid status transition: ' || v_from_status || ' -> ' || p_to_status);
  END IF;

  UPDATE public.orders
  SET status = p_to_status,
      accepted_at = CASE WHEN p_to_status = 'accepted' THEN now() ELSE accepted_at END,
      delivered_at = CASE WHEN p_to_status = 'delivered' THEN now() ELSE delivered_at END,
      cod_collected_paise = CASE
        WHEN p_to_status = 'delivered' AND v_order.payment_method = 'cod' THEN v_order.grand_total_paise
        ELSE cod_collected_paise
      END,
      payment_status = CASE
        WHEN p_to_status = 'delivered' AND v_order.payment_method = 'cod' THEN 'paid'::public.payment_status
        WHEN p_to_status IN ('cancelled'::public.order_status, 'rejected'::public.order_status)
             AND v_order.payment_method = 'online'
             AND v_order.payment_status IN ('paid'::public.payment_status, 'pending'::public.payment_status)
          THEN 'refund_pending'::public.payment_status
        ELSE payment_status
      END
  WHERE id = p_order_id;

  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (p_order_id, v_from_status, p_to_status, auth.uid(), v_reason);

  IF p_to_status IN ('rejected'::public.order_status, 'cancelled'::public.order_status)
     AND v_from_status IN ('pending_review'::public.order_status,
                           'accepted'::public.order_status,
                           'preparing'::public.order_status,
                           'ready_for_dispatch'::public.order_status) THEN
    v_restore_stock := public.restore_order_stock(
      p_order_id,
      CASE WHEN p_to_status = 'rejected'
           THEN 'order_rejected'::public.inventory_reason
           ELSE 'order_cancelled'::public.inventory_reason END,
      'Auto-restore on ' || p_to_status || ': ' || COALESCE(v_reason, '')
    );
  END IF;

  RETURN jsonb_build_object(
    'order_id', p_order_id,
    'from_status', v_from_status,
    'to_status', p_to_status,
    'stock_restored', v_restore_stock,
    'ok', true
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.transition_order(uuid, public.order_status, text) TO authenticated;

-- ---------------------------------------------------------------------------
-- 6) Unavailable-item removal: recompute from active lines, cancel instead
--    of leaving an emptied order open, restore stock, and return the bill.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.remove_order_item(
  p_order_id uuid,
  p_item_id uuid,
  p_reason text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order public.orders%ROWTYPE;
  v_item public.order_items%ROWTYPE;
  v_reason text := NULLIF(btrim(COALESCE(p_reason, '')), '');
  v_active_count integer;
  v_new_subtotal bigint;
  v_new_fee bigint;
  v_new_tax bigint;
  v_discount bigint;
  v_new_grand bigint;
  v_settings public.store_settings%ROWTYPE;
  v_fee_info record;
  v_new_payment_status public.payment_status;
BEGIN
  IF public.current_role() NOT IN ('staff', 'manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  IF v_reason IS NULL THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'A reason is required');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  IF v_order.status NOT IN ('pending_review', 'accepted', 'preparing') THEN
    RETURN jsonb_build_object('error', 'INVALID_STATUS',
      'message', 'Item removal is only allowed before dispatch');
  END IF;

  SELECT * INTO v_item
  FROM public.order_items
  WHERE id = p_item_id AND order_id = p_order_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order item not found');
  END IF;

  IF v_item.removed_at IS NOT NULL THEN
    RETURN jsonb_build_object('error', 'ALREADY_REMOVED', 'message', 'Item already removed');
  END IF;

  SELECT count(*) INTO v_active_count
  FROM public.order_items
  WHERE order_id = p_order_id AND removed_at IS NULL;

  UPDATE public.order_items
  SET removed_at = now(), removed_reason = v_reason
  WHERE id = p_item_id;

  IF v_item.product_id IS NOT NULL THEN
    UPDATE public.products
    SET stock_qty = stock_qty + v_item.qty_base
    WHERE id = v_item.product_id;

    INSERT INTO public.inventory_movements (
      product_id, delta_qty, reason, reference_type, reference_id, actor_id, note
    ) VALUES (
      v_item.product_id, v_item.qty_base, 'item_removed', 'order', p_order_id,
      auth.uid(), v_reason
    );
  END IF;

  -- Removing the final item leaves nothing to fulfil: cancel the order and
  -- keep an auditable status event instead of leaving a zero-item order open.
  IF v_active_count <= 1 THEN
    UPDATE public.orders
    SET status = 'cancelled',
        item_subtotal_paise = 0,
        discount_paise = 0,
        delivery_fee_paise = 0,
        tax_paise = 0,
        grand_total_paise = 0,
        payment_status = CASE
          WHEN payment_method = 'online'
               AND payment_status IN ('paid'::public.payment_status,
                                      'pending'::public.payment_status)
            THEN 'refund_pending'::public.payment_status
          ELSE payment_status
        END
    WHERE id = p_order_id;

    INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
    VALUES (p_order_id, v_order.status, 'cancelled', auth.uid(),
            'All items removed: ' || v_reason);

    INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, before, after, request_id)
    VALUES (
      auth.uid(), 'REMOVE_ORDER_ITEM', 'order_items', p_item_id::text,
      to_jsonb(v_item),
      jsonb_build_object('removed_at', now(), 'removed_reason', v_reason,
                         'order_cancelled', true),
      current_setting('request.id', true)
    );

    RETURN jsonb_build_object(
      'success', true,
      'restored_stock', v_item.product_id IS NOT NULL,
      'order_cancelled', true,
      'item_subtotal_paise', 0,
      'delivery_fee_paise', 0,
      'tax_paise', 0,
      'grand_total_paise', 0,
      'payment_status', CASE
        WHEN v_order.payment_method = 'online' THEN 'refund_pending'
        ELSE v_order.payment_status::text
      END
    );
  END IF;

  SELECT COALESCE(sum(line_total_paise), 0) INTO v_new_subtotal
  FROM public.order_items
  WHERE order_id = p_order_id AND removed_at IS NULL;

  SELECT * INTO v_settings FROM public.store_settings;
  SELECT * INTO v_fee_info FROM public.compute_delivery_fee(v_new_subtotal);
  v_new_fee := v_fee_info.fee_paise;
  v_discount := LEAST(v_order.discount_paise, v_new_subtotal);
  v_new_tax := CASE
    WHEN v_settings.tax_mode = 'exclusive' AND v_settings.tax_rate_bps > 0
      THEN ((v_new_subtotal - v_discount + v_new_fee) * v_settings.tax_rate_bps) / 10000
    ELSE 0
  END;
  v_new_grand := v_new_subtotal - v_discount + v_new_fee + v_new_tax;

  v_new_payment_status := CASE
    WHEN v_order.payment_method = 'online' THEN 'refund_pending'::public.payment_status
    ELSE v_order.payment_status
  END;

  UPDATE public.orders
  SET item_subtotal_paise = v_new_subtotal,
      discount_paise = v_discount,
      delivery_fee_paise = v_new_fee,
      tax_paise = v_new_tax,
      grand_total_paise = v_new_grand,
      payment_status = v_new_payment_status,
      fee_breakdown = jsonb_build_object(
        'delivery_charge_threshold_paise', v_settings.delivery_charge_threshold_paise,
        'delivery_fee_paise', v_settings.delivery_fee_paise,
        'free_delivery_threshold_paise', v_settings.free_delivery_threshold_paise,
        'mid_tier_fee_paise', v_settings.mid_tier_fee_paise,
        'eligible_subtotal_paise', v_new_subtotal,
        'delivery_fee_applied_paise', v_new_fee,
        'rule_note', v_fee_info.rule_note
      )
  WHERE id = p_order_id;

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, before, after, request_id)
  VALUES (
    auth.uid(), 'REMOVE_ORDER_ITEM', 'order_items', p_item_id::text,
    to_jsonb(v_item),
    jsonb_build_object(
      'removed_at', now(),
      'removed_reason', v_reason,
      'grand_total_paise', v_new_grand
    ),
    current_setting('request.id', true)
  );

  RETURN jsonb_build_object(
    'success', true,
    'restored_stock', v_item.product_id IS NOT NULL,
    'item_subtotal_paise', v_new_subtotal,
    'delivery_fee_paise', v_new_fee,
    'tax_paise', v_new_tax,
    'grand_total_paise', v_new_grand,
    'payment_status', v_new_payment_status
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.remove_order_item(uuid, uuid, text) TO authenticated;
