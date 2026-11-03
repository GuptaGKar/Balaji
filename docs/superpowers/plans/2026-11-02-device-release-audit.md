# Device and release audit implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish current evidence, repair reproduced defects and prepare a reviewable release decision.

**Architecture:** M12 hardening only; preserve the shared backend and server-side business rules. Use isolated fixtures and an owner-confirmed staging backend for mutable tests. Execute directly in this session as requested by the owner; no commits without approval.

**Tech Stack:** Kotlin/Compose, Gradle, Next.js/TypeScript, Supabase/Postgres, Python unittest, ADB.

**Current todo:** [M12_TODO.md](../../M12_TODO.md).

**Spec:** Owner's twelve-phase device/release request; `docs/IMPLEMENTATION_PLAN.md`, `AGENTS.md`, `BASELINE_REPORT.md`.

## Global constraints

- No minimum order; integer paise and base quantities; server-authoritative pricing/stock/authorization.
- Online payments stay disabled until M11 passes; no new business policy.
- No production access, data deletion, deployment, commit, push, signing-key creation or publishing.
- Preserve pre-existing version bump and installed app data.
- M12 implementation approved 2026-11-02. Missing staging credentials, physical interaction and signature incompatibility must remain explicit blockers.

## Review focus

- Guard documentation exemptions must not hide executable forbidden fields: isolated fixture tests.
- Debug/release signature differences must not cause destructive reinstall: compare certificates before installation.
- Live-backend defaults must not lead to real writes: establish staging before app launch or authenticated tests.
- Missing runtime infrastructure must not become false passes: report database/device/web test gaps separately.
- Realtime loss must reconcile from the backend: network/lifecycle matrix only after isolated backend access.

### Task 1: Baseline and connectivity

**Files:** `BASELINE_REPORT.md`, requested report files, `docs/DECISIONS.md`.

- [x] Inspect build/CI/test structure and environment; preserve existing changes.
- [x] Run ADB discovery and read authorized device properties; omit device serial/pairing secrets from reports.
- [x] Save pre-fix baseline.

### Task 2: Reproduced local check failures

**Files:** `scripts/tests/test_check_guards.py`, `scripts/check_guards.sh`, `.github/workflows/ci.yml`, `web/src/app/(admin)/service-areas/page.tsx`, `web/src/app/(admin)/coupons/page.tsx`.

- [x] Reproduce web format and business guard failures.
- [x] Write isolated guard cases; execute and observe the SQL documentation case fail.
- [x] Exempt SQL-only comments from the concept scan; preserve rejection of executable fields and threshold constants. Reword UI explanation without changing its policy.
- [x] Run Python guard cases and repository guard; format flagged coupons page and run web lint/types/format again.
- [x] Run both flavors' unit tests, lint and debug builds. Record executed counts and errors. Reproduced notification MissingPermission lint failure; fixed explicit permission check/narrow catch; final lint and builds pass.

### Task 3: Device, synchronization and security validation

Resumed after Docker enabled: add opt-in `-Pbalaji.localAudit=true` builds with separate `.audit` identities/output directories, explicit loopback backend/key, audit-only network policy and disabled Firebase initialization/receiver. Files: `android/app/build.gradle.kts`, `android/app/src/localAudit/AndroidManifest.xml`, `android/app/src/localAudit/res/xml/local_audit_network_security.xml`, `scripts/check_local_audit_apk.py`. Existing production APK failed the artifact safety check before implementation, as expected. Build audit artifacts, inspect identities/endpoints/manifest, then install without replacing either existing app. Full push delivery is excluded from this local configuration and must remain an untested gate.

**Files:** `DEVICE_TEST_REPORT.md`, `BUG_REPORT.md`, `PERFORMANCE_REPORT.md`, `SYNC_AUDIT_REPORT.md`, `SECURITY_AUDIT_REPORT.md`; implementation files only when an issue is reproduced and regression test identified.

- [x] Establish isolated backend/test accounts; inspect identities/signatures before QA installs.
- [ ] Capture redacted crash evidence and test customer/staff lifecycle, permission boundaries and network recovery against synthetic data.
- [ ] Measure repeated startup/frame/memory baselines under recorded conditions; optimize only measured bottlenecks and repeat the same measurement.
- [x] Fresh migration/seed replay and 39 suites/416 assertions on PostgreSQL 15.19 and 17.11; actual Auth/REST/Storage/Realtime checks.
- [ ] Test inventory/order/settings updates across all three clients, reconnection and concurrent last-stock orders.

### Task 4: Release readiness

