-- Category images: public-read bucket, manager-only writes.
-- The admin catalogue form uploads an image and stores the public URL in
-- categories.image_path. The Android storefront falls back to its category
-- icon when the column is empty.

ALTER TABLE public.categories
  ADD COLUMN IF NOT EXISTS image_path text;

CREATE OR REPLACE VIEW public.catalog_categories AS SELECT * FROM public.categories;
ALTER VIEW public.catalog_categories SET (security_invoker = true);

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'category-images',
  'category-images',
  true,
  2097152,
  ARRAY['image/jpeg', 'image/png', 'image/webp']::text[]
)
ON CONFLICT (id) DO UPDATE
SET public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "category_images_public_read" ON storage.objects;
CREATE POLICY "category_images_public_read"
  ON storage.objects
  FOR SELECT
  TO public
  USING (bucket_id = 'category-images');

DROP POLICY IF EXISTS "category_images_manager_insert" ON storage.objects;
CREATE POLICY "category_images_manager_insert"
  ON storage.objects
  FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'category-images'
    AND public.current_role() = 'manager'
  );

DROP POLICY IF EXISTS "category_images_manager_update" ON storage.objects;
CREATE POLICY "category_images_manager_update"
  ON storage.objects
  FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'category-images'
    AND public.current_role() = 'manager'
  )
  WITH CHECK (
    bucket_id = 'category-images'
    AND public.current_role() = 'manager'
  );

DROP POLICY IF EXISTS "category_images_manager_delete" ON storage.objects;
CREATE POLICY "category_images_manager_delete"
  ON storage.objects
  FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'category-images'
    AND public.current_role() = 'manager'
  );

COMMENT ON COLUMN public.categories.image_path IS
  'Public URL of the category tile image; NULL falls back to the built-in icon.';
