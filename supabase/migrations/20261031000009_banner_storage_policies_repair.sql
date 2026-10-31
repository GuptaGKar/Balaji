-- Migration: 20261031000009_banner_storage_policies_repair.sql
-- "new row violates row-level security policy" on banner upload means the INSERT policy
-- on storage.objects rejected the caller. The previous policy depended on
-- public.current_role() = 'manager'; a dashboard session whose profile lookup does not
-- resolve (or a staff account) fails that check even though the person is allowed to
-- manage banners.
--
-- These policies use a direct EXISTS against public.profiles instead, accept an active
-- manager OR staff profile, and keep anonymous writes impossible. Public read stays so
-- the storefront can render the images.

BEGIN;

DO $$
DECLARE r record;
BEGIN
  FOR r IN
    SELECT policyname FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND (policyname LIKE 'banner_images%' OR policyname LIKE 'banner-images%')
  LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I ON storage.objects', r.policyname);
  END LOOP;
END $$;

CREATE POLICY "banner_images_public_read"
  ON storage.objects
  FOR SELECT
  TO public
  USING (bucket_id = 'banner-images');

CREATE POLICY "banner_images_staff_insert"
  ON storage.objects
  FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'banner-images'
    AND EXISTS (
      SELECT 1 FROM public.profiles p
      WHERE p.id = auth.uid()
        AND p.is_active
        AND p.role::text IN ('manager', 'staff')
    )
  );

CREATE POLICY "banner_images_staff_update"
  ON storage.objects
  FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'banner-images'
    AND EXISTS (
      SELECT 1 FROM public.profiles p
      WHERE p.id = auth.uid()
        AND p.is_active
        AND p.role::text IN ('manager', 'staff')
    )
  )
  WITH CHECK (
    bucket_id = 'banner-images'
    AND EXISTS (
      SELECT 1 FROM public.profiles p
      WHERE p.id = auth.uid()
        AND p.is_active
        AND p.role::text IN ('manager', 'staff')
    )
  );

CREATE POLICY "banner_images_staff_delete"
  ON storage.objects
  FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'banner-images'
    AND EXISTS (
      SELECT 1 FROM public.profiles p
      WHERE p.id = auth.uid()
        AND p.is_active
        AND p.role::text IN ('manager', 'staff')
    )
  );

-- Confirm the bucket itself still allows 10 MB images.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('banner-images', 'banner-images', true, 10485760,
        ARRAY['image/jpeg', 'image/png', 'image/webp']::text[])
ON CONFLICT (id) DO UPDATE
SET public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

COMMIT;
