-- M8: Inventory adjustments, reports, audit UI (IMPLEMENTATION_PLAN.md §8)
-- adjust_stock, low-stock list, stock reconciliation, reports, audit viewer

-- 1) adjust_stock RPC - manual stock adjustment with reason (staff/manager)
CREATE OR REPLACE FUNCTION public.adjust_stock(
  p_product_id uuid,
  p_delta_qty bigint,
  p_reason public.inventory_reason,
  p_note text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_product products%ROWTYPE;
  v_new_stock bigint;
  v_actor_id uuid := auth.uid();
BEGIN
  -- Authorization
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  -- Product must exist
  SELECT * INTO v_product FROM public.products WHERE id = p_product_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Product not found');
  END IF;

  -- Validate delta
  IF p_delta_qty = 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Delta quantity cannot be zero');
  END IF;

  -- Check resulting stock won't go negative
  v_new_stock := v_product.stock_qty + p_delta_qty;
  IF v_new_stock < 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Insufficient stock for adjustment');
  END IF;

  -- Apply adjustment
  UPDATE public.products
  SET stock_qty = v_new_stock
  WHERE id = p_product_id;

  -- Record movement
  INSERT INTO public.inventory_movements (product_id, delta_qty, reason, reference_type, reference_id, actor_id, note)
  VALUES (p_product_id, p_delta_qty, p_reason, 'manual', gen_random_uuid(), v_actor_id, p_note);

  -- Audit log
  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, before, after, request_id)
  VALUES (v_actor_id, 'ADJUST_STOCK', 'products', p_product_id,
          jsonb_build_object('stock_qty', v_product.stock_qty),
          jsonb_build_object('stock_qty', v_new_stock, 'delta_qty', p_delta_qty, 'reason', p_reason),
          current_setting('request.id', true)::text);

  RETURN jsonb_build_object(
    'success', true,
    'product_id', p_product_id,
    'old_stock', v_product.stock_qty,
    'new_stock', v_new_stock,
    'delta_qty', p_delta_qty
  );
END;
$$;

GRANT EXECUTE ON FUNCTION public.adjust_stock(uuid, bigint, public.inventory_reason, text) TO authenticated;

