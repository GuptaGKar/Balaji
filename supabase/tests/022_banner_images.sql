-- pgTAP: banner image bucket (rolls back).

BEGIN;
SELECT plan(3);

SELECT ok(
  EXISTS (SELECT 1 FROM storage.buckets WHERE id = 'banner-images'),
  'banner-images storage bucket exists');

SELECT ok(
  (SELECT public FROM storage.buckets WHERE id = 'banner-images'),
  'banner-images bucket is public-read');

SELECT ok(
  (SELECT file_size_limit FROM storage.buckets WHERE id = 'banner-images') = 2097152,
  'banner-images bucket enforces the 2 MB limit');

SELECT * FROM finish();
ROLLBACK;
