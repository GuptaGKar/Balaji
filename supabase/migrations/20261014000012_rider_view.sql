-- M2: rider limited view + status update RPC (IMPLEMENTATION_PLAN.md §5.2, §5.4, §6)
-- Riders see ONLY assigned orders, with only necessary fields.

-- 1) Security-invoker view: filters to orders assigned to the current delivery user
-- Only exposes: order number, recipient name, phone, address snapshot, item names/quantities,
-- payment method, COD amount due, status, customer_note.
CREATE OR REPLACE VIEW public.rider_orders
WITH (security_invoker = true) AS
SELECT
  o.id,
  o.public_order_number,
  o.address_snapshot->>'recipient_name' AS recipient_name,
  o.address_snapshot->>'phone' AS phone,
  o.address_snapshot->>'address_lines' AS address_lines,
  o.address_snapshot->>'landmark' AS landmark,
  o.address_snapshot->>'area' AS area,
  o.address_snapshot->>'city' AS city,
  o.address_snapshot->>'state' AS state,
  o.address_snapshot->>'postal_code' AS postal_code,
  o.payment_method,
  o.cod_collected_paise,
  o.grand_total_paise,
  o.status,
  o.customer_note,
  o.created_at,
  o.accepted_at,
  (
    SELECT jsonb_agg(jsonb_build_object(
      'name', oi.name_snapshot,
      'qty_base', oi.qty_base,
      'unit_label', oi.unit_label_snapshot,
      'quantity_type', oi.quantity_type_snapshot
    ))
    FROM public.order_items oi
    WHERE oi.order_id = o.id AND oi.removed_at IS NULL
  ) AS items
FROM public.orders o
JOIN public.order_assignments oa ON oa.order_id = o.id
WHERE oa.delivery_user_id = auth.uid()
  AND oa.unassigned_at IS NULL
  AND o.status IN ('ready_for_dispatch', 'out_for_delivery', 'delivered', 'delivery_failed');

GRANT SELECT ON public.rider_orders TO authenticated;

-- 2) Rider status update RPC — enforces state machine and assignment ownership
-- Called by rider; only allows the two rider transitions on their assigned orders.
CREATE OR REPLACE FUNCTION public.rider_update_status(
  p_order_id uuid,
  p_to_status order_status,
  p_note text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_order public.orders%ROWTYPE;
  v_assignment public.order_assignments%ROWTYPE;
  v_from_status public.order_status;
  v_allowed boolean;
BEGIN
  -- Must be a delivery user
  IF public.current_role() <> 'delivery'::public.user_role THEN
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

  -- Perform transition (status + event + timestamp)
  UPDATE public.orders
  SET status = p_to_status,
      delivered_at = CASE WHEN p_to_status = 'delivered' THEN now() ELSE delivered_at END
  WHERE id = p_order_id;

  INSERT INTO public.order_status_events (order_id, from_status, to_status, actor_id, reason)
  VALUES (p_order_id, v_from_status, p_to_status, auth.uid(), p_note);

  RETURN jsonb_build_object(
    'order_id', p_order_id,
    'from_status', v_from_status,
    'to_status', p_to_status,
    'ok', true
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.rider_update_status(uuid, order_status, text) TO authenticated;

-- 3) Staff/manager transition RPC — full state machine enforcement (stub for M2, fleshed out in M4)
-- This placeholder exists so RLS policies referencing it can be tested; real logic in M4.
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
BEGIN
  IF public.current_role() = ANY (ARRAY['staff','manager']::public.user_role[]) = false THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;
  -- Full implementation in M4. For now, deny to avoid accidental use.
  RETURN jsonb_build_object('error', 'NOT_IMPLEMENTED', 'message', 'transition_order implemented in M4');
END;
$$;

GRANT EXECUTE ON FUNCTION public.transition_order(uuid, public.order_status, text) TO authenticated;

-- 4) Staff/manager assign order RPC (stub; real logic in M6)
CREATE OR REPLACE FUNCTION public.assign_order(
  p_order_id uuid,
  p_delivery_user_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() = ANY (ARRAY['staff','manager']::public.user_role[]) = false THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;
  RETURN jsonb_build_object('error', 'NOT_IMPLEMENTED', 'message', 'assign_order implemented in M6');
END;
$$;

GRANT EXECUTE ON FUNCTION public.assign_order(uuid, uuid) TO authenticated;