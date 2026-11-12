/**
 * Catalogue image storage, shared by the product form and the bulk approval queue so both agree
 * on which objects this app owns. Only URLs under our own public bucket are treated as managed:
 * a hand-pasted external URL must never be deleted when a product's image is replaced.
 */
export const PRODUCT_IMAGE_BUCKET = "product-images";
export const PRODUCT_IMAGE_MAX_BYTES = 2 * 1024 * 1024;
export const PRODUCT_IMAGE_URL_PREFIX = `${(process.env.NEXT_PUBLIC_SUPABASE_URL || "").replace(
  /\/$/,
  "",
)}/storage/v1/object/public/${PRODUCT_IMAGE_BUCKET}/`;

/** Storage object path for a managed public URL, or null for external/absent URLs. */
export function managedImagePath(url: string | null | undefined): string | null {
  if (!url || !url.startsWith(PRODUCT_IMAGE_URL_PREFIX)) return null;
  return decodeURIComponent(url.slice(PRODUCT_IMAGE_URL_PREFIX.length).split("?")[0]);
}