**Files:** `PLAYSTORE_READINESS.md`, `RELEASE_CHECKLIST.md`, all audit reports, `docs/DECISIONS.md`.

- [x] Consult current official Play requirements; assess customer and staff distribution separately.
- [x] Build both flavors' minified release APK/AAB artifacts with existing configuration and signing; verify signatures without exposing secrets/replacing keys. Separate local minified QA customer checkout and staff processing/dispatch now tested; full release/Firebase/Play import remains open. Final post-preview/upload artifacts rebuilt, signatures and bundletool validation pass;135 tests per flavor.
- [x] Mark each gate PASS/FAIL/BLOCKED/NOT TESTED and produce READY/NOT READY/BLOCKED with remaining risks. Current decision NOT READY; remaining tasks are explicitly blocked/unexecuted. No upload or publishing.

### M12 follow-up: reproduced private-media leak

Synthetic PNGs in both order buckets were returned with HTTP 200 without authentication. Replace public reads with active ownership/assignment rules, keep unrelated catalogue media public, and route web image/link requests through cookie-authenticated short-lived Storage signing. Android source has no private-media public URL rendering path in the inspected files; uploads remain unchanged. Files: new `20261102000008_private_order_media.sql`, `039_private_order_media.sql`, `web/src/app/api/order-media/route.ts`, the orders/requests URL helpers, and local HTTP/role tests. Verify denied anonymous/wrong-rider access and allowed customer/staff/assigned-rider reads. No live migration/deployment.

### M12 device reproduction: nullable catalogue category

Wireless QA reached Home, but catalogue GETs returned HTTP 200 while the UI showed refresh failure. The real products table permits category_id NULL; ProductResponse required String and the serializer regression failed with JsonDecodingException. Change only the DTO to nullable and map uncategorized rows to the domain's existing empty category ID. Files: SupabaseApi.kt, ProductResponseSerializationTest.kt. Run both flavors' tests/lint/build; reinstall only .audit packages without clearing data, then retry catalogue and exercise cart.

### M12 minified local runtime validation

Build audit release APKs with R8/resource shrinking, audit loopback/Firebase overlay and the existing QA debug signing config so they update .audit installs without uninstall/data loss. This is a release-equivalent code-path test, not a Play-signing/Firebase certification. Normal release uses the existing release config. Rebuild both flavors, inspect identities/non-debuggable flags/signatures, install QA-only and exercise catalogue/login/order paths. No key is created or overwritten.

Verified before the preview repair: 121 Android tests/flavor, normal + audit lint/build and release signatures. Device customer COD checkout/history and staff acceptance/preparing/ready/assignment/PNG proof/dispatch passed. Preview repair adds12 targeted passing tests; upload repair adds2; final full135-test suites/artifacts pass, physical viewer/upload/delivery retested. Remaining full UI/failure/performance/Play/operational gates are explicitly open; NOT READY remains the decision.

### M12 reproduced Android proof preview defect

Actual View tap stays on the same sheet: proof state stores stage→kind, but ManagerScreen passes kind (image) to ACTION_VIEW and silently catches failure. Keep kind metadata for gates; request a fresh authorized 60-second Storage signed URL on each View action, expose loading/error state and catch only viewer-availability/security failures with a visible message. Files: SupabaseApi.kt, RequestsRepository.kt, OrderProofViewModel.kt, ManagerScreen.kt, repository/ViewModel regression tests. No new business rules or migration required: private Storage RLS already enforces reads. Rebuild/test normal and isolated minified staff artifacts; retest preview/device flow.

### M12 rider proof upload RLS reproduction

Owner's rider PNG selection failed with new-row RLS error. Same synthetic identity/file, fresh object paths: x-upsert=true returns HTTP400/RLS; x-upsert=false returns HTTP200. Android defaults overwrite=true despite generating a new UUID path. Preserve restrictive private-media policies; change only proof upload's default header to create-only. Add upload/retake regression and rejection mapping tests, rebuild staff QA and retest physical upload/delivery. Previous preview-build results predate this upload fix.

### Final recorded-order evidence

The planned synthetic happy path is complete: customer checkout, staff processing/assignment/dispatch proof, rider proof/delivery/COD, automatic website update and refreshed customer Delivered agree. Final normal APK/AABs rebuilt and verified;135 tests/flavor,39 SQL suites/416 assertions on PG17 and fresh PG15.19, web/guard regressions pass. Controlled performance/full failure/inventory/settings/operational/Play gates remain open as listed in docs/M12_TODO.md; no complete-production-readiness claim.
