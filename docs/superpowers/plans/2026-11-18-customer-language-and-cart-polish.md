# Customer language and cart polish — working plan

> Started 2026-11-18. One customer-app pass; no backend, migration or policy change.

## Requested changes

1. Language option: Hindi / English.
2. Remove SKU from the customer product page.
3. Open a product from the cart by tapping its image or name.
4. Stop the ADD control overlapping the price on product cards.
5. Show the grouped pack sizes in the catalogue display, not just a count.
6. Price sorting, ascending and descending.

## Status

### Done

- [x] **SKU removed** from `ui/catalog/ProductDetailsScreen.kt` (`AttributeBlock`).
- [x] **Cart → product** navigation: `CartScreen` / `CartItemRow` accept
      `onOpenProduct`, the thumbnail and product name are clickable, and
      `MainActivity` routes to `product/{productId}`. The stepper and remove
      controls keep their own touch targets.
- [x] **ADD / price overlap**: `ProductGridCard` now uses `priceStyleFor(...)`
      (the helper that was already used by the horizontal rail card) and keeps
      4 dp between the price column and the ADD control.
- [x] **Grouped packs shown**: both card types now render `group.packSummary`
      (for example `500 ml · 1 L · 2 L`) instead of only "3 packs".
- [x] **Price sort** confirmed in the catalogue sort menu and made visible:
      the Sort pill now shows `Price ↑` / `Price ↓` / `A–Z` while active.
- [x] **Language plumbing**:
      - `AppLanguage` (English / हिंदी), `AppStrings`, `HindiStrings`,
        `HindiDynamicStrings`, `LocalizedText`.
      - `LanguageViewModel` persists the choice in the existing DataStore
        (`customer_app_language`).
      - `MainActivity` provides the selected language to the customer shell.
      - Profile has a Language card with English / हिंदी filter chips.
      - Customer screens import `LocalizedText as Text`, so labels, buttons,
        dialogs and empty states translate at one point.
      - Static content descriptions now go through `tr(...)`.
      - Composed labels (`Your Cart (3)`, `Add ₹40 more…`, `25% OFF`) have
        regex translations; store-entered names and amounts pass through.
- [x] Unit test added: `AppStringsTest` (English pass-through, core copy,
      store data untouched, composed labels, non-blank entries).

### Verification

- [x] `AppStringsTest` run: `:app:testCustomerDebugUnitTest --tests AppStringsTest`
      → BUILD SUCCESSFUL (focused run only).
- [x] First full gate before the copy sweep: `testCustomerDebugUnitTest`,
      `lintCustomerDebug`, `assembleCustomerDebug`,
      `compileCustomerDebugAndroidTestKotlin` → BUILD SUCCESSFUL in 4m18s.
- [x] Re-run after the copy sweep: `testCustomerDebugUnitTest` 297 tests,
      0 failures/errors/skips; `lintCustomerDebug` 0 errors, 70 warnings,
      5 hints; `assembleCustomerDebug` built the APK;
      `compileCustomerDebugAndroidTestKotlin` compiled. BUILD SUCCESSFUL in 3m30s.
- [x] Scan all customer screens for untranslated copy; every missing static
      string found by the scan is now in `HindiStrings`, and the missing
      semantics labels (`Open <product>`, `View basket`, `Not added`,
      `Out of stock`, `<n> in basket`, map/address labels) go through `tr(...)`.
      Remaining English literals are intentional: brand names, a sample phone
      number, units and store-entered text.
- [x] Hindi wrapping: the product card name (`maxLines = 2`), pack label and pack
      summary (`maxLines = 1`) and the cart row name (`maxLines = 2`) already
      ellipsise; the added Hindi copy fits those slots. Devanagari line-height
      clipping at small widths is **not device-verified** (no device attached).
- [x] Decision recorded in `docs/DECISIONS.md`.
- [ ] Decide whether the language card belongs only under Profile or also in a
      first-run/onboarding step (current: Profile only). Open question for the
      owner, not blocking.
- [x] No new permission or SDK, so `store-assets/play/data-safety.md` is
      unchanged.

### Podium removal (owner request, 2026-11-21)

- [x] Mirrored `master`'s `e373058`: deleted `ProductShowcasePodium.kt`,
      `TrimProductTransparency.kt`, `PodiumColors.kt`, `ProductBoundsTest.kt`,
      the web bulk-image queue and cutout pipeline, and the two podium docs.
- [x] `ProductDetailsScreen.ProductImage` restored to the flat hero, with the
      `Image of <product>` accessible name kept and resolved outside the
      semantics lambda.
- [x] Feature tiles translated and a saved-address label left raw; both were
      device findings from the Hindi pass.

### Device checks (SM-A346E, release-signed debug build)

- [x] English home, Hindi switch under Profile, Hindi home, categories,
      catalogue, sort menu and price-desc order: captured and read.
- [x] ADD no longer overlaps the price (₹105.00 / जोड़ें separated).
- [x] Grouped pack sizes render from the store's own variant labels
      (`Rs. 10 · Rs 20`).
- [x] Sort pill shows `कीमत ↓` and the list reorders (₹20 before ₹10).
- [ ] Cart row, cart → product navigation and the product page itself were not
      captured before the wireless-debugging connection dropped.
- [ ] After the sort, the ₹105 item was not in the visible dump; scroll-to-top
      re-check still open.
- [ ] Hindi text on the flat product hero and the two-line capability tiles is
      not yet re-checked on the device after the podium removal.

## Files touched

- `android/app/src/main/java/com/balaji/provision/MainActivity.kt`
- `android/app/src/main/java/com/balaji/provision/core/AppModule.kt`
- `android/app/src/main/java/com/balaji/provision/ui/i18n/` (new package)
- `android/app/src/main/java/com/balaji/provision/ui/profile/ProfileScreen.kt`
- `android/app/src/main/java/com/balaji/provision/ui/catalog/CatalogScreen.kt`
- `android/app/src/main/java/com/balaji/provision/ui/catalog/ProductDetailsScreen.kt`
- `android/app/src/main/java/com/balaji/provision/ui/cart/CartScreen.kt`
- `android/app/src/main/java/com/balaji/provision/ui/components/StorefrontComponents.kt`
- customer UI files switched to `LocalizedText as Text` (account, auth, cart,
  catalog, categories, checkout, components, favourites, home, onboarding,
  orders, profile, requests, session, splash)
- `android/app/src/test/java/com/balaji/provision/ui/i18n/AppStringsTest.kt`

## Constraints

- No pricing, stock, order or authorization logic changes.
- No new dependency, permission or SDK.
- Product names, descriptions, category names and manager-composed section
  titles are shown exactly as entered.
- Hindi translations are UI copy only; legal text and staff/manager tooling
  remain English until separately reviewed.
