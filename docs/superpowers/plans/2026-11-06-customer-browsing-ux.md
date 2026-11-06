# Customer Browsing UX Implementation Plan

> Execution: native implementation in this session, under the user's request to analyze and improve the Android customer app.

**Goal:** Make finding products and adjusting a basket clearer on small screens and with larger text.

**Architecture:** Improve the existing Compose catalogue and shared cards without changing repositories that calculate prices or submit orders. Keep cached catalogue content visible with an actionable refresh status. Use the existing orange/glass theme.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, StateFlow, JUnit, Compose UI tests.

**Spec:** `docs/IMPLEMENTATION_PLAN.md` §8 and M5; pack UX/accessibility and QA requirements; current user request.

## Current state and constraints

- Branch `feature/customer-orange-glass-v3`, base `aae8b36`; no tracked edits at start. Existing untracked screenshots/design files are preserved.
- Search clear control overlaps the text field and is only 20 dp. IME Search has an empty handler.
- Price, MRP and ADD compete in a narrow card; the card changes height when first added.
- Listing controls duplicate the stock filter. Cached refresh errors are hidden by the nonempty product branch; a stock-filtered empty list offers no reset.
- Listing column count uses screen width without content padding or font scale.
- Work stays in M5. No schema or policy changes, deployments, production access or releases. Existing accepted orange contrast and pending variant policy remain documented.
- No attached Android device at audit start. Device-dependent checks must be labelled not run unless an emulator/device becomes available.

## Review focus

- A failed refresh keeps cached products, search and category selection, and offers retry.
- A filter producing zero results has a direct recovery action before suggesting an item request.
- Large type and long prices retain separate space from purchase controls.
- Clear search is independently labelled and does not cover the editable text.
- ADD, quantity controls and card height remain usable across zero/positive quantity and stock changes.

## Task 1: Catalogue state and recovery

Files: `ui/catalog/ProductViewModel.kt`, `ui/catalog/CatalogScreen.kt`, `ui/catalog/CatalogPresentation.kt`, JVM tests under `ui/catalog/`.

- [ ] Add regression tests for cached refresh, recovery action precedence and responsive grid sizing; execute before implementation.
- [ ] Introduce explicit refresh state, a visible cached-error/retry panel, meaningful filter/sort labels and result counts, and recovery from empty filters.
- [ ] Use available layout width and font-aware adaptive card size; preserve scroll state and category/search navigation.

## Task 2: Search and product controls

Files: `ui/components/StorefrontComponents.kt`, `ui/components/AddQuantityControl.kt`, Compose UI tests under `src/androidTest/`.

- [ ] Give search its own clear-button slot and 48 dp target; label the field and dismiss the keyboard on IME Search.
- [ ] Separate price and purchase controls, keep purchase rows stable, wrap price/MRP when needed, and scale horizontal card width with text.
- [ ] Give ADD and quantity controls proper touch targets and product-specific accessibility labels; prevent increasing unavailable products while allowing removal.
- [ ] Add Compose regressions for search geometry/clear, card height stability and disabled increment. Execute if a test device is available; otherwise compile and report the runtime gap.

## Task 3: Verification and report

- [ ] Run both Android flavor JVM suites, Kotlin compilation, lint, debug builds, and instrumentation test compilation.
- [ ] Run repository format/lint/typecheck/test commands and guards, recording failures separately.
- [ ] Review the final diff; update `docs/DECISIONS.md` and write an audit with actual results and remaining visual-validation risks.
