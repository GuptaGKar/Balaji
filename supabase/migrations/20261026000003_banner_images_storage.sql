-- Banner images: public-read bucket, manager-only writes.
-- The admin settings form uploads a hero image and stores its public URL in
-- store_settings.weekly_hours.promotional_banners[].image_url. The Android
-- storefront reads the same JSON and falls back to its built-in banner art when
-- the URL is empty.

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'banner-images',
  'banner-images',
  true,
  2097152,
  ARRAY['image/jpeg', 'image/png', 'image/webp']::text[]
)
ON CONFLICT (id) DO UPDATE
SET public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "banner_images_public_read" ON storage.objects;
CREATE POLICY "banner_images_public_read"
  ON storage.objects
  FOR SELECT
  TO public
  USING (bucket_id = 'banner-images');

DROP POLICY IF EXISTS "banner_images_manager_insert" ON storage.objects;
CREATE POLICY "banner_images_manager_insert"
  ON storage.objects
  FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'banner-images'
    AND public.current_role() = 'manager'
  );

DROP POLICY IF EXISTS "banner_images_manager_update" ON storage.objects;
CREATE POLICY "banner_images_manager_update"
  ON storage.objects
  FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'banner-images'
    AND public.current_role() = 'manager'
  )
  WITH CHECK (
    bucket_id = 'banner-images'
    AND public.current_role() = 'manager'
  );

DROP POLICY IF EXISTS "banner_images_manager_delete" ON storage.objects;
CREATE POLICY "banner_images_manager_delete"
  ON storage.objects
  FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'banner-images'
    AND public.current_role() = 'manager'
  );
