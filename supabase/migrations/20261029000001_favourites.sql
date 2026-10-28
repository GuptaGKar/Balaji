-- Migration: 20261029000001_favourites.sql
-- F3: favourites ("heart") for the customer app.
--   * One row per (customer, product), owner-only RLS.
--   * Direct PostgREST insert/delete is enough; no RPC needed, so there is no
--     second pricing or stock path.
--   * A favourited product that later becomes inactive stays in the table but is
--     hidden when the client joins products, because the products read policy only
--     exposes active and available rows.

BEGIN;

CREATE TABLE IF NOT EXISTS public.favourites (
  customer_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  product_id uuid NOT NULL REFERENCES public.products(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (customer_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_favourites_customer_created
  ON public.favourites (customer_id, created_at DESC);

ALTER TABLE public.favourites ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "favourites_owner_select" ON public.favourites;
CREATE POLICY "favourites_owner_select"
  ON public.favourites
  FOR SELECT TO authenticated
  USING (customer_id = auth.uid());

DROP POLICY IF EXISTS "favourites_owner_insert" ON public.favourites;
CREATE POLICY "favourites_owner_insert"
  ON public.favourites
  FOR INSERT TO authenticated
  WITH CHECK (customer_id = auth.uid());

DROP POLICY IF EXISTS "favourites_owner_delete" ON public.favourites;
CREATE POLICY "favourites_owner_delete"
  ON public.favourites
  FOR DELETE TO authenticated
  USING (customer_id = auth.uid());

GRANT SELECT, INSERT, DELETE ON public.favourites TO authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;
