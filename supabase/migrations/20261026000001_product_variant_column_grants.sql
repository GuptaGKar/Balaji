-- Fix "unable to save product": restore INSERT/UPDATE on the two variant
-- columns added after the security-hardening migration.
--
-- 20261014000017_security_hardening.sql deliberately replaced table-level
-- INSERT/UPDATE on public.products with a column allow-list that excludes
-- stock_qty (stock must go through adjust_stock). 20261023000001 added
-- variant_group_id and variant_label later, but never extended that allow-list.
-- The admin form includes both columns whenever the grouping feature is
-- present, so every product save failed with a column-permission error even
-- though the manager RLS policy allowed the row write.
--
-- RLS is unchanged: products_write_manager still restricts writes to managers.
-- Column privileges are additive; the existing allow-list stays in force.

GRANT INSERT (variant_group_id, variant_label) ON public.products TO authenticated;
GRANT UPDATE (variant_group_id, variant_label) ON public.products TO authenticated;

-- Rollback:
--   REVOKE INSERT (variant_group_id, variant_label) ON public.products FROM authenticated;
--   REVOKE UPDATE (variant_group_id, variant_label) ON public.products FROM authenticated;
