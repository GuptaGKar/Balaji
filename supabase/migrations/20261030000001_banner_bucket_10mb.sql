-- Migration: 20261030000001_banner_bucket_10mb.sql
-- Fixes the banner bucket: create it if the project never had it (uploads then failed
-- with "Bucket not found") and raise the per-file limit from 2 MB to 10 MB, which the
-- dashboard now advertises and validates.

BEGIN;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'banner-images',
  'banner-images',
  true,
  10485760,
  ARRAY['image/jpeg', 'image/png', 'image/webp']::text[]
)
ON CONFLICT (id) DO UPDATE
SET public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

-- Re-assert the policies in case the bucket was recreated on a project where the
-- original migration could not create it.
DROP POLICY IF EXISTS "banner_images_public_read" ON storage.objects;
CREATE POLICY "banner_images_public_read"
  ON storage.objects FOR SELECT
  USING (bucket_id = 'banner-images');

NOTIFY pgrst, 'reload schema';

COMMIT;
