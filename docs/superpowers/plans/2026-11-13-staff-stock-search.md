# Staff Stock Editing and Search Implementation Plan

> Execute natively in this session, one M8 inventory change at a time.

**Goal:** Staff can find any catalogue product and adjust its stock from the native Stock tab.

**Architecture:** Reuse the authenticated `adjust_stock(uuid, bigint, inventory_reason, text)` RPC and its existing transaction, role checks and movement/audit records. Put one search field above both stock lists. Keep quantities as integer grams or packs.

**Tech Stack:** Kotlin, Compose, StateFlow, JUnit, Compose instrumentation.

**Spec:** Owner request; `docs/IMPLEMENTATION_PLAN.md` M8; `docs/README.md` inventory rules.

## Constraints and review focus

- Search must be visible before low-stock alerts and filter names and SKUs in both lists.
- Normal-stock and zero-stock products must expose adjustment controls.
- Malformed, fractional, zero, excessive and overflowing adjustments must not be submitted.
- Catalogue failures must expose retry while retaining previously loaded rows.
- Only server-confirmed writes count as success; prevent repeated submissions while saving.
- No new business policy, permissions, migration, deployment or production access is needed.

## Task 1: Stock state and adjustment validation

**Files:** `ui/manager/ManagerViewModel.kt`, new `ui/manager/StockAdjustment.kt`, matching unit tests under `android/app/src/test/java/com/balaji/provision/ui/manager/`.

- [x] Add failing tests for shared search, catalogue failures, stock input validation and repeated submissions.
- [x] Run the focused staff unit tests and record the missing behavior (initial test compilation failed on absent APIs).
- [x] Implement filtered state, independent catalogue loading, safe integer input validation and server-confirmed adjustment feedback.
- [x] Run unit tests until green: full staff suite 290/290.

## Task 2: Native stock controls

**Files:** `ui/manager/ManagerScreen.kt`, new stock Compose tests under `android/app/src/androidTest/java/com/balaji/provision/ui/manager/`, `docs/DECISIONS.md`.

- [x] Add a search field above both lists, clear action, filtered empty state, catalogue progress/error/retry, stock labels and an Edit Stock action on each catalogue product.
- [x] Reuse the stock dialog with safe validation and keep it open on failed saves, showing the error inside the editor.
- [x] Compile synthetic Compose tests. Also repair the existing `BrowsingControlsTest` action/assertion chaining error exposed by compilation.
- [x] Run synthetic Compose tests on the wireless device after pairing succeeds (all three new `StaffStockScreenTest` cases passed on the SM-A346E; four pre-existing `BrowsingControlsTest` failures reproduced unchanged on a clean HEAD baseline — see `docs/DECISIONS.md`).
- [x] Run Android unit tests/lint/build, web format/lint/typecheck/tests and diff checks; record actual results and unrun gates in `docs/DECISIONS.md`. Web typecheck retains 11 pre-existing form errors.

## Task 3: Split stock and price editing into two pages, each with its own search

Owner request (2026-11-13): stock adjustment and price changes become separate top-level
pages, each with its own search bar. Popup dialogs stay for the actual entry.

**Files:** `ui/manager/ManagerViewModel.kt`, `ui/manager/ManagerScreen.kt`,
`ManagerStockViewModelTest.kt`, `StaffStockScreenTest.kt`, new `StaffPricesScreenTest.kt`,
`docs/DECISIONS.md`.

- [x] Add failing tests: independent price-search filtering (unit), PricesTab search/filter/clear, Edit Price hand-off, catalogue error retry, stock page offers stock editing only, stock editor has no price mode.
- [x] Record the red state (unit compilation failed on absent `setPriceSearch` / `filteredPriceProducts`).
- [x] Implement: fifth top-level `Prices` tab with `priceSearchQuery`/`filteredPriceProducts`, stock page loses its price action, `AdjustStockDialog` becomes stock-only (price chip and price inputs removed), catalogue tabs no longer blocked by order loading/error.
- [x] Run unit tests green: 291/291.
- [x] Run the Compose tests on the wireless device: 8/8 passed for the `ui.manager` package (`StaffPricesScreenTest` 3, `StaffStockScreenTest` 5) on the SM-A346E over IP wireless debugging. The search test's original `assertDoesNotExist("Rice")` failed on a non-rendered Compose 1.5.4 semantics ghost node (screenshot-verified: only the matching card is drawn), so the test now asserts rendered rows, the empty state and the state-level filter result instead; reason recorded in the test and in `docs/DECISIONS.md`.
- [x] Run lint/build/diff checks and record results in `docs/DECISIONS.md`: `lintStaffDebug` 0 errors/70 warnings/5 hints, unit 291/291, release lintVital green, `assembleCustomerRelease` + `assembleStaffRelease` built, web format/lint/tests green with the 11 pre-existing typecheck errors, `git diff --check` clean. QA/local-audit tooling deleted and both apps reinstalled fresh as v16/1.15.
