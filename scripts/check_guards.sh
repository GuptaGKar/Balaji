#!/usr/bin/env bash
# M0 guard (IMPLEMENTATION_PLAN.md §11):
#   1. Fail on any "minimum order" concept in source.
#   2. Fail on hard-coded delivery threshold values (250 / 25000) outside
#      seed data and tests. Thresholds must come from store_settings.
set -euo pipefail

SCAN_PATHS=("supabase/migrations" "supabase/functions" "web/src" "android")
EXT_GLOB="*.sql *.ts *.tsx *.kt *.kts"
FAIL=0

# 1. No minimum-order concept anywhere in source.
for p in "${SCAN_PATHS[@]}"; do
  [ -d "$p" ] || continue
  # shellcheck disable=SC2086
  if grep -rniE "minimum[ _-]?order" "$p" --include="*.sql" --include="*.ts" --include="*.tsx" --include="*.kt" --include="*.kts" 2>/dev/null; then
    echo "::error::minimum-order concept found in $p — the plan forbids any minimum order"
    FAIL=1
  fi
done

# 2. No hard-coded 25000 or 250 outside seed/tests.
for p in "${SCAN_PATHS[@]}"; do
  [ -d "$p" ] || continue
  # shellcheck disable=SC2086
  if grep -rnE "(^|[^0-9])25000([^0-9]|$)" "$p" --include="*.sql" --include="*.ts" --include="*.tsx" --include="*.kt" --include="*.kts" 2>/dev/null; then
    echo "::error::hard-coded 25000 found in $p — threshold must come from store_settings"
    FAIL=1
  fi
  # shellcheck disable=SC2086
  if grep -rnE "(^|[^0-9])250([^0-9]|$)" "$p" --include="*.sql" --include="*.ts" --include="*.tsx" --include="*.kt" --include="*.kts" 2>/dev/null; then
    echo "::error::hard-coded 250 found in $p — threshold must come from store_settings"
    FAIL=1
  fi
done

if [ "$FAIL" -ne 0 ]; then
  echo "Guard FAILED. Delivery-charge threshold and fee are configurable settings (store_settings), never constants."
  exit 1
fi
echo "Guards passed: no minimum-order concept; no hard-coded thresholds outside seed/tests."
