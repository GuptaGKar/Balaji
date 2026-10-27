#!/usr/bin/env bash
# M0 guard (IMPLEMENTATION_PLAN.md §11):
#   1. Fail on any store-level "minimum order" concept in source.
#   2. Fail on hard-coded delivery-threshold values (25000 paise / ₹250) outside
#      seed data and tests. Thresholds must come from store_settings.
#
# Coupon eligibility is NOT a store minimum order: `coupons.min_order_paise` is
# the spend required for one promotion, so coupon copy is exempt from rule 1.
# Bare "250" is not checked because product pack sizes (e.g. "250 g") and SKUs
# legitimately contain it.
set -euo pipefail

SCAN_PATHS=("supabase/migrations" "supabase/functions" "web/src" "android/app/src")
INCLUDE=(--include="*.sql" --include="*.ts" --include="*.tsx" --include="*.kt" --include="*.kts")
EXCLUDES=(
  --exclude-dir=build
  --exclude-dir=node_modules
  --exclude-dir=test
  --exclude-dir=tests
  --exclude-dir=__tests__
  --exclude="*.test.*"
  --exclude="*.spec.*"
)
FAIL=0

scan() {
  local path="$1"
  shift
  [ -d "$path" ] || return 0
  grep -rn "$@" "$path" "${INCLUDE[@]}" "${EXCLUDES[@]}" 2>/dev/null || true
}

for p in "${SCAN_PATHS[@]}"; do
  # 1. Minimum-order concept. Coupon spend copy is exempt (see header).
  min_order_hits=$(scan "$p" -iE "minimum[ _-]?order" | grep -vi "coupon" || true)
  if [ -n "$min_order_hits" ]; then
    echo "$min_order_hits"
    echo "::error::minimum-order concept found in $p — the plan forbids any minimum order"
    FAIL=1
  fi

  # 2a. Delivery threshold in paise.
  threshold_paise=$(scan "$p" -E "(^|[^0-9])25000([^0-9]|$)")
  if [ -n "$threshold_paise" ]; then
    echo "$threshold_paise"
    echo "::error::hard-coded 25000 paise found in $p — threshold must come from store_settings"
    FAIL=1
  fi

  # 2b. Delivery threshold written as rupee copy.
  threshold_rupees=$(scan "$p" -E "(₹|Rs\.?|INR) *250([^0-9]|$)")
  if [ -n "$threshold_rupees" ]; then
    echo "$threshold_rupees"
    echo "::error::hard-coded ₹250 found in $p — threshold must come from store_settings"
    FAIL=1
  fi
done

if [ "$FAIL" -ne 0 ]; then
  echo "Guard FAILED. Delivery-charge threshold and fee are configurable settings (store_settings), never constants."
  exit 1
fi
echo "Guards passed: no minimum-order concept; no hard-coded thresholds outside seed/tests."
