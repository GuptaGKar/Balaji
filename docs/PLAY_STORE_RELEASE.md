# Google Play release guide — customer and staff apps

This is the release checklist for `com.balaji.provision` (customer) and
`com.balaji.provision.staff` (staff). It records the built release candidate and
the external steps that must be completed before upload. It is not evidence that
either app has been published or that the backend is production-ready.

## Current release candidate

| Item | Customer | Staff |
|---|---|---|
| Package | `com.balaji.provision` | `com.balaji.provision.staff` |
| Version | 1.6 (7) | 1.6 (7) |
| Target SDK | 36 (Android 16) | 36 (Android 16) |
| Min SDK | 24 | 24 |
| Release AAB | `android/app/build/outputs/bundle/customerRelease/app-customer-release.aab` | `android/app/build/outputs/bundle/staffRelease/app-staff-release.aab` |
| Upload key | `~/.config/balaji/balaji-upload.jks` | same upload key |

- Keystore alias: `balaji-upload`
- Certificate SHA-256:
  `8A:6B:C8:2D:4A:3C:64:00:78:70:FB:D6:40:49:8F:CF:B8:09:DD:50:B9:D2:AB:64:F6:C0:FE:19:85:F7:D6:4D`
- Signing credentials: `android/keystore.properties` (git-ignored). Back up the
  keystore and this file in a secure password manager before the first upload.
- R8 minification and resource shrinking are enabled for release. Test the
  release build, not only debug.
- R8 mapping files: `releases/mapping-customer-1.6.txt.gz` and
  `releases/mapping-staff-1.6.txt.gz` (keep these with the uploaded version for
  crash deobfuscation).
- The staff manifest removes location permissions; only the customer app asks
  for optional location access from the address editor.

## What changed in 1.6

- Category images uploaded by managers now render in the customer Android app.
- Web product management supports pack-size groups while keeping every SKU
  globally unique.
- Staff order details no longer fail because of an invalid `created_at`
  ordering request on `order_items`.
- Staff and managers can remove unavailable items before dispatch with a
  required reason; the server recalculates stock and totals.

Category images and multi-pack groups require the corresponding database
migrations below. Without them the app still runs, but those features stay
dormant or unavailable.

## Build commands

```bash
cd android
bash gradlew :app:bundleCustomerRelease :app:bundleStaffRelease
bash gradlew :app:assembleCustomerRelease :app:assembleStaffRelease
```

The current app versions bundle the legal text, so no web URL is needed to open
privacy, terms or deletion help inside either app. The deployed public domain is
still required in Play Console for the privacy-policy and account-deletion URLs.
(`STORE_WEB_URL` is a reserved build setting and is not consumed by the current
in-app legal screens.)

## Hard blockers before upload

1. **Backend migrations are not applied or verified.** Apply pending migrations
   in timestamp order, especially:
   - `20261023000001_product_variant_grouping.sql`
   - `20261023000002_billing_pos.sql`
   - `20261023000003_account_deletion.sql`
   - `20261023000004_category_images.sql`

   Run `supabase/tests/018_billing_pos.sql`,
   `supabase/tests/019_account_deletion.sql` (19 assertions) and
   `supabase/tests/020_category_images.sql` (3 assertions) against the migrated
   database. Earlier M9/M10 migrations in `supabase/migrations/` may also be
   pending. Product grouping and category-image writes depend on 000001 and
   000004 respectively.
2. **SMS/OTP is not verified.** Customer sign-in depends on Supabase phone auth.
   Configure an SMS provider and test Indian numbers. For Play review, configure
   a Supabase test phone number and fixed OTP; write the exact steps in App
   access.
3. **No public HTTPS web URL.** Deploy the Next.js app, set `APP_BASE_URL`, and
   verify `/privacy`, `/terms` and `/account-deletion` without authentication.
   Enter those URLs in Play Console; the Android apps already bundle the same
   legal text.
4. **Legal review.** Privacy, terms and deletion copy are drafts. Confirm the
   store's legal name, address/contact, retention period, grievance contact and
   applicable Indian consumer/privacy requirements.
5. **Staff test account.** Create a non-production manager account, and ensure
   Play review can reach every staff screen. Never place credentials in git.
6. **Authenticated release QA is incomplete.** The 1.5 signed, minified release
   launched on an API 37 emulator, and the staff privacy screen rendered; the
   1.6 release build has not been launched. The customer authenticated flow,
   address/location, catalogue, quote, COD order, tracking, bills, account
   deletion, staff orders, delivery completion and POS/billing still need a
   clean device/emulator pass. Emulator use was stopped at the owner's request;
   do not claim these flows are verified.
7. **Play Console access and upload key registration.** Upload the first AAB
   manually, enrol Play App Signing, and keep the upload key backed up.

## Play Console setup

### Customer app (`com.balaji.provision`)

