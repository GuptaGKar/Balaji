# M12 audit todo

Updated 2026-11-02. Current release decision: **NOT READY**. Live store untouched; no commits, pushes, deployment or Play publication.

## Completed

- [x] Inspect repository/environment and save baseline.
- [x] Establish wireless ADB, identify the phone, preserve original installed apps/data.
- [x] Enable an isolated full local Supabase stack and synthetic Auth identities.
- [x] Replay migrations/seed on PostgreSQL 17 and a separate fresh PostgreSQL 15.19 database.
- [x] Pass 39 database suites / 416 assertions on both versions.
- [x] Implement and locally test eight forward security/synchronization migrations.
- [x] Pass real Auth/checkout/stock/proof/COD/bill API lifecycle and three concurrent last-stock races.
- [x] Pass authenticated realtime order delivery, 12 staff API checks and 12 media API checks.
- [x] Reproduce public media leakage, close it locally, verify denied anonymous reads and authorized signed downloads.
- [x] Pass web lint/types/format/build, 3 rendered web regressions, 6 Python guard regressions and business guard.
- [x] Build/install separate customer/staff QA apps beside original packages.
- [x] Fix nullable-category decoding; device catalogue now loads.
- [x] Test customer OTP/session/catalogue/packed quantities/cart persistence and synthetic COD checkout/history on minified QA.
- [x] Verify one device order: 19000 paise, one row, stock reduced exactly two packs.
- [x] Test minified staff receipt/items, acceptance, preparation, ready status and synthetic rider assignment.
- [x] Verify missing-proof dispatch rejection.
- [x] Upload synthetic dispatch PNG through Android; verify stored bytes and successful staff dispatch.
- [x] Collect app-scoped memory/frame/crash-buffer snapshots, with no performance improvement claim.
- [x] Build both normal signed release APKs/AABs and verify signatures/non-debuggable flags after both proof fixes (135 tests per flavor); final signatures and bundletool validation pass.
- [x] Create all seven requested audit/release reports and decision log.

## Current work

- [x] Reproduce broken Android proof View action (kind string used as URI; silent catch).
- [x] Implement fresh authorized preview signing, visible loading/error states and duplicate-tap protection.
- [x] Pass 12 targeted preview repository/ViewModel regressions.
- [x] Finish full staff QA tests/lint and debug/minified rebuild with preview fix: 133 tests, zero failures.
- [x] Retest preview on the wireless phone: owner confirmed synthetic PNG opened; returned to QA.
- [x] Rider login and assigned-order display for the same synthetic order.
- [x] Fix rider proof-upload RLS failure: overwrite=true fails; create-only succeeds; red→green regression.
- [x] Rebuild/retest minified rider proof upload and delivery confirmation; downloaded bytes match synthetic PNG.
- [x] Verify server delivered/paid COD19000, active assignment0, stock35→33; customer after refresh agrees.
- [x] Normal preview-fix rebuild/retry passed; transient staff debug packaging failure recorded.
- [x] Rebuild/reverify final normal artifacts:135 tests/flavor, lint0 errors/63 warnings/4 hints; signatures, bundletool, hashes refreshed.
- [x] Update final reports with completed device lifecycle, automatic browser synchronization and remaining gaps.
- [x] Dedicated desktop browser catalogue renders30 rows; same order changes to Delivered without reload, zero sampled unhandled JS exceptions.

## Remaining validation and release gates

- [ ] Complete weighted products, variants, search/sort/filter, address edge cases, cancellation and session-recovery UI cases.
- [ ] Test actual website inventory/settings changes propagating to both Android clients.
- [ ] Test controlled offline/reconnect/background/mobile-switch/slow-network/expired-session and duplicate/out-of-order event cases.
- [ ] Run controlled startup/scroll/memory/battery benchmarks; optimize only reproduced bottlenecks and repeat measurements.
- [ ] Complete browser visual/keyboard/responsive/accessibility end-to-end checks and device accessibility/orientation checks.
- [ ] Resolve management-web dependency gate: **FAIL**, 1 critical / 2 high package findings.
- [ ] Separate customer Play distribution from direct-APK updater/install permission: **FAIL readiness**.
- [ ] Validate actual privacy/deletion/retention/Data Safety/SDK disclosures and public legal URLs with owner evidence.
- [ ] Validate Firebase/push and production configuration in an approved staging/release-equivalent environment.
- [ ] Obtain backup/restore/rollback, operational, reviewer/testing/listing and Play Console evidence.
- [ ] Obtain explicit approval before any commit, push, production change, deployment or Play upload/publication.

## Phase mapping

| Requested phase                   | Status                                                                                                                |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| 1 Discovery/baseline              | PASS                                                                                                                  |
| 2 Wireless setup                  | PASS; physical unlock/reconnection needed when interrupted                                                            |
| 3 Android build/install/workflows | Listed checks PASS; complete matrix NOT TESTED                                                                        |
| 4 Logcat/root-cause debugging     | Reproduced fixes and sampled logs PASS; full scenario matrix NOT TESTED                                               |
| 5 Performance                     | Diagnostic snapshots collected; controlled before/after NOT TESTED                                                    |
| 6 Synchronization                 | Listed SQL/API and recorded complete order device/browser lifecycle PASS; full three-client/failure matrix NOT TESTED |
| 7 Functional audit                | Catalogue fixed/device-tested; preview and rider upload fixed/device-tested; remaining cases NOT TESTED               |
| 8 Security/data integrity         | Listed local checks PASS; dependency gate FAIL; broader operational review incomplete                                 |
| 9 Website                         | Build/HTTP/component checks PASS; catalogue/order browser checks PASS; full E2E NOT TESTED                            |
| 10 Play readiness                 | NOT READY; policy and owner/Console gates remain                                                                      |
| 11 Release validation             | Final signed APK/AAB builds/signatures/bundletool PASS; production/Play runtime gates open                            |
| 12 Reports                        | All requested files updated with latest evidence; broader audit remains incomplete                                    |
