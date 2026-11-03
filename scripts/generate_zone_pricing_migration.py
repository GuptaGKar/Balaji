#!/usr/bin/env python3
"""Generate 20261102000010_zone_delivery_pricing.sql from the REAL latest function
bodies in 20261101000001_free_item_coupons.sql, patching only the intended lines.

Every replacement asserts it matched exactly once, so a silent no-op edit is
impossible. Run from the repo root.
"""
import re, sys

SRC = "supabase/migrations/20261101000001_free_item_coupons.sql"
OUT = "supabase/migrations/20261102000010_zone_delivery_pricing.sql"

lines = open(SRC, encoding="utf-8").read().split("\n")  # 0-based


def slice_block(start_line, end_line):
    """1-based inclusive line range."""
    return "\n".join(lines[start_line - 1:end_line])


quote_cart = slice_block(44, 401)
place_order = slice_block(405, 751)

assert quote_cart.startswith("CREATE OR REPLACE FUNCTION public.quote_cart("), quote_cart[:60]
assert quote_cart.rstrip().endswith("$$;"), quote_cart[-40:]
assert place_order.startswith("CREATE OR REPLACE FUNCTION public.place_order(")
assert place_order.rstrip().endswith("$$;")


def sub_once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        sys.exit(f"FAIL [{label}]: expected exactly 1 match, found {n}\n---\n{old[:200]}")
    return text.replace(old, new)


# ── quote_cart: add the two advisory coordinate parameters ────────────────────
quote_cart = sub_once(
    quote_cart,
    "  p_free_item_id uuid DEFAULT NULL\n)",
    "  p_free_item_id uuid DEFAULT NULL,\n"
    "  p_lat double precision DEFAULT NULL,\n"
    "  p_lng double precision DEFAULT NULL\n)",
    "quote_cart params",
)

# ── quote_cart: hand the coordinates to the fee authority ─────────────────────
quote_cart = sub_once(
    quote_cart,
    "  SELECT * INTO v_fee_info FROM public.compute_delivery_fee_for_pin(v_item_subtotal, p_pin_code);",
    "  -- Coordinates are advisory: they can only choose between prices a manager already\n"
    "  -- configured, never invent one. NULL means \"fall back to the PIN / store rule\".\n"
    "  SELECT * INTO v_fee_info FROM public.compute_delivery_fee_for_pin(\n"
    "    v_item_subtotal, p_pin_code, p_lat, p_lng\n"
    "  );",
    "quote_cart fee call",
)

# ── place_order: declare the parsed coordinate + zone holder ──────────────────
place_order = sub_once(
    place_order,
    "  v_fee_breakdown jsonb;\n",
    "  v_fee_breakdown jsonb;\n"
    "  v_lat double precision;\n"
    "  v_lng double precision;\n"
    "  v_zone public.service_areas%ROWTYPE;\n",
    "place_order declare",
)

# ── place_order: safe coordinate parsing, then pass them to quote_cart ────────
place_order = sub_once(
    place_order,
    "  v_quote := public.quote_cart(v_items, v_coupon_code, v_address->>'postal_code', v_free_item_id);",
    "  -- Read the address coordinates defensively. A non-numeric, out-of-range or\n"
    "  -- exactly-zero value is treated as \"no location supplied\" rather than allowed to\n"
    "  -- cast-fail the RPC or resolve a zone on the coast of Somalia.\n"
    "  IF (v_address->>'lat') ~ '^-?[0-9]+([.][0-9]+)?$'\n"
    "     AND (v_address->>'lng') ~ '^-?[0-9]+([.][0-9]+)?$' THEN\n"
    "    v_lat := (v_address->>'lat')::double precision;\n"
    "    v_lng := (v_address->>'lng')::double precision;\n"
    "    IF v_lat IS NULL OR v_lat < -90 OR v_lat > 90 THEN v_lat := NULL; END IF;\n"
    "    IF v_lng IS NULL OR v_lng < -180 OR v_lng > 180 THEN v_lng := NULL; END IF;\n"
    "    IF v_lat = 0 AND v_lng = 0 THEN\n"
    "      v_lat := NULL;\n"
    "      v_lng := NULL;\n"
    "    END IF;\n"
    "  END IF;\n"
    "\n"
    "  v_quote := public.quote_cart(\n"
    "    v_items, v_coupon_code, v_address->>'postal_code', v_free_item_id, v_lat, v_lng\n"
    "  );",
    "place_order quote call",
)

