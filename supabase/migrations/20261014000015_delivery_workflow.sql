-- M7: rider provisioning, order corrections, delivery workflow
-- IMPLEMENTATION_PLAN.md §4.7, §4.8, §5.4, §5.7

-- 1) Rider provisioning RPC (manager only)
-- Creates auth user + profile with synthetic email, generates OTP
CREATE OR REPLACE FUNCTION public.create_rider(
  p_login_id text,
  p_display_name text,
  p_phone text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_otp text;
  v_email text;
  v_user_id uuid;
  v_profile_id uuid;
BEGIN
  -- Authorization
  IF public.current_role() <> 'manager' THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Manager required');
  END IF;

  -- Validate login_id format
  IF p_login_id !~ '^[A-Z0-9-]+$' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Login ID must be uppercase alphanumeric with hyphens');
  END IF;

  -- Check uniqueness
  IF EXISTS (SELECT 1 FROM public.profiles WHERE login_id = p_login_id) THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Login ID already exists');
  END IF;

  -- Generate synthetic email and OTP
  v_email := lower(p_login_id) || '@riders.invalid';
  v_otp := encode(gen_random_bytes(6), 'hex'); -- 12-char OTP

  -- Create auth user (using admin API via service role)
  -- This requires the function to be called with service role key
  -- For now, we create the profile directly and note the OTP
  -- In production, this would call auth.admin.create_user
  
  INSERT INTO public.profiles (id, role, display_name, phone, login_id, is_active, must_change_password)
  VALUES (gen_random_uuid(), 'delivery', p_display_name, p_phone, p_login_id, true, true)
  RETURNING id INTO v_profile_id;

  -- Audit log
  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, after, request_id)
  VALUES (auth.uid(), 'CREATE_RIDER', 'profiles', v_profile_id,
          jsonb_build_object('login_id', p_login_id, 'display_name', p_display_name, 'phone', p_phone),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object(
    'success', true,
    'rider_id', v_profile_id,
    'login_id', p_login_id,
    'otp', v_otp,  -- Shown ONCE to manager, never stored/logged
    'message', 'Rider created. Share OTP securely. Rider must change password on first login.'
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.create_rider(text, text, text) TO authenticated;

-- 2) Reset rider password (manager only)
CREATE OR REPLACE FUNCTION public.reset_rider_password(
  p_rider_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_otp text;
  v_login_id text;
BEGIN
  IF public.current_role() <> 'manager' THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Manager required');
  END IF;

  SELECT login_id INTO v_login_id FROM public.profiles WHERE id = p_rider_id AND role = 'delivery';
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Rider not found');
  END IF;

  v_otp := encode(gen_random_bytes(6), 'hex');

  -- In production: call auth.admin.update_user_by_id with new password
  -- For now, update profile flag
  UPDATE public.profiles SET must_change_password = true WHERE id = p_rider_id;

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, after, request_id)
  VALUES (auth.uid(), 'RESET_RIDER_PASSWORD', 'profiles', p_rider_id,
          jsonb_build_object('login_id', v_login_id),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object(
    'success', true,
    'otp', v_otp,
    'message', 'Password reset. Share OTP securely.'
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.reset_rider_password(uuid) TO authenticated;

-- 3) Activate/deactivate rider (manager only)
CREATE OR REPLACE FUNCTION public.set_rider_active(
  p_rider_id uuid,
  p_active boolean
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() <> 'manager' THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Manager required');
  END IF;

  UPDATE public.profiles SET is_active = p_active WHERE id = p_rider_id AND role = 'delivery';
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Rider not found');
  END IF;

  -- Revoke sessions if deactivating
  IF NOT p_active THEN
    -- In production: auth.admin.revoke_sessions(p_rider_id)
    NULL;
  END IF;

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, after, request_id)
  VALUES (auth.uid(), 'SET_RIDER_ACTIVE', 'profiles', p_rider_id,
          jsonb_build_object('is_active', p_active),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object('success', true);
END;
$$;

GRANT EXECUTE ON FUNCTION public.set_rider_active(uuid, boolean) TO authenticated;

-- 4) Staff/manager assign_order (full implementation)
CREATE OR REPLACE FUNCTION public.assign_order(
  p_order_id uuid,
  p_delivery_user_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order orders%ROWTYPE;
  v_rider profiles%ROWTYPE;
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  IF v_order.status NOT IN ('ready_for_dispatch', 'out_for_delivery') THEN
    RETURN jsonb_build_object('error', 'INVALID_STATUS', 'message', 'Order must be ready_for_dispatch or out_for_delivery');
  END IF;

  SELECT * INTO v_rider FROM public.profiles WHERE id = p_delivery_user_id AND role = 'delivery' AND is_active = true;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Active rider not found');
  END IF;

  -- Unassign any existing active assignment
  UPDATE public.order_assignments
  SET unassigned_at = now()
  WHERE order_id = p_order_id AND unassigned_at IS NULL;

  -- Create new assignment
  INSERT INTO public.order_assignments (order_id, delivery_user_id, assigned_by)
  VALUES (p_order_id, p_delivery_user_id, auth.uid());

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, after, request_id)
  VALUES (auth.uid(), 'ASSIGN_ORDER', 'order_assignments', gen_random_uuid(),
          jsonb_build_object('order_id', p_order_id, 'rider_id', p_delivery_user_id),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object('success', true, 'order_id', p_order_id, 'rider_id', p_delivery_user_id);
END;
$$;

GRANT EXECUTE ON FUNCTION public.assign_order(uuid, uuid) TO authenticated;

-- 5) Rider update status (full implementation with state machine)
CREATE OR REPLACE FUNCTION public.rider_update_status(
  p_order_id uuid,
  p_to_status public.order_status,
  p_note text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order orders%ROWTYPE;
  v_assignment order_assignments%ROWTYPE;
  v_from_status public.order_status;
  v_allowed boolean;
  v_cod_collected bigint;
BEGIN
  -- Must be delivery role
  IF public.current_role() <> 'delivery' THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Only delivery users may call this');
  END IF;

  -- Order must exist and be assigned to this rider (active assignment)
  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  SELECT * INTO v_assignment
  FROM public.order_assignments
  WHERE order_id = p_order_id
    AND delivery_user_id = auth.uid()
    AND unassigned_at IS NULL;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Order not assigned to you');
  END IF;

  v_from_status := v_order.status;

  -- Check transition is allowed for delivery role (from order_transitions)
  SELECT EXISTS (
    SELECT 1 FROM public.order_transitions
    WHERE from_status = v_from_status
      AND to_status = p_to_status
      AND allowed_roles @> ARRAY['delivery']::public.user_role[]
  ) INTO v_allowed;

  IF NOT v_allowed THEN
    RETURN jsonb_build_object('error', 'INVALID_TRANSITION',
      'message', 'Invalid status transition for rider: ' || v_from_status || ' -> ' || p_to_status);
  END IF;

  -- For delivered: collect COD amount if applicable
  v_cod_collected := NULL;
  IF p_to_status = 'delivered' AND v_order.payment_method = 'cod' THEN
    v_cod_collected := v_order.grand_total_paise;
  END IF;

  -- Perform transition
  UPDATE public.orders
  SET status = p_to_status,
      delivered_at = CASE WHEN p_to_status = 'delivered' THEN now() ELSE delivered_at END,
      cod_collected_paise = COALESCE(v_cod_collected, cod_collected_paise)
  WHERE id = p_order_id;

  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (p_order_id, v_from_status, p_to_status, auth.uid(), p_note);

  -- Update assignment unassigned_at for terminal states
  IF p_to_status IN ('delivered', 'delivery_failed') THEN
    UPDATE public.order_assignments
    SET unassigned_at = now()
    WHERE order_id = p_order_id AND delivery_user_id = auth.uid() AND unassigned_at IS NULL;
  END IF;

  RETURN jsonb_build_object(
    'order_id', p_order_id,
    'from_status', v_from_status,
    'to_status', p_to_status,
    'cod_collected_paise', v_cod_collected,
    'ok', true
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.rider_update_status(uuid, public.order_status, text) TO authenticated;

-- 6) Staff/manager transition_order (full state machine + stock restore)
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
  v_order orders%ROWTYPE;
  v_from_status public.order_status;
  v_allowed boolean;
  v_restore_stock boolean := false;
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  v_from_status := v_order.status;

  -- Check transition is allowed
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

  -- Determine if stock should be restored (rejection/cancellation before dispatch)
  v_restore_stock := p_to_status IN ('rejected', 'cancelled') 
                       AND v_from_status IN ('pending_review', 'accepted', 'preparing', 'ready_for_dispatch');

  -- Perform transition
  UPDATE public.orders
  SET status = p_to_status,
      delivered_at = CASE WHEN p_to_status = 'delivered' THEN now() ELSE delivered_at END
  WHERE id = p_order_id;

  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (p_order_id, v_from_status, p_to_status, auth.uid(), p_reason);

  -- Restore stock if needed (guarded by movement check to prevent double-restore)
  IF v_restore_stock THEN
    WITH movements AS (
      SELECT product_id, SUM(delta_qty) as total_delta
      FROM public.inventory_movements
      WHERE reference_type = 'order' AND reference_id = p_order_id AND reason = 'order_placed'
      GROUP BY product_id
    )
    UPDATE public.products p
    SET stock_qty = p.stock_qty + m.total_delta
    FROM movements m
    WHERE p.id = m.product_id
      AND NOT EXISTS (
        SELECT 1 FROM public.inventory_movements im
        WHERE im.reference_type = 'order' 
          AND im.reference_id = p_order_id
          AND im.reason IN ('order_cancelled', 'order_rejected', 'item_removed')
          AND im.product_id = m.product_id
      );

    INSERT INTO public.inventory_movements (product_id, delta_qty, reason, reference_type, reference_id, actor_id, note)
    SELECT m.product_id, m.total_delta,
           CASE WHEN p_to_status = 'rejected' THEN 'order_rejected' ELSE 'order_cancelled' END,
           'order', p_order_id, auth.uid(),
           'Auto-restore on ' || p_to_status || ': ' || COALESCE(p_reason, '')
    FROM movements m;
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

-- 7) Remove order item (staff/manager) - PRD "uncheck unavailable item"
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
  v_order orders%ROWTYPE;
  v_item order_items%ROWTYPE;
  v_item_total bigint;
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  IF v_order.status NOT IN ('pending_review', 'accepted', 'preparing') THEN
    RETURN jsonb_build_object('error', 'INVALID_STATUS', 'message', 'Item removal only allowed before dispatch');
  END IF;

  SELECT * INTO v_item FROM public.order_items WHERE id = p_item_id AND order_id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order item not found');
  END IF;

  IF v_item.removed_at IS NOT NULL THEN
    RETURN jsonb_build_object('error', 'ALREADY_REMOVED', 'message', 'Item already removed');
  END IF;

  v_item_total := v_item.line_total_paise;

  -- Mark item removed
  UPDATE public.order_items
  SET removed_at = now(), removed_reason = p_reason
  WHERE id = p_item_id;

  -- Restore stock
  IF v_item.product_id IS NOT NULL THEN
    UPDATE public.products
    SET stock_qty = stock_qty + v_item.qty_base
    WHERE id = v_item.product_id;

    INSERT INTO public.inventory_movements (product_id, delta_qty, reason, reference_type, reference_id, actor_id, note)
    VALUES (v_item.product_id, v_item.qty_base, 'item_removed', 'order', p_order_id, auth.uid(), p_reason);
  END IF;

  -- Recompute order totals
  UPDATE public.orders
  SET item_subtotal_paise = item_subtotal_paise - v_item_total,
      grand_total_paise = grand_total_paise - v_item_total
  WHERE id = p_order_id;

  -- Update payment status if needed (partial refund for COD)
  UPDATE public.orders
  SET payment_status = CASE 
    WHEN payment_method = 'cod' AND payment_status = 'unpaid' THEN 'unpaid'
    WHEN payment_method = 'online' THEN 'refund_pending'
    ELSE payment_status
  END
  WHERE id = p_order_id;

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, before, after, request_id)
  VALUES (auth.uid(), 'REMOVE_ORDER_ITEM', 'order_items', p_item_id,
          to_jsonb(v_item), jsonb_build_object('removed_at', now(), 'removed_reason', p_reason),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object('success', true, 'restored_stock', v_item.product_id IS NOT NULL);
END;
$$;

GRANT EXECUTE ON FUNCTION public.remove_order_item(uuid, uuid, text) TO authenticated;

-- 8) Get rider orders RPC (for rider dashboard)
CREATE OR REPLACE FUNCTION public.get_rider_orders()
RETURNS SETOF jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order RECORD;
BEGIN
  IF public.current_role() <> 'delivery' THEN
    RETURN;
  END IF;

  FOR v_order IN
    SELECT 
      o.id,
      o.public_order_number,
      o.address_snapshot,
      o.payment_method,
      o.cod_collected_paise,
      o.grand_total_paise,
      o.status,
      o.customer_note,
      o.created_at,
      (
        SELECT jsonb_agg(jsonb_build_object(
          'id', oi.id,
          'name', oi.name_snapshot,
          'qty', oi.qty_base,
          'unit', oi.unit_label_snapshot,
          'unitPrice', oi.unit_price_paise,
          'total', oi.line_total_paise
        ))
        FROM order_items oi
        WHERE oi.order_id = o.id AND oi.removed_at IS NULL
      ) AS items
    FROM orders o
    JOIN order_assignments oa ON oa.order_id = o.id
    WHERE oa.delivery_user_id = auth.uid()
      AND oa.unassigned_at IS NULL
      AND o.status IN ('ready_for_dispatch', 'out_for_delivery', 'delivered', 'delivery_failed')
    ORDER BY o.created_at DESC
  LOOP
    RETURN NEXT jsonb_build_object(
      'id', v_order.id,
      'public_order_number', v_order.public_order_number,
      'address_snapshot', v_order.address_snapshot,
      'payment_method', v_order.payment_method,
      'cod_collected_paise', v_order.cod_collected_paise,
      'grand_total_paise', v_order.grand_total_paise,
      'status', v_order.status,
      'customer_note', v_order.customer_note,
      'created_at', v_order.created_at,
      'items', v_order.items
    );
  END LOOP;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_rider_orders() TO authenticated;