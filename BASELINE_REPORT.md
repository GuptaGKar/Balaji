# Repository and environment baseline

Date: 2026-11-02. Milestone: M12 (implementation approved during this session).
Scope: current discovery and local checks, before audit fixes. Historical QA is not a pass for this run.

## Repository

- HEAD: `14f9794`. Pre-existing user edit: Android version 1.11 (12) → 1.12 (13); preserve it.
- Android: one `:app` module, customer/staff flavors, debug/release build types. IDs: `com.balaji.provision` and `com.balaji.provision.staff`. No debug ID suffix, so debug cannot be assumed to install over release signatures.
- Gradle wrapper 8.13; AGP 8.13.2; Kotlin 1.9.21; Compose UI 1.5.4/compiler 1.5.6; compile/target SDK 36, minimum 24, Java bytecode 17. Hilt, ViewModels, StateFlow and Compose navigation; Retrofit/OkHttp access to Supabase REST/Auth/RPC, Coil images, persisted cart via DataStore, encrypted session preferences.
- Shared flavor-aware activity routes customer, staff and rider flows. Catalogue uses an in-memory repository; refresh/poll/push paths need lifecycle and reconciliation testing. Web orders use a Postgres Changes subscription.
- Web: Next.js App Router 14, React 18, TypeScript, Supabase JS/SSR. Administrative, billing/POS, rider, legal and account-deletion routes. Server administrative client lives in `web/src/server/supabaseAdmin.ts`. `next.config.js` supplies security headers. No dedicated hosting deployment configuration identified in the inspected files.
- Backend: ordered migrations, synthetic seed, pgTAP suites through 032, Edge Functions; archived pre-plan SQL is not authoritative. Latest applied state is unknown in this session. A historical migration has a 15-digit prefix; do not rename applied files.
- CI: web lint/types/format/build, business guards, Supabase DB tests, Android unit/lint/debug builds. No Android instrumentation source directory or web test script found. CI workflow inspection is not proof CI passed.
- `QA_LOG.md`, `docs/PROJECT_STATUS.md`, `docs/PLAY_STORE_RELEASE.md` and release artifacts describe older runs; their version/device/backend claims require fresh checks.

## Environment (executed)

| Item                   | Observed                                                                                           |
| ---------------------- | -------------------------------------------------------------------------------------------------- |
| JDK                    | OpenJDK 21.0.12.1                                                                                  |
| Node / npm             | 22.23.3 / 10.9.9                                                                                   |
| Git                    | 2.43.0                                                                                             |
| CPU / memory           | 4 CPUs, 7.7 GiB RAM, 4 GiB swap                                                                    |
| Free disk              | 128 GiB                                                                                            |
| SDK                    | `/home/linuai/gate_prep/Android/Sdk`; platforms 34, 36, 37.0; build-tools 34/35/36                 |
| ADB                    | 1.0.41, platform-tools 37.0.1; installed but absent from PATH; absolute path works                 |
| Gradle                 | Project wrapper available; no standalone Gradle on PATH                                            |
| SDK command-line tools | No `cmdline-tools` directory in configured SDK                                                     |
| Supabase CLI           | 2.121.0                                                                                            |
| Database               | Docker client 29.9.1; daemon socket access denied; no psql on PATH                                 |
| Sandbox                | Fails before commands with `bwrap: loopback: Failed RTM_NEWADDR`; approved commands run outside it |

## Physical device (executed)

Wireless ADB authorized after owner connected it. Samsung SM-A346E, Android 16/API 36, 1080×2340, density 450. Both packages installed at 1.11 (12), target 36, without DEBUGGABLE in inspected package flags. No apps installed, uninstalled, launched or cleared during discovery. No pairing credentials collected.

## Baseline checks

| Check                              | Result                                                                            |
| ---------------------------------- | --------------------------------------------------------------------------------- |
| Web lint                           | PASS, with existing hook dependency warnings in inventory:136 and reports:122     |
| Web TypeScript                     | PASS                                                                              |
| Web format                         | FAIL: coupons/page.tsx                                                            |
| Business guard                     | FAIL: rejects SQL comment and UI copy stating below-threshold ordering is allowed |
| Android unit/lint/debug builds     | Running; final outcome to be recorded separately                                  |
| Database tests                     | BLOCKED: Docker socket denied, no local Postgres                                  |
| Authenticated device/web flows     | BLOCKED: isolated backend/test identities not established                         |
| Performance and release validation | NOT TESTED                                                                        |

## Execution plan and constraints

M12 only. Save baseline, reproduce failures, add focused guard regression coverage, fix the guard false positive without editing applied SQL, format the flagged page, run both flavors' checks, then test against an approved isolated backend. Capture sanitized device evidence and measure performance before optimizing. Check current official Play rules before release conclusions. Update the seven requested reports and decisions with PASS/FAIL/BLOCKED/NOT TESTED and exact evidence. No production access, deployment, commit, key creation or publication authorized.

Initial files: this baseline, seven requested root reports, `docs/DECISIONS.md`, `docs/superpowers/plans/2026-11-02-device-release-audit.md`, `scripts/check_guards.sh`, `scripts/tests/test_check_guards.py`, `web/src/app/(admin)/coupons/page.tsx`, `web/src/app/(admin)/service-areas/page.tsx`. Additional fixes require reproduced evidence and a focused update; business policy remains unchanged.

## Subsequent execution (baseline above remains historical)

Docker was enabled by the owner. The audit subsequently started an isolated PostgreSQL 17 full Supabase stack, created synthetic local Auth identities, applied eight new forward migrations and passed 39 suites/416 assertions. Both separate QA apps installed beside originals; customer local OTP login and subsequent session-restored Home were observed. Catalogue failure on nullable categories was reproduced during wireless retesting. See current reports for its final status. Original packages and live backend remain untouched. PostgreSQL 15 compatibility is not tested.
