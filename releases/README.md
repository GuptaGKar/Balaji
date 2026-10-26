# Android builds

The same codebase produces two apps with separate application IDs, so both can
be installed on one phone:

- **Balaji Provision Store** (`com.balaji.provision`) — customer app, phone OTP.
- **Balaji Staff** (`com.balaji.provision.staff`) — manager and delivery app,
  staff login. The signed-in role decides what the user sees.

For the complete Play submission checklist, signing details, Data Safety
worksheet and remaining blockers, read [`docs/PLAY_STORE_RELEASE.md`](../docs/PLAY_STORE_RELEASE.md).

## Current release — 1.6 (versionCode 7)

Target SDK 36 (Android 16), min SDK 24, R8 minified and signed with the Balaji
upload key.

| File | App | Purpose |
|---|---|---|
| [`balaji-provision-1.6-release.aab`](balaji-provision-1.6-release.aab) | Customer | Google Play upload bundle |
| [`balaji-staff-1.6-release.aab`](balaji-staff-1.6-release.aab) | Staff | Google Play upload bundle |
| [`balaji-provision-1.6-release.apk`](balaji-provision-1.6-release.apk) | Customer | Signed APK for device QA/sideload |
| [`balaji-staff-1.6-release.apk`](balaji-staff-1.6-release.apk) | Staff | Signed APK for device QA/sideload |

SHA-256:

```
44a0da720416052f71115a00aadd68f7b6377ec759fa6a46ff68ef8bdd410d53  balaji-provision-1.6-release.aab
dba871da0af23c926874ab035c841f4eb46d916dd8169ffbdb653264a3c75cc4  balaji-staff-1.6-release.aab
66f642d5f68b6a9957bdc52877ebf337ea1eddcda53a68b4f6753c10eda4c4b1  balaji-provision-1.6-release.apk
5f2b0dcb072f299b2cba0b108a18db2276c536b550776f0e1d0d34f689d9cd18  balaji-staff-1.6-release.apk
```

The matching `.sha256` files are beside each artifact. R8 mapping files for
crash deobfuscation are included as `mapping-customer-1.6.txt.gz` and
`mapping-staff-1.6.txt.gz`, with checksums. The upload certificate SHA-256 is
`8A:6B:C8:2D:4A:3C:64:00:78:70:FB:D6:40:49:8F:CF:B8:09:DD:50:B9:D2:AB:64:F6:C0:FE:19:85:F7:D6:4D`.

## What's new in 1.6

- **Category images.** Managers can upload a JPEG/PNG/WebP category image from
  the web catalogue; the customer Android app shows it in category cards and
  falls back to the existing icon when no image is set.
- **Pack-size grouping.** The web product form can place multiple pack-size SKUs
  in one customer-facing group. Each pack keeps its own SKU, price and stock;
  SKUs remain globally unique. Grouping requires migration
  `20261023000001_product_variant_grouping.sql`.
- **Staff order-item fix.** Opening an order in the staff app no longer requests
  a non-existent `order_items.created_at` ordering column, which caused the item
  list to fail.
- **Remove unavailable items.** Staff and managers can remove an item from an
  order before dispatch (`pending_review`, `accepted`, `preparing`), with a
  required reason. Stock and totals are recalculated by the existing server RPC.
- Version 1.6 (versionCode 7).

## Previous build — 1.5 (versionCode 6)

The signed 1.5 artifacts remain in `releases/` for history:
`balaji-provision-1.5-release.aab`, `balaji-staff-1.5-release.aab`, their QA
APKs and `mapping-*-1.5.txt.gz`. 1.5 added Android 16 targeting, account
deletion, and bundled privacy/terms/deletion screens.

## Older build — 1.4 debug

The older [`balaji-provision-1.4-debug.apk`](balaji-provision-1.4-debug.apk) and
[`balaji-staff-1.4-debug.apk`](balaji-staff-1.4-debug.apk) are debug-signed,
target API 34 and must not be uploaded to Play. Because their signing key differs
from the release key, Android will refuse to install 1.5/1.6 over an installed
1.4 debug build; uninstall it first (this deletes local app data) or use a clean
device/emulator.

## Install on a phone

1. Download the `1.6-release.apk` for the app you need.
2. Allow **Install unknown apps** for the browser/file manager when prompted.
3. Customer app: sign in with a mobile number (OTP).
4. Staff app: sign in with the login ID and password provided by the store.

Upgrades between builds signed with the same upload key preserve app data. The
two apps have different application IDs, so installing one never overwrites the
other.

## Remaining limitations

- These artifacts have **not** been uploaded to Play or reviewed. Play Console
  access, the public privacy-policy URL, an SMS/OTP provider and a reviewer test
  account are still required.
- The database migrations, including variant grouping, category images, M9
  billing and account deletion, have not been applied or tested against a live
  database. Category images and multi-pack groups stay unavailable until their
  migrations are applied. See the Play guide.
- The 1.5 signed, minified build launched on an API 37 emulator and the staff
  privacy screen rendered; the 1.6 release build has not been launched.
  Authenticated customer flows, R8 runtime behaviour after login and
  real-device visual QA are still unverified. Emulator testing was stopped at
  the owner's request.
- In-app privacy, terms and account-deletion screens are bundled and work
  offline. Play Console still needs public HTTPS URLs backed by the deployed web
  app.