# ── place_order: record which zone priced the order, for the audit trail ──────
place_order = sub_once(
    place_order,
    "    'rule_note', v_quote->>'fee_rule_note'\n  );",
    "    'rule_note', v_quote->>'fee_rule_note'\n  );\n"
    "\n"
    "  -- Which area (if any) physically covers this drop point. Advisory provenance only:\n"
    "  -- it explains the fee, it does not recompute it, and it never gates the order.\n"
    "  SELECT * INTO v_zone FROM public.resolve_delivery_zone(v_lat, v_lng);\n"
    "  IF v_zone.id IS NOT NULL THEN\n"
    "    v_fee_breakdown := v_fee_breakdown || jsonb_build_object(\n"
    "      'zone_id', v_zone.id,\n"
    "      'zone_label', COALESCE(NULLIF(btrim(v_zone.name), ''), v_zone.value),\n"
    "      'zone_priority', v_zone.priority\n"
    "    );\n"
    "  END IF;",
    "place_order fee_breakdown",
)

# ── place_order: snapshot the validated coordinates, not a raw cast ───────────
# The live body casts the client string straight into double precision, so a
# non-numeric "lat" aborts the whole RPC with a database error. Reuse the values we
# already parsed defensively; an unusable coordinate becomes NULL, as documented.
place_order = sub_once(
    place_order,
    "    'lat', CASE WHEN v_address->>'lat' IS NOT NULL THEN (v_address->>'lat')::double precision END,\n"
    "    'lng', CASE WHEN v_address->>'lng' IS NOT NULL THEN (v_address->>'lng')::double precision END",
    "    'lat', v_lat,\n"
    "    'lng', v_lng",
    "place_order snapshot coords",
)

