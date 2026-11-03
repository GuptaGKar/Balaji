"""Reject an APK unless its installed identity and backend are isolated for local QA."""

import argparse
from pathlib import Path
import subprocess
from zipfile import ZipFile


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aapt2", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--audience", choices=("customer", "staff"), required=True)
    args = parser.parse_args()
    expected = "com.balaji.provision" + (".staff" if args.audience == "staff" else "") + ".audit"
    badging = subprocess.check_output([args.aapt2, "dump", "badging", str(args.apk)], text=True)
    require(f"name='{expected}'" in badging, "Audit APK must have a separate .audit identity")
    manifest = subprocess.check_output(
        [args.aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", str(args.apk)], text=True
    )
    require("com.balaji.provision.LOCAL_AUDIT" in manifest, "Audit manifest marker missing")
    require("networkSecurityConfig" in manifest, "Local-only network security config missing")
    require("com.google.firebase.provider.FirebaseInitProvider" not in manifest, "Firebase auto-init enabled")
    require("com.balaji.provision.push.BalajiMessagingService" not in manifest, "Live push receiver enabled")
    with ZipFile(args.apk) as archive:
        dex = b"".join(archive.read(n) for n in archive.namelist() if n.endswith(".dex"))
        require(b"http://127.0.0.1:54321" in dex, "Loopback Supabase endpoint missing")
        require(b"czbwwnlnmhhzketyjnzt.supabase.co" not in dex, "Live backend endpoint present")
    print(f"PASS: {expected} identity, local backend and disabled Firebase initialization")


if __name__ == "__main__":
    main()
