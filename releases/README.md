# Android APK downloads

Installable Android builds of **Balaji Provision Store** (`com.balaji.provision`).

## Current build

| File | Version | Build type | Size | min / target SDK |
|---|---|---|---|---|
| [`balaji-provision-1.1-debug.apk`](balaji-provision-1.1-debug.apk) | versionName 1.1 (versionCode 2) | debug, debug-signed | ~19 MB | 24 / 34 |

SHA-256 (also in `balaji-provision-1.1-debug.apk.sha256`):

```
0531343dd31806a846fcf1820045294f575c9e3d3c5d3dc100c1f65820ed127a  balaji-provision-1.1-debug.apk
```

Permissions requested: `INTERNET`, `ACCESS_NETWORK_STATE`, `REORDER_TASKS` only. No camera, location, contacts or storage access.

## Install on a phone

1. Download `balaji-provision-1.1-debug.apk` from this folder (GitHub: **Code → Download raw file**, or *Releases* if a release is published).
2. Open the file on the phone and allow **Install unknown apps** for the browser/file manager.
3. Launch **Balaji Provision Store** and sign in with your mobile number (OTP).

Upgrades: install the newer APK over the older one — the application id stays the same, so data is preserved. Android may ask to "update" the app because the signing key differs from the Play Store build.

## How this build was produced

```bash
cd android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash gradlew assembleDebug
# output: android/app/build/outputs/apk/debug/app-debug.apk
```

`SUPABASE_URL` and the publishable `SUPABASE_ANON_KEY` are read from Gradle properties or environment variables, and fall back to the project's public publishable key. No server secret is embedded in the APK.

## Known limitations of this artifact

- It is a **debug build**: `android:debuggable="true"` and signed with the local Android debug keystore. It is fine for internal and pilot installs, but a **signed release build** (own keystore, `assembleRelease`) should be produced before wider distribution.
- It is not attached to a GitHub *Release* yet — this folder in the repository is the download location. A CI workflow that builds and uploads the APK automatically is still to be added.

## Rebuild after a code change

```bash
cd android && bash gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ../releases/balaji-provision-<version>-debug.apk
cd ../releases && sha256sum balaji-provision-<version>-debug.apk > balaji-provision-<version>-debug.apk.sha256
```

Commit the updated APK and checksum with the code change so the download always matches the source.