-- 2) Low stock list RPC
CREATE OR REPLACE FUNCTION public.get_low_stock_products()
RETURNS TABLE (
  id uuid,
  sku text,
  name text,
  stock_qty bigint,
  low_stock_threshold bigint,
  quantity_type public.quantity_type,
  unit_label text,
  price_basis_qty bigint,
  price_paise bigint
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
         p.quantity_type, p.unit_label, p.price_basis_qty, p.price_paise
  FROM public.products p
  WHERE p.active = true
    AND p.stock_qty > 0
    AND p.stock_qty <= p.low_stock_threshold
  ORDER BY p.low_stock_threshold - p.stock_qty DESC, p.name;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_low_stock_products() TO authenticated;

-- 3) Stock reconciliation check RPC
CREATE OR REPLACE FUNCTION public.check_stock_reconciliation()
RETURNS TABLE (
  product_id uuid,
  sku text,
  name text,
  current_stock bigint,
  movement_sum bigint,
  discrepancy bigint
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
  SELECT p.id as product_id, p.sku, p.name, p.stock_qty as current_stock,
         COALESCE(SUM(im.delta_qty), 0) as movement_sum,
         p.stock_qty - COALESCE(SUM(im.delta_qty), 0) as discrepancy
  FROM public.products p
  LEFT JOIN public.inventory_movements im ON im.product_id = p.id
  WHERE p.active = true
  GROUP BY p.id, p.sku, p.name, p.stock_qty
  HAVING p.stock_qty <> COALESCE(SUM(im.delta_qty), 0)
  ORDER BY ABS(p.stock_qty - COALESCE(SUM(im.delta_qty), 0)) DESC;
END;
$$;

GRANT EXECUTE ON FUNCTION public.check_stock_reconciliation() TO authenticated;

-- 4) Reports: Sales summary by date range and status
CREATE OR REPLACE FUNCTION public.get_sales_report(
  p_start_date timestamptz,
  p_end_date timestamptz,
  p_status public.order_status DEFAULT NULL
)
RETURNS TABLE (
  total_orders bigint,
  total_revenue_paise bigint,
  total_cod_paise bigint,
  total_online_paise bigint,
  avg_order_value_paise bigint,
  cod_collected_paise bigint,
  cod_pending_paise bigint,
  by_status jsonb,
  by_payment_method jsonb,
  daily_breakdown jsonb
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() NOT IN ('staff','manager') THEN
    RETURN;
  END IF;

  -- Main aggregates
  RETURN QUERY
  WITH filtered_orders AS (
    SELECT o.*
    FROM public.orders o
    WHERE o.created_at >= p_start_date
      AND o.created_at <= p_end_date
      AND (p_status IS NULL OR o.status = p_status)
      AND o.status NOT IN ('cancelled', 'rejected')
  ),
  main_agg AS (
    SELECT 
      COUNT(*) as total_orders,
      COALESCE(SUM(grand_total_paise), 0) as total_revenue_paise,
      COALESCE(SUM(CASE WHEN payment_method = 'cod' THEN grand_total_paise ELSE 0 END), 0) as total_cod_paise,
      COALESCE(SUM(CASE WHEN payment_method = 'online' THEN grand_total_paise ELSE 0 END), 0) as total_online_paise,
      CASE WHEN COUNT(*) > 0 THEN COALESCE(SUM(grand_total_paise), 0) / COUNT(*) ELSE 0 END as avg_order_value_paise,
      COALESCE(SUM(cod_collected_paise), 0) as cod_collected_paise,
      COALESCE(SUM(CASE WHEN payment_method = 'cod' AND payment_status = 'unpaid' THEN grand_total_paise ELSE 0 END), 0) as cod_pending_paise
    FROM filtered_orders
  ),
  status_breakdown AS (
    SELECT jsonb_object_agg(status, cnt) as by_status
    FROM (
      SELECT status, COUNT(*) as cnt
      FROM filtered_orders
      GROUP BY status
    ) s
  ),
  payment_breakdown AS (
    SELECT jsonb_object_agg(payment_method, cnt) as by_payment_method
    FROM (
      SELECT payment_method, COUNT(*) as cnt
      FROM filtered_orders
      GROUP BY payment_method
    ) s
  ),
  daily AS (
    SELECT jsonb_agg(jsonb_build_object(
      'date', d,
      'orders', c,
      'revenue_paise', r
    ) ORDER BY d) as daily_breakdown
    FROM (
      SELECT DATE(created_at) as d,
             COUNT(*) as c,
             COALESCE(SUM(grand_total_paise), 0) as r
      FROM filtered_orders
      GROUP BY DATE(created_at)
    ) d
  )
  SELECT m.total_orders, m.total_revenue_paise, m.total_cod_paise, m.total_online_paise,
         m.avg_order_value_paise, m.cod_collected_paise, m.cod_pending_paise,
         s.by_status, p.by_payment_method, d.daily_breakdown
  FROM main_agg m
  CROSS JOIN status_breakdown s
  CROSS JOIN payment_breakdown p
  CROSS JOIN daily d;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_sales_report(timestamptz, timestamptz, public.order_status) TO authenticated;

-- 5) Reports: COD reconciliation
CREATE OR REPLACE FUNCTION public.get_cod_reconciliation(
  p_start_date timestamptz,
  p_end_date timestamptz
)
RETURNS TABLE (
  total_cod_orders bigint,
  total_cod_paise bigint,
  collected_paise bigint,
  pending_paise bigint,
  by_rider jsonb,
  daily_breakdown jsonb
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
  WITH cod_orders AS (
    SELECT o.*, oa.delivery_user_id
    FROM public.orders o
    LEFT JOIN public.order_assignments oa ON oa.order_id = o.id AND oa.unassigned_at IS NULL
    WHERE o.payment_method = 'cod'
      AND o.created_at >= p_start_date
      AND o.created_at <= p_end_date
      AND o.status NOT IN ('cancelled', 'rejected')
  ),
  main_agg AS (
    SELECT 
      COUNT(*) as total_cod_orders,
      COALESCE(SUM(grand_total_paise), 0) as total_cod_paise,
      COALESCE(SUM(cod_collected_paise), 0) as collected_paise,
      COALESCE(SUM(CASE WHEN cod_collected_paise IS NULL OR cod_collected_paise = 0 THEN grand_total_paise ELSE 0 END), 0) as pending_paise
    FROM cod_orders
  ),
  by_rider AS (
    SELECT jsonb_object_agg(rider, stats) as by_rider
    FROM (
      SELECT 
        COALESCE(p.display_name || ' (' || p.login_id || ')', 'Unassigned') as rider,
        jsonb_build_object(
          'orders', COUNT(*),
          'total_paise', COALESCE(SUM(o.grand_total_paise), 0),
          'collected_paise', COALESCE(SUM(o.cod_collected_paise), 0),
          'pending_paise', COALESCE(SUM(CASE WHEN o.cod_collected_paise IS NULL OR o.cod_collected_paise = 0 THEN o.grand_total_paise ELSE 0 END), 0)
        ) as stats
      FROM cod_orders o
      LEFT JOIN public.profiles p ON p.id = o.delivery_user_id
      GROUP BY p.id, p.display_name, p.login_id
    ) s
  ),
  daily AS (
    SELECT jsonb_agg(jsonb_build_object(
      'date', d,
      'orders', c,
      'total_paise', t,
      'collected_paise', col
    ) ORDER BY d) as daily_breakdown
    FROM (
      SELECT DATE(o.created_at) as d,
             COUNT(*) as c,
             COALESCE(SUM(o.grand_total_paise), 0) as t,
             COALESCE(SUM(o.cod_collected_paise), 0) as col
      FROM cod_orders o
      GROUP BY DATE(o.created_at)
    ) d
  )
  SELECT m.total_cod_orders, m.total_cod_paise, m.collected_paise, m.pending_paise,
         r.by_rider, d.daily_breakdown
  FROM main_agg m
  CROSS JOIN by_rider r
  CROSS JOIN daily d;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_cod_reconciliation(timestamptz, timestamptz) TO authenticated;

-- 5) Audit log viewer RPC
CREATE OR REPLACE FUNCTION public.get_audit_logs(
  p_start_date timestamptz DEFAULT NULL,
  p_end_date timestamptz DEFAULT NULL,
  p_entity_type text DEFAULT NULL,
  p_actor_id uuid DEFAULT NULL,
  p_limit int DEFAULT 100,
  p_offset int DEFAULT 0
)
RETURNS TABLE (
  id uuid,
  actor_id uuid,
  action text,
  entity_type text,
  entity_id text,
  before jsonb,
  after jsonb,
  request_id text,
  created_at timestamptz,
  actor_name text,
  actor_role text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() <> 'manager' THEN
    RETURN;
  END IF;

  RETURN QUERY
  SELECT al.id, al.actor_id, al.action, al.entity_type, al.entity_id,
         al.before, al.after, al.request_id, al.created_at,
         p.display_name as actor_name, p.role as actor_role
  FROM public.audit_logs al
  LEFT JOIN public.profiles p ON p.id = al.actor_id
  WHERE (p_start_date IS NULL OR al.created_at >= p_start_date)
    AND (p_end_date IS NULL OR al.created_at <= p_end_date)
    AND (p_entity_type IS NULL OR al.entity_type = p_entity_type)
    AND (p_actor_id IS NULL OR al.actor_id = p_actor_id)
  ORDER BY al.created_at DESC
  LIMIT p_limit OFFSET p_offset;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_audit_logs(timestamptz, timestamptz, text, uuid, int, int) TO authenticated;

-- 6) RLS policies for inventory_movements (already enabled in M1, add manager read)
-- Already covered in M2: inventory_movements read for staff/manager

