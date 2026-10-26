-- Product images: public-read bucket, manager-only writes.
-- Catalogue managers upload from the admin web app; customers read via the
-- public object URL stored in products.image_path.

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'product-images',
  'product-images',
  true,
  2097152,
  ARRAY['image/jpeg', 'image/png', 'image/webp']::text[]
)
ON CONFLICT (id) DO UPDATE
SET public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "product_images_public_read" ON storage.objects;
CREATE POLICY "product_images_public_read"
  ON storage.objects
  FOR SELECT
  TO public
  USING (bucket_id = 'product-images');

DROP POLICY IF EXISTS "product_images_manager_insert" ON storage.objects;
CREATE POLICY "product_images_manager_insert"
  ON storage.objects
  FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'product-images'
    AND public.current_role() = 'manager'
  );

DROP POLICY IF EXISTS "product_images_manager_update" ON storage.objects;
CREATE POLICY "product_images_manager_update"
  ON storage.objects
  FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'product-images'
    AND public.current_role() = 'manager'
  )
  WITH CHECK (
    bucket_id = 'product-images'
    AND public.current_role() = 'manager'
  );

DROP POLICY IF EXISTS "product_images_manager_delete" ON storage.objects;
CREATE POLICY "product_images_manager_delete"
  ON storage.objects
  FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'product-images'
    AND public.current_role() = 'manager'
  );
