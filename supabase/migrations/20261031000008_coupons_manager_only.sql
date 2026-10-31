-- Migration: 20261031000008_coupons_manager_only.sql
-- BUG-101: public.coupons was readable with only the publishable key, so anyone could
-- enumerate every active code, its discount and its PIN restrictions.
--
-- Coupon evaluation already happens inside quote_cart/place_order, which are SECURITY
-- DEFINER and therefore unaffected by RLS. Only the dashboard (manager) needs to read
-- the table directly.

BEGIN;

DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT policyname FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'coupons'
  LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.coupons', r.policyname);
  END LOOP;
END $$;

ALTER TABLE public.coupons ENABLE ROW LEVEL SECURITY;

CREATE POLICY "coupons_manager_all"
  ON public.coupons
  FOR ALL TO authenticated
  USING (public.current_role()::text = 'manager')
  WITH CHECK (public.current_role()::text = 'manager');

REVOKE ALL ON public.coupons FROM anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.coupons TO authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;
