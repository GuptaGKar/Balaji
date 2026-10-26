-- pgTAP: category image column and public bucket (rolls back).

BEGIN;
SELECT plan(3);

SELECT has_column(
  'public', 'categories', 'image_path',
  'categories has an image_path column');

SELECT ok(
  EXISTS (SELECT 1 FROM storage.buckets WHERE id = 'category-images'),
  'category-images storage bucket exists');

SELECT ok(
  (SELECT public FROM storage.buckets WHERE id = 'category-images'),
  'category-images bucket is public-read');

SELECT * FROM finish();
ROLLBACK;