- **App name:** Balaji Provision Store
- **Category:** Food & Drink (or Shopping)
- **Country availability:** India initially
- **Ads:** No
- **In-app purchases:** No
- **App access:** All functionality requires login. Provide the test phone number
  and fixed OTP, or a documented reviewer flow.
- **Privacy policy:** `https://<deployed-domain>/privacy`
- **Account deletion:** `https://<deployed-domain>/account-deletion`
- **Data safety:** use `store-assets/play/data-safety.md`
- **Target audience:** 18+; the app is not child-directed.
- **Content rating:** complete the questionnaire truthfully; no ads, gambling,
  violence or user-generated public content.
- **Permissions:** approximate/precise location is optional and used only when
  the customer taps “Use my current location” while saving an address. The app
  works without it. Staff builds do not declare location permission.

### Staff app (`com.balaji.provision.staff`)

- **App name:** Balaji Staff
- **Category:** Business
- **Distribution:** internal testing/closed track or managed Google Play private
  app. This is a staff-only operations app, not a consumer storefront.
- **App access:** provide a manager test login ID and password in Play Console.
- **Privacy policy:** same public URL
- **Ads/in-app purchases:** No
- **Data safety:** use `store-assets/play/data-safety.md`
- The privacy policy is reachable from the staff login screen. Staff accounts do
  not self-delete; a manager or owner removes them on request.

## Store listing assets

- Icon: `store-assets/play/icon-512.png`
- Feature graphic: `store-assets/play/feature-graphic-1024x500.png`
- Listing text: `store-assets/play/listing.md`
- Screenshots: **marketing set not generated yet**. `store-assets/play/qa/`
  contains real login/privacy captures used only for QA; authenticated feature
  screens require a migrated backend and test account. Capture the full set on a
  clean build before submitting. Do not use `UI.png`; it is a design mock, not
  the shipped app.
- Minimum 2 phone screenshots per app; 4–8 recommended. 1080×1920 or higher,
  16:9/9:16, no personal data, no misleading features.

## Tester and rollout notes

- Run an internal testing track first. Verify the Play-signed install, not a
  locally sideloaded debug APK.
- For a new personal Play developer account, complete the closed-testing
  requirement shown in Play Console (currently 12 testers for 14 continuous
  days) before applying for production access. Confirm the current requirement
  in Play Console; do not treat this document as a waiver.
- Upload both AABs separately; they are separate Play listings.
- Keep a copy of the uploaded AAB, its SHA-256 checksum and R8 mapping with the
  release notes. Final candidates, checksums and mappings are already in
  `releases/`.

## Verification commands

```bash
cd web
npm run -s type-check
npm run -s lint
npm run -s build

cd ../android
bash gradlew :app:testCustomerDebugUnitTest :app:testStaffDebugUnitTest
bash gradlew :app:lintCustomerDebug :app:lintStaffDebug
bash gradlew :app:assembleCustomerDebug :app:assembleStaffDebug
bash gradlew :app:bundleCustomerRelease :app:bundleStaffRelease
bash gradlew :app:assembleCustomerRelease :app:assembleStaffRelease
cd .. && bash scripts/check_guards.sh

# Inspect the signed APK
$ANDROID_HOME/build-tools/36.0.0/aapt dump badging \
  android/app/build/outputs/apk/customer/release/app-customer-release.apk | \
  grep -E "package:|sdkVersion|targetSdkVersion"
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
  android/app/build/outputs/apk/customer/release/app-customer-release.apk
```

## Release artifacts and checksums

The final 1.6 candidates are in `releases/`:

```
66f642d5f68b6a9957bdc52877ebf337ea1eddcda53a68b4f6753c10eda4c4b1  balaji-provision-1.6-release.apk
5f2b0dcb072f299b2cba0b108a18db2276c536b550776f0e1d0d34f689d9cd18  balaji-staff-1.6-release.apk
44a0da720416052f71115a00aadd68f7b6377ec759fa6a46ff68ef8bdd410d53  balaji-provision-1.6-release.aab
dba871da0af23c926874ab035c841f4eb46d916dd8169ffbdb653264a3c75cc4  balaji-staff-1.6-release.aab
```

Mappings: `mapping-customer-1.6.txt.gz`, `mapping-staff-1.6.txt.gz` (SHA-256
`8275639416bc1f2df5a3a8c178bf7ca278c73d8377c3fab85ac2b0dd0a1f0cdd` and
`f60319a57b9203daaefc742f28dfb65bae7556283fe093fdf496567d7435ff2b`).
The 1.6 APKs verify with `apksigner`; `aapt` reports versionCode 7, versionName
1.6, min SDK 24 and target SDK 36. The staff APK requests no location, camera
or SMS permission; its manifest contains internet, network state and the app's
own dynamic-receiver permission. The release build has not been run on a device.
