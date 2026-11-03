# Google Play readiness

2026-11-02 · **NOT READY**. Compilation and source review do not establish compliance.

| Gate                                                            | Result                                        | Evidence / remaining work                                                                                                                   |
| --------------------------------------------------------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| IDs/version                                                     | PASS source                                   | Customer `com.balaji.provision`, staff `.staff`; existing owner version edit 1.12 (13). Highest Play code unknown                           |
| Target/min SDK                                                  | PASS source requirement check                 | Target 36/min 24; current phone/tablet submission floor is 36                                                                               |
| Debug/cleartext/R8                                              | PASS normal artifact/source                   | Final normal release APKs are non-debuggable; source cleartext false/shrinking enabled; final build/signature checks passed                 |
| APK updater/install permission                                  | FAIL                                          | Grocery app requests REQUEST_INSTALL_PACKAGES and implements direct APK updates. Separate Play route required                               |
| Signing                                                         | BLOCKED final sign-off                        | Existing ignored configuration; no key created/replaced or passwords exposed. Customer debug/release artifact certificates differ           |
| Fresh APK/AAB build                                             | PASS build/signature checks with AAB warnings | Both flavors 1.12 (13); artifact paths/hashes and verification caveats in release checklist                                                 |
| Release-equivalent device flows                                 | PASS listed checks; full matrix NOT TESTED    | Minified isolated QA customer→staff→rider COD lifecycle and browser/customer final status passed; production/Firebase/recovery cases remain |
| 16 KB/native/R8 SDK behavior, links/screens/icons/accessibility | NOT TESTED comprehensively                    | Normal artifacts have no embedded native .so; full SDK/device checks still required                                                         |
| Permissions/SDK declarations/Data Safety                        | BLOCKED final review                          | Location, notifications, Firebase, updater and actual data practices need disclosure review                                                 |
| Legal/account deletion                                          | BLOCKED                                       | Local public pages return 200; public HTTPS URLs, retention/legal text and actual deletion not validated                                    |
| Reviewer access/closed testing/pre-launch                       | BLOCKED                                       | Console/account-type evidence and reviewer test access absent; no upload authorized                                                         |
| Store listing/screenshots/graphics                              | NOT TESTED                                    | Final device/listing review required                                                                                                        |

Official references checked this session:

- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en): phone/tablet submissions require API 36 from August 31, 2026. Target setting meets that floor only.
- [Installation permission policy](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en): permission is restricted to eligible core package-installation purposes. Current grocery-app updater is a release blocker.
- [Account deletion](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en): account-creating apps need in-app and external deletion-request paths.
- [Personal-account testing](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en): applicable new personal accounts need 12 continuously opted-in testers for 14 days. Account applicability unknown.

Staff recommendation: controlled internal distribution suits a small staff/rider audience; assess managed private distribution if organization/device setup supports it. Public staff listing is not the default recommendation. Preserve direct-APK distribution separately from the customer Play route. No upload, policy submission or signing-key replacement occurred.

Independent security blocker: executed production dependency audit reports 1 critical and 2 high package findings for the management website. See `SECURITY_AUDIT_REPORT.md`; customer release readiness also depends on safe backend/management operations.

## Final artifact and device evidence

Final normal customer and staff source each passed135 unit tests (zero failures/errors/skips), lint0 errors/63 warnings/4 hints, debug and signed minified release APK/AAB builds. Both final APK signatures verify v2/v3 with correct version/IDs/target and no debug flag. Both AABs pass cached bundletool validate and jarsigner verification with certificate/timestamp/archive warnings recorded in RELEASE_CHECKLIST.md. No embedded native .so entries were found; broader16KB/SDK runtime coverage is not implied.

Separate minified QA apps completed one local synthetic customer COD checkout→staff processing/assignment/dispatch proof→rider delivery proof/confirmation. The website automatically showed Delivered without reload; customer refreshed history agreed. Proof View and rider create-only upload repairs were retested physically. This is partial release-equivalent evidence: Firebase is excluded, normal production packages were not launched, full recovery/performance/accessibility matrices and actual Play import/pre-launch remain open.

Official references above were checked again on2026-11-02. Current target36 matches the published submission floor. The updater/install permission remains a Play release blocker under the linked policy. No Play Console access, enrollment confirmation, upload or publication occurred. Existing signing configuration is available for local artifacts; Play App Signing ownership/enrollment and highest uploaded version code require owner evidence.
