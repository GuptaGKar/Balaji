-- Migration: 20261103000001_delivery_zone_admin.sql
-- M3: let a manager save and inspect a delivery area from the web dashboard.
--
-- Why functions instead of direct table writes:
--   * PostgREST cannot turn GeoJSON text into a PostGIS geometry, so the boundary has to
--     be converted server-side (ST_GeomFromGeoJSON) rather than sent as a column value.
--   * Both functions are SECURITY DEFINER, which bypasses RLS, so each one re-checks the
--     caller's role itself. Only a manager may write a boundary or read the amounts.
--
-- The PIN-code gate is untouched: this only maintains the shape and the per-area price
-- that migration 20261102000010 already resolves.

BEGIN;

-- ─── 1. Create or update one service area, optionally with a boundary ─────────
CREATE OR REPLACE FUNCTION public.save_delivery_zone(
  p_id uuid DEFAULT NULL,
  p_kind text DEFAULT 'neighbourhood',
  p_value text DEFAULT '',
  p_name text DEFAULT '',
  p_priority integer DEFAULT 100,
  p_geojson text DEFAULT NULL,
  p_delivery_fee_paise bigint DEFAULT NULL,
  p_delivery_charge_threshold_paise bigint DEFAULT NULL,
  p_free_delivery_threshold_paise bigint DEFAULT NULL,
  p_active boolean DEFAULT true,
  p_clear_boundary boolean DEFAULT false
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_role public.user_role := public.current_role();
  v_kind public.service_area_kind;
  v_value text := btrim(COALESCE(p_value, ''));
  v_name text := NULLIF(btrim(COALESCE(p_name, '')), '');
  v_boundary extensions.geometry;
  v_final_boundary extensions.geometry;
  v_existing public.service_areas%ROWTYPE;
  v_id uuid;
  v_overlaps integer := 0;
BEGIN
  IF v_role IS DISTINCT FROM 'manager'::public.user_role THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Manager access required');
  END IF;

  IF p_kind NOT IN ('pincode', 'neighbourhood') THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Unknown area type');
  END IF;
  v_kind := p_kind::public.service_area_kind;

  IF v_value = '' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'A PIN or area name is required');
  END IF;

  IF p_priority IS NULL OR p_priority < 1 OR p_priority > 1000 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Priority must be between 1 and 1000');
  END IF;

  IF COALESCE(p_delivery_fee_paise, 0) < 0
     OR COALESCE(p_delivery_charge_threshold_paise, 0) < 0
     OR COALESCE(p_free_delivery_threshold_paise, 0) < 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Amounts cannot be negative');
  END IF;

  -- A boundary is only accepted as a real, closed WGS84 polygon. Anything else is
  -- rejected here so the table CHECK cannot abort the caller with a raw database error.
  IF p_geojson IS NOT NULL AND btrim(p_geojson) <> '' AND NOT p_clear_boundary THEN
    BEGIN
      v_boundary := extensions.ST_SetSRID(extensions.ST_GeomFromGeoJSON(p_geojson), 4326);
    EXCEPTION WHEN OTHERS THEN
      RETURN jsonb_build_object(
        'error', 'INVALID_BOUNDARY', 'message', 'The drawn shape is not valid GeoJSON'
      );
    END;

    IF extensions.ST_GeometryType(v_boundary) <> 'ST_Polygon' THEN
      RETURN jsonb_build_object('error', 'INVALID_BOUNDARY', 'message', 'Draw a polygon, not a point or line');
    END IF;
    IF extensions.ST_NPoints(v_boundary) < 4 THEN
      RETURN jsonb_build_object('error', 'INVALID_BOUNDARY', 'message', 'A shape needs at least three corners');
    END IF;
    IF NOT extensions.ST_IsClosed(extensions.ST_ExteriorRing(v_boundary)) THEN
      RETURN jsonb_build_object('error', 'INVALID_BOUNDARY', 'message', 'The drawn shape is not closed');
    END IF;
  END IF;

  IF p_id IS NOT NULL THEN
    SELECT * INTO v_existing FROM public.service_areas WHERE id = p_id;
    IF NOT FOUND THEN
      RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'That service area no longer exists');
    END IF;

    BEGIN
      UPDATE public.service_areas
         SET kind = v_kind,
             value = v_value,
             name = v_name,
             priority = p_priority,
             boundary = CASE WHEN p_clear_boundary THEN NULL ELSE COALESCE(v_boundary, boundary) END,
             delivery_fee_paise = p_delivery_fee_paise,
             delivery_charge_threshold_paise = p_delivery_charge_threshold_paise,
             free_delivery_threshold_paise = p_free_delivery_threshold_paise,
             active = p_active
       WHERE id = p_id
       RETURNING id, boundary INTO v_id, v_final_boundary;
    EXCEPTION WHEN unique_violation THEN
      RETURN jsonb_build_object(
        'error', 'VALIDATION_FAILED', 'message', 'That PIN or area name already exists'
      );
    END;
  ELSE
    BEGIN
      INSERT INTO public.service_areas (
        kind, value, name, priority, boundary,
        delivery_fee_paise, delivery_charge_threshold_paise, free_delivery_threshold_paise, active
      ) VALUES (
        v_kind, v_value, v_name, p_priority,
        CASE WHEN p_clear_boundary THEN NULL ELSE v_boundary END,
        p_delivery_fee_paise, p_delivery_charge_threshold_paise, p_free_delivery_threshold_paise, p_active
      )
      RETURNING id, boundary INTO v_id, v_final_boundary;
    EXCEPTION WHEN unique_violation THEN
      RETURN jsonb_build_object(
        'error', 'VALIDATION_FAILED', 'message', 'That PIN or area name already exists'
      );
    END;
  END IF;

  -- Overlap is allowed (priority decides which area prices a spot) but the manager should
  -- see it, so it is reported back rather than silently accepted.
  IF v_final_boundary IS NOT NULL THEN
    SELECT count(*) INTO v_overlaps
    FROM public.service_areas other
    WHERE other.id <> v_id
      AND other.active = true
      AND other.boundary IS NOT NULL
      AND extensions.ST_Intersects(other.boundary, v_final_boundary);
  END IF;

  RETURN jsonb_build_object(
    'id', v_id,
    'value', v_value,
    'name', v_name,
    'has_boundary', v_final_boundary IS NOT NULL,
    'overlapping_areas', v_overlaps
  );
