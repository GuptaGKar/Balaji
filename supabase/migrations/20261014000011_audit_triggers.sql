-- M2: audit trigger infrastructure + table-specific triggers (IMPLEMENTATION_PLAN.md §4.9)
-- Triggers are SECURITY DEFINER, set search_path='', capture actor from auth.uid().

-- 1) Generic audit function
CREATE OR REPLACE FUNCTION public.audit_trigger_fn()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_actor uuid := auth.uid();
  v_before jsonb;
  v_after jsonb;
  v_action text;
BEGIN
  -- Determine action
  IF TG_OP = 'INSERT' THEN
    v_action := 'INSERT';
    v_before := NULL;
    v_after := to_jsonb(NEW);
  ELSIF TG_OP = 'UPDATE' THEN
    v_action := 'UPDATE';
    v_before := to_jsonb(OLD);
    v_after := to_jsonb(NEW);
  ELSIF TG_OP = 'DELETE' THEN
    v_action := 'DELETE';
    v_before := to_jsonb(OLD);
    v_after := NULL;
  END IF;

  -- Redact sensitive fields
  v_before := public.redact_for_audit(v_before);
  v_after := public.redact_for_audit(v_after);

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, before, after, request_id)
  VALUES (v_actor, v_action, TG_TABLE_NAME, COALESCE(NEW.id, OLD.id), v_before, v_after, current_setting('request.id', true)::text);

  RETURN NULL; -- result ignored for AFTER triggers
END;
$$;

-- 2) Tables that get audit triggers (per §4.9):
-- store_settings, service_areas, products (price/stock/active/available),
-- profiles (role/is_active), order_assignments, bills (void/correction)
-- Note: orders/order_items/pos_sales/bill_items use RPC-level audit (§4.9).

-- store_settings
DROP TRIGGER IF EXISTS audit_store_settings ON store_settings;
CREATE TRIGGER audit_store_settings
  AFTER INSERT OR UPDATE OR DELETE ON store_settings
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- service_areas
DROP TRIGGER IF EXISTS audit_service_areas ON service_areas;
CREATE TRIGGER audit_service_areas
  AFTER INSERT OR UPDATE OR DELETE ON service_areas
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- products (audit price, stock, active, available changes)
DROP TRIGGER IF EXISTS audit_products ON products;
CREATE TRIGGER audit_products
  AFTER UPDATE ON products
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- profiles (audit role, is_active)
DROP TRIGGER IF EXISTS audit_profiles ON profiles;
CREATE TRIGGER audit_profiles
  AFTER UPDATE OF role, is_active ON profiles
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- order_assignments
DROP TRIGGER IF EXISTS audit_order_assignments ON order_assignments;
CREATE TRIGGER audit_order_assignments
  AFTER INSERT OR UPDATE OR DELETE ON order_assignments
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- bills (audit void/correction: status, void_reason, voided_by, voided_at)
DROP TRIGGER IF EXISTS audit_bills ON bills;
CREATE TRIGGER audit_bills
  AFTER UPDATE OF status, void_reason, voided_by, voided_at ON bills
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_fn();

-- inventory_movements, order_status_events: append-only, no UPDATE/DELETE grants;
-- audit is implicit (every row is an audit trail). No triggers needed.

-- Grant execute on audit function to authenticated (triggers run as table owner, not caller;
-- but SECURITY DEFINER ensures it works regardless).
GRANT EXECUTE ON FUNCTION public.audit_trigger_fn() TO authenticated;

-- Note: RPC-level audit for status transitions, inventory adjustments, price overrides,
-- account creation/reset, item removal/refund-state changes is added in M4/M7/M9
-- when those RPCs are implemented.