# Product podium implementation and verification

## Usage

In Catalogue → Products, expand **Bulk Product Image Manager**. Choose up to 30 local PNG/WebP/JPEG files. Processing runs one image at a time. Compare original, product-only PNG, light podium and dark podium; inspect white edges and printed text. Uncheck removal to retain a white package against its original background. Select the exact catalogue product, pack and SKU, then approve/upload/save or reject/next.

Only the 1024×1024 transparent product canvas is uploaded to `product-images`; the existing product row receives only `image_path`. Normal catalogue image uploads remain available. Upload/update authorization uses the existing Supabase session, storage policies and products RLS. No schema or credentials are added. A lost catalogue response can leave an unreferenced uploaded object; files are retained rather than risking deletion of an image whose update committed. On a confirmed save the replaced object is now removed (see the follow-up section), matching what the product form already did.

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

## Follow-up review (2026-11-13)

Review of `0370567` found two defects, both fixed here. No behaviour, price, stock or policy changed.

- **The hero lost its accessible name.** The old `ProductImage` wrapped the thumbnail in
  `clearAndSetSemantics { contentDescription = "Image of ${product.name}" }`; the podium passed
  only `productName`, so TalkBack announced the name twice (title + image) and the drawn platform's
  loading/error text leaked into the tree. `ProductShowcasePodium` now sets one
  `clearAndSetSemantics { contentDescription = "Image of $productName" }` on the stage and leaves
  the image child unlabelled.
- **Approval leaked the replaced storage object.** `approve()` uploaded a new
  `products/<id>/<uuid>.png` and updated `products.image_path` without deleting the previous
  object, while the product form already deletes it. A confirmed save now removes the replaced
  object best-effort. Two guards keep that safe: only URLs under our own public bucket are treated
  as managed (a hand-pasted external URL is never deleted), and a path still referenced by another
  pack in the list is kept. The upload-then-failed-save path still retains its new object on
  purpose. The URL/bucket/limit constants and `managedImagePath` moved to
  `web/src/lib/product-image-url.ts` so the form and the queue cannot drift.

### Checks executed for the follow-up

- `:app:assembleCustomerDebug`, `:app:testCustomerDebugUnitTest` and
  `:app:testStaffDebugUnitTest` (283/283 each, zero failures/errors/skips) and
  `:app:lintCustomerDebug` (0 errors, 69 warnings, 4 hints) on `eac9730` plus these changes.
- `web/node_modules` is absent in this checkout, so `npm test`, `npm run lint`,
  `npm run type-check` and `prettier --check` **cannot run here**; the web changes were reviewed by
  hand and parsed with a standalone `tsc --noResolve` pass, which reports only unresolved imports.
  They are therefore not type-checked, linted or covered by the web suite in this environment.
- Device pass (Galaxy A34, customer account, 9AM Green Chilli - Sauce 180g, one line in the
  basket): the details hero draws the platform, rim, glow and diffused shadow with the package
  bottom on the platform, and it renders in both themes by flipping the profile's dark-mode toggle.
  The hero exposes exactly one accessibility node, `Image of 9AM Green Chilli - Sauce 180g`
  spanning the hero bounds - the duplicate name and the loading text no longer reach TalkBack.
  Screenshots: `.tmp_device_qa/h1.png` (dark), `h2.png` (light), untracked.

### Open finding from the device pass

The catalogue images are JPEGs with white backgrounds (the backfill migration uploads
`products/<SKU>.jpg`), and `TrimProductTransparency` is a no-op when the bitmap has no alpha, so the
hero shows a white rectangle standing on the platform: glaring in dark mode, a visible seam in light
mode. The podium composition is only correct for images produced by the bulk queue. Until those
cutouts exist the hero looks worse than the previous flat thumbnail, so either the catalogue has to
be re-shot through the queue or the podium has to be shown only for processed PNGs. That is a
product decision, not made here.

## Feature files

- `android/app/src/main/java/com/balaji/provision/ui/components/ProductShowcasePodium.kt`
- `android/app/src/main/java/com/balaji/provision/ui/components/TrimProductTransparency.kt`
- `android/app/src/main/java/com/balaji/provision/ui/theme/PodiumColors.kt`
- `android/app/src/main/java/com/balaji/provision/ui/catalog/ProductDetailsScreen.kt`
- `android/app/src/test/java/com/balaji/provision/ui/components/ProductBoundsTest.kt`
- `web/src/components/BulkProductImageManager.tsx`
- `web/src/components/ProductShowcase.module.css`
- `web/src/lib/product-cutout.ts`
- `web/src/lib/product-image-url.ts`
- `web/src/app/(admin)/catalogue/ProductsTab.tsx`
- `scripts/tests/product_cutout.test.cjs`
- `docs/superpowers/plans/2026-11-13-product-podium.md`
- `docs/PRODUCT_PODIUM_QA.md`
- `docs/DECISIONS.md`

Earlier local release/CI edits and the `play asset` folder were preserved; they are not podium changes.
