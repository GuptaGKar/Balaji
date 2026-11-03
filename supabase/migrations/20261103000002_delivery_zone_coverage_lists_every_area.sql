-- ============================================================
-- 20261103000002  Coverage list shows every area, drawn or not
-- ============================================================
-- Reported by the owner: an area added in the admin panel did not appear under
-- "Drawn areas and what they cover". Cause: get_delivery_zone_coverage filtered rows to
-- `boundary IS NOT NULL`, so a shape-less area was invisible with no explanation.
--
-- It also exposed a second, sharper fact that is preserved on purpose: an area with no
-- boundary AND a kind that is not a PIN rule cannot price anything. resolve_delivery_zone
-- requires `boundary IS NOT NULL`, and the PIN lookup in quote_cart/check_delivery_availability
-- only matches kind = 'pincode'. The list therefore has to show the row and say what it does,
-- rather than hide it.
--
-- Signature is unchanged, so no DROP is needed and PostgREST keeps one candidate.

CREATE OR REPLACE FUNCTION public.get_delivery_zone_coverage()
-- Returns EVERY service area, drawn or not, with has_boundary saying whether it has a shape.
-- A row without a boundary can still gate its own PIN (kind = 'pincode'); a row that is both
-- shapeless and not a PIN rule prices nothing, and the admin table has to say so rather than
-- hide it.
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_role public.user_role := public.current_role();
  v_rows jsonb;
BEGIN
  IF v_role IS DISTINCT FROM 'manager'::public.user_role THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Manager access required');
  END IF;

  SELECT COALESCE(jsonb_agg(row_json ORDER BY priority, label), '[]'::jsonb) INTO v_rows
  FROM (
    SELECT jsonb_build_object(
      'id', sa.id,
      'kind', sa.kind,
      'value', sa.value,
      'label', COALESCE(NULLIF(btrim(sa.name), ''), sa.value),
      'priority', sa.priority,
      'active', sa.active,
      'delivery_fee_paise', sa.delivery_fee_paise,
      'delivery_charge_threshold_paise', sa.delivery_charge_threshold_paise,
      'free_delivery_threshold_paise', sa.free_delivery_threshold_paise,
      'addresses_inside', (
        SELECT count(*)
        FROM public.addresses a
        WHERE a.lat IS NOT NULL AND a.lng IS NOT NULL
          AND NOT (a.lat = 0 AND a.lng = 0)
          AND sa.boundary IS NOT NULL
          AND extensions.ST_Covers(
            sa.boundary,
            extensions.ST_SetSRID(extensions.ST_MakePoint(a.lng, a.lat), 4326)
          )
      ),
      'overlapping_areas', (
        SELECT count(*)
        FROM public.service_areas other
        WHERE other.id <> sa.id
          AND other.active = true
          AND other.boundary IS NOT NULL
          AND sa.boundary IS NOT NULL
          AND extensions.ST_Intersects(other.boundary, sa.boundary)
      ),
      'has_boundary', sa.boundary IS NOT NULL,
      'geojson', CASE WHEN sa.boundary IS NULL THEN NULL
                      ELSE extensions.ST_AsGeoJSON(sa.boundary)::jsonb END
    ) AS row_json,
    sa.priority AS priority,
    COALESCE(NULLIF(btrim(sa.name), ''), sa.value) AS label
    FROM public.service_areas sa
  ) all_areas;

  RETURN v_rows;
END;
$$;

-- Grants are restated exactly as the applied migration set them (idempotent, no widening).
REVOKE ALL ON FUNCTION public.get_delivery_zone_coverage() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_delivery_zone_coverage() TO authenticated;

COMMENT ON FUNCTION public.get_delivery_zone_coverage IS
  'Manager-only list of EVERY delivery area with its coverage counts. has_boundary says whether '
  'the row carries a drawn polygon; shapeless rows that are not PIN rules price nothing.';

NOTIFY pgrst, 'reload schema';