END;
$$;

-- ─── 2. Coverage overview for the dashboard map ──────────────────────────────
-- How many real customer addresses each drawn area actually contains. PIN rows carry no
-- coordinates anywhere in this schema, so a PIN cannot be placed on the map; the count of
-- saved addresses inside the shape is the honest measure of what a boundary covers.
CREATE OR REPLACE FUNCTION public.get_delivery_zone_coverage()
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
      'geojson', CASE WHEN sa.boundary IS NULL THEN NULL
                      ELSE extensions.ST_AsGeoJSON(sa.boundary)::jsonb END
    ) AS row_json,
    sa.priority AS priority,
    COALESCE(NULLIF(btrim(sa.name), ''), sa.value) AS label
    FROM public.service_areas sa
    WHERE sa.boundary IS NOT NULL
  ) bounded;

  RETURN v_rows;
END;
$$;

COMMENT ON FUNCTION public.save_delivery_zone IS
  'Manager-only upsert of a service area and its optional boundary. The PIN-code gate in '
  'place_order is unaffected; boundary and amounts only choose a price.';
COMMENT ON FUNCTION public.get_delivery_zone_coverage IS
  'Manager-only list of drawn areas with the number of saved customer addresses inside each.';

REVOKE ALL ON FUNCTION public.save_delivery_zone(uuid, text, text, text, integer, text, bigint, bigint, bigint, boolean, boolean) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_delivery_zone_coverage() FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.save_delivery_zone(uuid, text, text, text, integer, text, bigint, bigint, bigint, boolean, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_delivery_zone_coverage() TO authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;
