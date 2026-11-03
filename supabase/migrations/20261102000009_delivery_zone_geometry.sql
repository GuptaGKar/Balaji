-- Migration: 20261102000009_delivery_zone_geometry.sql
-- M1 foundation step: give a service area an optional polygon boundary.
-- Behaviour-neutral on purpose — nothing in this file changes a price, a fee, an
-- order outcome or an existing policy. The pricing switch is the next migration.
--
-- Design decided by the owner on 2026-11-02:
--   * A "delivery zone" is a public.service_areas row that carries a boundary, not a
--     parallel entity, so there is exactly one coverage table and no second source of
--     truth. The orphan public.delivery_zones table left by the abandoned 2026-10-29
--     experiment is not reused (see the hardening step at the bottom).
--   * PIN codes stay the delivery gate. Boundary geometry only ever selects a price.
--   * PostGIS is used because the owner chose it over the GeoJSON fallback; it must be
--     created in the "extensions" schema first. This migration refuses to run if it
--     is absent, so it can never half-apply.

BEGIN;

-- ─── 0. PostGIS, required by the typed boundary column ───────────────────────
-- Created here so a fresh database builds from the migration set alone instead of
-- depending on somebody having enabled it in the dashboard first. On this project
-- PostGIS 3.3.7 is already installed in "extensions", whereupon this is a no-op.
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA extensions;

-- If creation could not happen (missing privilege, unsupported build), stop now with
-- an actionable message rather than failing confusingly on the typed column below.
DO $$
DECLARE
  v_installed text;
BEGIN
  SELECT n.nspname INTO v_installed
  FROM pg_catalog.pg_extension e
  JOIN pg_catalog.pg_namespace n ON n.oid = e.extnamespace
  WHERE e.extname = 'postgis';

  IF v_installed IS NULL THEN
    RAISE EXCEPTION
      'PostGIS is unavailable. Run: create extension if not exists postgis with schema extensions;'
      USING HINT = 'Then re-apply this migration.';
  END IF;

  IF v_installed <> 'extensions' THEN
    RAISE WARNING 'PostGIS is installed in schema "%", not "extensions".', v_installed;
  END IF;

  IF to_regtype('extensions.geometry') IS NULL THEN
    RAISE EXCEPTION 'extensions.geometry does not exist in this database.';
  END IF;
END;
$$;

-- ─── 1. service_areas gains an optional boundary ─────────────────────────────
ALTER TABLE public.service_areas
  ADD COLUMN IF NOT EXISTS name text
    CHECK (name IS NULL OR (length(btrim(name)) > 0 AND length(name) <= 80)),
  ADD COLUMN IF NOT EXISTS priority integer NOT NULL DEFAULT 100
    CHECK (priority BETWEEN 1 AND 1000),
  ADD COLUMN IF NOT EXISTS boundary extensions.geometry(Polygon, 4326);

COMMENT ON COLUMN public.service_areas.name IS
  'Optional display name for the area, e.g. "Roorkeez outskirt belt". Empty for plain PIN rows.';
COMMENT ON COLUMN public.service_areas.priority IS
  'When more than one boundary covers a point, the lowest number wins. 100 = default.';
COMMENT ON COLUMN public.service_areas.boundary IS
  'Optional polygon (WGS84) describing where this service area physically reaches. '
  'It never decides whether an order is accepted: the PIN-code gate does. '
  'It only supplies delivery-fee values for addresses that pass that gate.';

-- A stored boundary must be a real, closed, lat/lng polygon. ST_NPoints /
-- ST_IsClosed / ST_ExteriorRing are IMMUTABLE, which a table CHECK requires.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'service_areas_boundary_is_closed_polygon'
  ) THEN
    ALTER TABLE public.service_areas
      ADD CONSTRAINT service_areas_boundary_is_closed_polygon
      CHECK (
        boundary IS NULL
        OR (
          extensions.ST_SRID(boundary) = 4326
          AND extensions.ST_NPoints(boundary) >= 4
          AND extensions.ST_IsClosed(extensions.ST_ExteriorRing(boundary))
        )
      );
  END IF;
END;
$$;

-- Containment lookups are the point of the column, so index it spatially.
CREATE INDEX IF NOT EXISTS idx_service_areas_boundary_gist
  ON public.service_areas USING GIST (boundary);

-- ─── 2. Public map read: boundaries only, never money ────────────────────────
-- The customer app and the admin map need the shapes to draw them. They must not
-- have to read public.service_areas (which exposes per-area fee columns) to do it,
-- so this view deliberately omits every amount.
CREATE OR REPLACE VIEW public.delivery_zone_map AS
SELECT
  sa.id,
  sa.kind,
  COALESCE(NULLIF(btrim(sa.name), ''), sa.value) AS label,
  sa.value,
  sa.priority,
  sa.active,
  CASE
    WHEN sa.boundary IS NULL THEN NULL
    ELSE extensions.ST_AsGeoJSON(sa.boundary)::jsonb
  END AS geojson,
  CASE
    WHEN sa.boundary IS NULL THEN NULL
    ELSE extensions.ST_Y(extensions.ST_Centroid(sa.boundary))
  END AS center_lat,
  CASE
    WHEN sa.boundary IS NULL THEN NULL
    ELSE extensions.ST_X(extensions.ST_Centroid(sa.boundary))
  END AS center_lng
FROM public.service_areas sa
WHERE sa.boundary IS NOT NULL;

COMMENT ON VIEW public.delivery_zone_map IS
  'Draw-only projection of the service areas that have a boundary. Exposes no fee, '
  'threshold or any other amount column; pricing stays inside the server functions.';

-- Run the view as the calling role so the existing service_areas RLS policies still
-- decide which rows are visible. security_invoker exists on PostgreSQL 15+; if this
-- instance rejects it we say so loudly rather than silently publishing everything.
DO $$
BEGIN
  ALTER VIEW public.delivery_zone_map SET (security_invoker = true);
EXCEPTION WHEN OTHERS THEN
  RAISE WARNING
    'Could not set security_invoker on public.delivery_zone_map (%). The view will be '
    'read with the owner privileges, so it must not be granted to anon or authenticated '
    'until that is reviewed.', SQLERRM;
END;
$$;

GRANT SELECT ON public.delivery_zone_map TO anon, authenticated;

-- ─── 3. Hardening: close the anon read on the abandoned delivery_zones table ──
-- The 2026-10-29 zone experiment left public.delivery_zones in the live database, empty
-- but readable by anon over PostgREST. Nothing uses it since the PIN revert, so the
-- read is removed. The table itself is kept: dropping it is destructive and needs a
-- separate explicit approval.
DO $$
BEGIN
  IF to_regclass('public.delivery_zones') IS NOT NULL THEN
    REVOKE ALL ON public.delivery_zones FROM PUBLIC, anon, authenticated;
    EXECUTE 'ALTER TABLE public.delivery_zones ENABLE ROW LEVEL SECURITY';
    RAISE NOTICE 'Revoked all access to the unused public.delivery_zones table.';
  END IF;
END;
$$;

NOTIFY pgrst, 'reload schema';

COMMIT;
