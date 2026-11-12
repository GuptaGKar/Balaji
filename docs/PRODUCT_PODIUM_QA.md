# Product podium implementation and verification

## Usage

In Catalogue → Products, expand **Bulk Product Image Manager**. Choose up to 30 local PNG/WebP/JPEG files. Processing runs one image at a time. Compare original, product-only PNG, light podium and dark podium; inspect white edges and printed text. Uncheck removal to retain a white package against its original background. Select the exact catalogue product, pack and SKU, then approve/upload/save or reject/next.

Only the 1024×1024 transparent product canvas is uploaded to `product-images`; the existing product row receives only `image_path`. Normal catalogue image uploads remain available. Upload/update authorization uses the existing Supabase session, storage policies and products RLS. No schema or credentials are added. A lost catalogue response can leave an unreferenced uploaded object; files are retained rather than risking deletion of an image whose update committed.

Android `ProductShowcasePodium` draws independent static Canvas glow, diffused oval shadows, platform edge, top and rim. Coil fits a display-only alpha-trimmed product over the platform. Trimming participates in Coil's cache and does not rewrite stored images. Only the details hero uses the podium. Existing cards/cart/orders/categories remain unchanged. No animation, blur pass, OpenGL, new image dependency or paid background-removal service is introduced.

## Scope and limits

Background removal is an edge-connected near-white flood fill, not semantic segmentation. It preserves enclosed white labels and RGB values but cannot distinguish white package edges connected to white backgrounds. Managers must inspect and approve; use keep-background or an externally prepared transparent cutout for difficult sources. Package text and outlines are never generated or reconstructed.

The supplied reference `Changes/Balaji Provision Store App Redesign.png` was inspected: its details hero has a broad shallow ellipse, warm highlighted rim, subtle side depth and a bottom-aligned package. The implementation follows that composition, with theme-specific tokens and restrained orange lighting. This source review is **not** a running-app screenshot comparison.

## Checks executed

- Product-cutout regression tests cover border-connected removal, enclosed white branding, transparent input, bounds and invisible-result rejection. Full web suite: 51 tests passed.
- Prettier on all changed web modules/tests: passed. Full web lint completed with existing warnings; final new-module lint: no warnings/errors.
- Full web typecheck is blocked by 11 existing React Hook Form/Zod errors across CategoriesTab, ProductsTab, service areas and settings. Compiler diagnostics compared with the pulled ProductsTab baseline: no added or removed diagnostics. The comparison API additionally reports the same incremental-options diagnostic in both runs.
- Android compile, lint and unit tests completed: BUILD SUCCESSFUL, 283 tests with zero failures/errors. New shape test covers atta bag, pouch, bottle, chips packet, biscuits, rice bag, soap box and loose-grocery proportions using synthetic alpha geometry, plus empty and opaque-white inputs.

## Not run / acceptance still pending

`adb devices` returned no connected device. Running-app screenshots, reference side-by-side comparison, frame/CPU/GPU measurements and light/dark loading/offline interaction checks on entry-level hardware are **not run**. Real branded eight-product visual checks and authenticated local Supabase upload/approval integration are **not run**: no local test catalogue/assets/session were available, and production credentials/data were not used. No release or deployment performed. These acceptance criteria remain open; this feature is not certified visually complete.

Connect a device with a local test backend/catalogue. Capture details in both themes for Aashirvaad Atta 5 kg, milk pouch, oil bottle, chips, biscuits, rice, soap and loose groceries. Compare package bottom to the platform at 77% hero height; ensure no crop/stretch and inspect loading/error/offline states. Test approval, rejection, switching sources and network failure between upload/save. Download the approved object and inspect it on a checkerboard: there must be product pixels only. Compare card/cart/order screenshots to confirm no podium appears there. Measure frame and memory behavior while entering/leaving details; lighting must remain static.

## Feature files

- `android/app/src/main/java/com/balaji/provision/ui/components/ProductShowcasePodium.kt`
- `android/app/src/main/java/com/balaji/provision/ui/components/TrimProductTransparency.kt`
- `android/app/src/main/java/com/balaji/provision/ui/theme/PodiumColors.kt`
- `android/app/src/main/java/com/balaji/provision/ui/catalog/ProductDetailsScreen.kt`
- `android/app/src/test/java/com/balaji/provision/ui/components/ProductBoundsTest.kt`
- `web/src/components/BulkProductImageManager.tsx`
- `web/src/components/ProductShowcase.module.css`
- `web/src/lib/product-cutout.ts`
- `web/src/app/(admin)/catalogue/ProductsTab.tsx`
- `scripts/tests/product_cutout.test.cjs`
- `docs/superpowers/plans/2026-11-13-product-podium.md`
- `docs/PRODUCT_PODIUM_QA.md`
- `docs/DECISIONS.md`

Earlier local release/CI edits and the `play asset` folder were preserved; they are not podium changes.
