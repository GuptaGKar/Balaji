-- M12: proof metadata follows active order assignments; never edit applied policy history.
BEGIN;

DROP POLICY IF EXISTS "order_proofs_read" ON public.order_proofs;
CREATE POLICY "order_proofs_read"
  ON public.order_proofs
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.profiles p
      WHERE p.id = auth.uid() AND p.is_active
    )
    AND (
      public.current_role()::text IN ('staff', 'manager')
      OR EXISTS (
        SELECT 1 FROM public.orders o
        WHERE o.id = order_proofs.order_id
          AND (
            o.customer_id = auth.uid()
            OR (
              public.current_role()::text = 'delivery'
              AND EXISTS (
                SELECT 1 FROM public.order_assignments a
                WHERE a.order_id = o.id
                  AND a.delivery_user_id = auth.uid()
                  AND a.unassigned_at IS NULL
              )
            )
          )
      )
    )
  );

NOTIFY pgrst, 'reload schema';
COMMIT;
