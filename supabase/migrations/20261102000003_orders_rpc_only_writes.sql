-- Order state, stock and totals must change together through the authorized RPCs.
BEGIN;
DROP POLICY IF EXISTS "orders_update_status_staff" ON public.orders;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
  ON public.orders FROM PUBLIC, anon, authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;
