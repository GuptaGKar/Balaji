-- M12: private customer/order evidence; catalogue and release buckets are unchanged.
BEGIN;
UPDATE storage.buckets SET public=false WHERE id IN ('order-images','order-proofs');
DROP POLICY IF EXISTS "order_images_public_read" ON storage.objects;
DROP POLICY IF EXISTS "order_proofs_public_read" ON storage.objects;
CREATE POLICY "order_images_authorized_read" ON storage.objects
FOR SELECT TO authenticated USING (
 bucket_id='order-images'
 AND EXISTS(SELECT 1 FROM public.profiles p WHERE p.id=auth.uid() AND p.is_active)
 AND (
  public.current_role()::text IN ('staff','manager')
  OR (public.current_role()::text='customer' AND (storage.foldername(name))[1]=auth.uid()::text)
  OR EXISTS(SELECT 1 FROM public.orders o WHERE o.image_path=storage.objects.name)
 )
);
CREATE POLICY "order_proofs_authorized_read" ON storage.objects
FOR SELECT TO authenticated USING (
 bucket_id='order-proofs'
 AND EXISTS(SELECT 1 FROM public.profiles p WHERE p.id=auth.uid() AND p.is_active)
 AND (
  public.current_role()::text IN ('staff','manager')
  -- order_proofs RLS enforces customer ownership and active rider assignment.
  OR EXISTS(SELECT 1 FROM public.order_proofs p WHERE p.path=storage.objects.name)
 )
);
NOTIFY pgrst, 'reload schema';
COMMIT;