body = f"""-- Migration: 20261102000010_zone_delivery_pricing.sql
-- M1 pricing step: an address's delivery fee can come from the polygon that covers it.
--
-- Owner decisions this implements (2026-11-02, see docs/DECISIONS.md):
--   * PIN STAYS THE GATE. The place_order service-area check is untouched: a point
--     inside a drawn polygon can never make an undeliverable PIN deliverable, and a
--     polygon mistake can never block a customer who has a configured PIN.
--   * Zones only choose the PRICE. Precedence per column, most specific first:
--       PIN row value  ->  covering zone row value  ->  store_settings value
--     so every per-PIN override a manager already set keeps its exact value, and a
--     zone starts to matter only for PINs that carry no override of their own.
--   * Coordinates are advisory input. They can only land on a price a manager
--     configured; they cannot fabricate one, so the untrusted-client rule holds.
--
-- Signature hygiene (AGENTS.md 14/15): every changed function gets DEFAULTs on the new
-- parameters and its old signature is dropped in this same file, so PostgREST never
-- sees two candidates. place_order keeps its public signature. quote_cart's returned
-- JSON keys are unchanged so existing mobile DTOs are unaffected.

BEGIN;

DROP FUNCTION IF EXISTS public.compute_delivery_fee_for_pin(bigint, text);
DROP FUNCTION IF EXISTS public.quote_cart(jsonb, text, text, uuid);

-- ─── 1. Which service area physically covers a point? ────────────────────────
-- Returns at most one row: the active, bounded area whose polygon covers the point,
-- lowest priority wins, oldest on a tie. Invalid input simply resolves to no row.
CREATE OR REPLACE FUNCTION public.resolve_delivery_zone(
  p_lat double precision,
  p_lng double precision
)
RETURNS SETOF public.service_areas
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT sa.*
  FROM public.service_areas sa
  WHERE p_lat IS NOT NULL
    AND p_lng IS NOT NULL
    AND p_lat BETWEEN -90 AND 90
    AND p_lng BETWEEN -180 AND 180
    AND NOT (p_lat = 0 AND p_lng = 0)
    AND sa.active = true
    AND sa.boundary IS NOT NULL
    AND extensions.ST_Covers(
      sa.boundary,
      extensions.ST_SetSRID(extensions.ST_MakePoint(p_lng, p_lat), 4326)
    )
  ORDER BY sa.priority ASC, sa.created_at ASC
  LIMIT 1;
$$;

COMMENT ON FUNCTION public.resolve_delivery_zone(double precision, double precision) IS
  'The active service_areas row whose boundary covers the given WGS84 point, lowest '
  'priority first; no row when the point is in no zone. Price lookup only - this '
  'function is never used to decide whether an order is accepted.';

-- ─── 2. Fee authority: PIN row, then covering zone, then store defaults ──────
CREATE OR REPLACE FUNCTION public.compute_delivery_fee_for_pin(
  p_eligible_subtotal bigint,
  p_pin_code text,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL
)
RETURNS TABLE (
  fee_paise bigint,
  rule_note text,
  below_charge_threshold boolean,
  amount_to_free_delivery bigint
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_settings public.store_settings%ROWTYPE;
  v_area public.service_areas%ROWTYPE;
  v_zone public.service_areas%ROWTYPE;
  v_fee bigint := 0;
  v_note text := '';
  v_below boolean := false;
  v_to_free bigint := 0;
  v_charge_threshold bigint;
  v_free_threshold bigint;
  v_fee_source text := 'store';
BEGIN
  SELECT * INTO v_settings FROM public.store_settings;

  IF p_pin_code IS NOT NULL AND btrim(p_pin_code) <> '' THEN
    SELECT * INTO v_area
    FROM public.service_areas
    WHERE active = true
      AND kind = 'pincode'
      AND lower(value) = lower(btrim(p_pin_code))
    LIMIT 1;
  END IF;

  -- The area that physically covers the drop point. When the PIN row IS that row we
  -- do not look it up twice.
  IF p_lat IS NOT NULL AND p_lng IS NOT NULL
     AND (v_area.id IS NULL OR v_area.boundary IS NULL) THEN
    SELECT * INTO v_zone FROM public.resolve_delivery_zone(p_lat, p_lng);
  END IF;

  -- Per-column precedence: PIN override, then zone override, then store-wide setting.
  v_charge_threshold := COALESCE(
    v_area.delivery_charge_threshold_paise,
    v_zone.delivery_charge_threshold_paise,
    v_settings.delivery_charge_threshold_paise
  );
  v_free_threshold := COALESCE(
    v_area.free_delivery_threshold_paise,
    v_zone.free_delivery_threshold_paise,
    v_settings.free_delivery_threshold_paise
  );

  IF v_area.id IS NOT NULL AND v_area.delivery_fee_paise IS NOT NULL THEN
    v_fee_source := 'pin';
  ELSIF v_zone.id IS NOT NULL AND v_zone.delivery_fee_paise IS NOT NULL THEN
    v_fee_source := 'zone';
  END IF;

  IF v_charge_threshold IS NULL OR v_charge_threshold = 0 THEN
    RETURN QUERY SELECT 0::bigint, 'No delivery-charge threshold configured', false, 0::bigint;
    RETURN;
  END IF;

  IF p_eligible_subtotal < v_charge_threshold THEN
    v_fee := COALESCE(
      v_area.delivery_fee_paise,
      v_zone.delivery_fee_paise,
      v_settings.delivery_fee_paise
    );
    v_below := true;
    v_note := CASE v_fee_source
      WHEN 'pin' THEN 'Delivery fee for PIN ' || v_area.value || ' applied (below threshold)'
      WHEN 'zone' THEN 'Delivery fee for zone '
        || COALESCE(NULLIF(btrim(v_zone.name), ''), v_zone.value)
        || ' applied (below threshold)'
      ELSE 'Delivery fee applied (below delivery-charge threshold)' END;
    IF v_free_threshold IS NOT NULL AND v_free_threshold > 0 THEN
      v_to_free := GREATEST(v_free_threshold - p_eligible_subtotal, 0);
      v_note := v_note || '; add ' || public.paise_to_rupees(v_to_free) || ' for free delivery';
    END IF;
  ELSIF v_free_threshold IS NOT NULL
        AND v_free_threshold > 0
        AND p_eligible_subtotal < v_free_threshold THEN
    v_fee := COALESCE(
      v_settings.mid_tier_fee_paise,
      v_area.delivery_fee_paise,
      v_zone.delivery_fee_paise,
      v_settings.delivery_fee_paise
    );
    v_below := true;
    v_note := 'Mid-tier delivery fee applied';
    v_to_free := GREATEST(v_free_threshold - p_eligible_subtotal, 0);
    v_note := v_note || '; add ' || public.paise_to_rupees(v_to_free) || ' for free delivery';
  ELSE
    v_fee := 0;
    v_below := false;
    v_note := 'Free delivery (at/above threshold)';
    v_to_free := 0;
  END IF;

  RETURN QUERY SELECT v_fee, v_note, v_below, v_to_free;
END;
$$;

COMMENT ON FUNCTION public.compute_delivery_fee_for_pin(bigint, text, double precision, double precision) IS
  'Single delivery-fee authority. p_lat/p_lng are advisory and can only select a price '
  'from an active service_areas boundary that covers the point; they never decide '
  'deliverability, which is the PIN-code check in place_order.';

-- ─── 3. check_delivery_availability: the pre-order answer ────────────────────
-- The mobile app asks this while the customer drags the pin, so it must be the same
-- maths the order will use. It calls compute_delivery_fee_for_pin rather than
-- reimplementing anything, so a preview can never disagree with the placed order.
CREATE OR REPLACE FUNCTION public.check_delivery_availability(
  p_pin_code text,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL,
  p_eligible_subtotal bigint DEFAULT 0
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_pin text := lower(btrim(COALESCE(p_pin_code, '')));
  v_area public.service_areas%ROWTYPE;
  v_zone public.service_areas%ROWTYPE;
  v_fee record;
  v_gate_configured boolean;
  v_in_area boolean;
BEGIN
  IF v_pin = '' THEN
    RETURN jsonb_build_object(
      'error', 'VALIDATION_FAILED', 'message', 'A PIN code is required to check delivery.'
    );
  END IF;

  -- Same rule place_order applies: an unconfigured service_areas list must not reject
  -- everybody. Keeping that here is what stops the banner contradicting the cart.
  SELECT EXISTS (
    SELECT 1 FROM public.service_areas WHERE active = true AND kind = 'pincode'
  ) INTO v_gate_configured;

  SELECT * INTO v_area
  FROM public.service_areas
  WHERE active = true
    AND kind = 'pincode'
    AND lower(value) = v_pin
  LIMIT 1;

  v_in_area := (NOT v_gate_configured) OR (v_area.id IS NOT NULL);

  IF v_in_area AND (v_area.id IS NULL OR v_area.boundary IS NULL)
     AND p_lat IS NOT NULL AND p_lng IS NOT NULL THEN
    SELECT * INTO v_zone FROM public.resolve_delivery_zone(p_lat, p_lng);
  END IF;

  -- Identical maths to the order path: same function, same PIN, same coordinates.
  SELECT * INTO v_fee
  FROM public.compute_delivery_fee_for_pin(
    COALESCE(p_eligible_subtotal, 0),
    p_pin_code,
    p_lat,
    p_lng
  );

  RETURN jsonb_build_object(
    'deliverable', v_in_area,
    'pin_code', v_pin,
    'pin_configured', v_area.id IS NOT NULL,
    'zone_id', v_zone.id,
    'zone_label', CASE WHEN v_zone.id IS NOT NULL
                       THEN COALESCE(NULLIF(btrim(v_zone.name), ''), v_zone.value) END,
    'fee_paise', v_fee.fee_paise,
    'rule_note', CASE WHEN v_in_area THEN v_fee.rule_note
                      ELSE 'Delivery is not available to this PIN code' END,
    'below_charge_threshold', v_fee.below_charge_threshold,
    'amount_to_free_delivery', v_fee.amount_to_free_delivery,
    'gate_is_pin_based', true,
    'message', CASE
      WHEN NOT v_in_area THEN 'We do not deliver to ' || v_pin || ' yet.'
      WHEN v_zone.id IS NOT NULL THEN 'We deliver here - '
        || COALESCE(NULLIF(btrim(v_zone.name), ''), v_zone.value) || '.'
      ELSE 'We deliver to ' || v_pin || '.'
    END
  );
END;
$$;

COMMENT ON FUNCTION public.check_delivery_availability(text, double precision, double precision, bigint) IS
  'Read-only pre-order preview: is this PIN deliverable, and what delivery fee would '
  'apply at this spot. Mirrors place_order''s gate and fee maths exactly.';

-- ─── 4. quote_cart (generated from the live 20261101000001 body) ─────────────
{quote_cart}

-- ─── 5. place_order (generated from the live 20261101000001 body) ────────────
{place_order}

-- ─── 6. Grants on the real signatures only ───────────────────────────────────
REVOKE ALL ON FUNCTION public.resolve_delivery_zone(double precision, double precision) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.compute_delivery_fee_for_pin(bigint, text, double precision, double precision) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.check_delivery_availability(text, double precision, double precision, bigint) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.quote_cart(jsonb, text, text, uuid, double precision, double precision) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.place_order(jsonb, text) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.compute_delivery_fee_for_pin(bigint, text, double precision, double precision) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.check_delivery_availability(text, double precision, double precision, bigint) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.quote_cart(jsonb, text, text, uuid, double precision, double precision) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.place_order(jsonb, text) TO authenticated;

-- resolve_delivery_zone stays owner-only on purpose: it is reached through the
-- SECURITY DEFINER functions above, never called straight from a client.

NOTIFY pgrst, 'reload schema';

COMMIT;
"""

open(OUT, "w", encoding="utf-8").write(body)
print(f"wrote {OUT}: {len(body.splitlines())} lines")
