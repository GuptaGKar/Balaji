#!/usr/bin/env python3
"""Inspect an unminified APK's manifest and DEX for updater distribution isolation."""
import argparse
from pathlib import Path
import subprocess
import sys
from zipfile import ZipFile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--mode', choices=['play', 'direct'], required=True)
parser.add_argument('--aapt2', required=True, type=Path)
args = parser.parse_args()
result = subprocess.run([str(args.aapt2), 'dump', 'permissions', str(args.apk)], capture_output=True, text=True, check=True)
permission = 'android.permission.REQUEST_INSTALL_PACKAGES' in result.stdout
with ZipFile(args.apk) as apk:
    dex = [apk.read(n) for n in apk.namelist() if n.startswith('classes') and n.endswith('.dex')]
if not dex:
    sys.exit('FAIL: APK has no DEX files')
updater = any(b'Lcom/balaji/provision/data/update/AppUpdateManager;' in d for d in dex)
install_actions = any(b'android.settings.MANAGE_UNKNOWN_APP_SOURCES' in d or
                      b'application/vnd.android.package-archive' in d for d in dex)
expected = args.mode == 'direct'
if any(value != expected for value in (permission, updater, install_actions)):
    sys.exit(f'FAIL: {args.mode} permission={permission} updater={updater} install_actions={install_actions}; expected {expected}')
print(f'PASS: {args.mode} APK permission={permission}, updater={updater}, install_actions={install_actions}')