-- 7) RLS policy for bill_counters (manager only)
CREATE POLICY "bill_counters_manager" ON bill_counters
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager')
  WITH CHECK (public.current_role() = 'manager');

-- 8) RLS policy for pos_sales (staff/manager read, cashier insert)
CREATE POLICY "pos_sales_write_staff" ON pos_sales
  FOR INSERT TO authenticated
  WITH CHECK (public.current_role() IN ('staff','manager'));

CREATE POLICY "pos_sales_read_staff" ON pos_sales
  FOR SELECT TO authenticated
  USING (public.current_role() IN ('staff','manager'));

-- 9) RLS policy for bills (customer reads own, staff/manager read all)
CREATE POLICY "bills_read_own" ON bills
  FOR SELECT TO authenticated
  USING (
    (source = 'ORDER' AND EXISTS (
      SELECT 1 FROM orders o WHERE o.id = bills.order_id AND o.customer_id = auth.uid()
    )) OR
    public.current_role() IN ('staff','manager')
  );

CREATE POLICY "bills_write_staff" ON bills
  FOR ALL TO authenticated
  USING (public.current_role() IN ('staff','manager'))
  WITH CHECK (public.current_role() IN ('staff','manager'));

-- 10) RLS policy for bill_items (follow bills)
CREATE POLICY "bill_items_read_own" ON bill_items
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM bills b
    WHERE b.id = bill_items.bill_id
      AND ((b.source = 'ORDER' AND EXISTS (
        SELECT 1 FROM orders o WHERE o.id = b.order_id AND o.customer_id = auth.uid()
      )) OR public.current_role() IN ('staff','manager'))
  ));

CREATE POLICY "bill_items_write_staff" ON bill_items
  FOR ALL TO authenticated
  USING (public.current_role() IN ('staff','manager'))
  WITH CHECK (public.current_role() IN ('staff','manager'));

-- 11) RLS policy for payment_events (internal only)
CREATE POLICY "payment_events_none" ON payment_events
  FOR ALL TO authenticated USING (false);

-- 12) RLS policy for idempotency_keys (user owns their keys)
CREATE POLICY "idempotency_own" ON idempotency_keys
  FOR ALL TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

-- 13) RLS policy for audit_logs (manager read only)
CREATE POLICY "audit_logs_read_manager" ON audit_logs
  FOR SELECT TO authenticated
  USING (public.current_role() = 'manager');

-- 14) RLS policy for notification_outbox (internal only)
CREATE POLICY "notification_outbox_none" ON notification_outbox
  FOR ALL TO authenticated USING (false);