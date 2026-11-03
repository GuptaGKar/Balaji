-- ============================================================
-- 20261103000003  Area and address suggestions (M4)
-- ============================================================
-- Autocomplete for the customer address form, built only from data the store already owns:
-- active service_areas values plus the signed-in customer's own saved addresses. No third-party
-- geocoder is contacted, so no keystroke leaves this project and there is nothing to bill.
--
-- Security notes that matter more than the ranking:
--   * this is SECURITY DEFINER, so it deliberately bypasses RLS and therefore filters the
--     private half itself, on auth.uid(). If that predicate is ever removed the function leaks
--     every customer's address book, so suite 044 asserts isolation in both directions.
--   * with no authenticated caller auth.uid() is NULL and the private half is empty; the public
--     areas still work, which is what an anonymous shopper browsing needs.
--   * no money is exposed here. Delivery fee, thresholds and priority are business data and stay
--     behind check_delivery_availability / the manager-only coverage list; a suggestion only
--     tells you what to type.
--   * user text is escaped before it is used as a LIKE pattern, so a typed % or _ is a literal,
--     not a wildcard that would match the whole table.
--
-- Verified against the live project inside a rolled-back transaction by
-- scripts/verify_migration_live.py with supabase/tests/044_area_suggestions.sql.

CREATE OR REPLACE FUNCTION public.search_area_suggestions(
  p_query text,
  p_limit integer DEFAULT 8
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_raw text;
  v_pat text;
  v_cap integer;
  v_uid uuid;
  v_rows jsonb;
BEGIN
  v_raw := lower(btrim(coalesce(p_query, '')));

  -- Two characters before anything is searched; an over-long query is refused rather than
  -- handed to the planner. Both answers are an empty array, not an error, so the UI just has
  -- "no suggestions" to show.
  IF char_length(v_raw) < 2 OR char_length(v_raw) > 64 THEN
    RETURN '[]'::jsonb;
  END IF;

  v_cap := LEAST(GREATEST(coalesce(p_limit, 8), 1), 10);

  -- Escape LIKE metacharacters: the backslash first, or the escapes it introduces get escaped
  -- again by the later replaces.
  v_pat := replace(replace(replace(v_raw, '\', '\\'), '%', '\%'), '_', '\_');

  v_uid := auth.uid();

  SELECT COALESCE(
           jsonb_agg(picked.row_json
                     ORDER BY picked.match_rank, picked.source_order, picked.label),
           '[]'::jsonb
         )
    INTO v_rows
    FROM (
      SELECT m.match_rank, m.source_order, m.label, m.row_json
        FROM (
          -- ─── Areas the store serves (public) ───────────────────────────────
          SELECT CASE
                   WHEN lower(sa.value) = v_raw
                     OR lower(coalesce(btrim(sa.name), '')) = v_raw THEN 1
                   WHEN lower(sa.value) LIKE v_pat || '%'
                     OR lower(coalesce(btrim(sa.name), '')) LIKE v_pat || '%' THEN 2
                   ELSE 4
                 END AS match_rank,
                 lpad(sa.priority::text, 6, '0') || coalesce(sa.value, '') AS source_order,
                 COALESCE(NULLIF(btrim(sa.name), ''), sa.value) AS label,
                 jsonb_build_object(
                   'type', 'area',
                   'id', sa.id,
                   'kind', sa.kind::text,
                   'value', sa.value,
                   'label', COALESCE(NULLIF(btrim(sa.name), ''), sa.value),
                   'has_boundary', sa.boundary IS NOT NULL,
                   -- Centroid so the pin-drop picker can jump to the zone the shopper picked.
                   'lat', CASE WHEN sa.boundary IS NULL THEN NULL
                               ELSE round(extensions.ST_Y(extensions.ST_Centroid(sa.boundary))::numeric, 6) END,
                   'lng', CASE WHEN sa.boundary IS NULL THEN NULL
                               ELSE round(extensions.ST_X(extensions.ST_Centroid(sa.boundary))::numeric, 6) END
                 ) AS row_json
            FROM public.service_areas sa
           WHERE sa.active = true
             AND (lower(sa.value) LIKE '%' || v_pat || '%'
                  OR lower(coalesce(btrim(sa.name), '')) LIKE '%' || v_pat || '%')

          UNION ALL

          -- ─── The caller's own saved addresses (private, filtered by auth.uid()) ───
          SELECT CASE
                   WHEN lower(coalesce(a.label, '')) = v_raw
                     OR lower(coalesce(a.area, '')) = v_raw
                     OR lower(coalesce(a.city, '')) = v_raw THEN 1
                   WHEN lower(coalesce(a.label, '')) LIKE v_pat || '%'
                     OR lower(coalesce(a.area, '')) LIKE v_pat || '%'
                     OR lower(coalesce(a.city, '')) LIKE v_pat || '%'
                     OR lower(coalesce(a.address_lines, '')) LIKE v_pat || '%'
                     OR lower(coalesce(a.landmark, '')) LIKE v_pat || '%' THEN 3
                   ELSE 5
                 END AS match_rank,
                 CASE WHEN a.is_default THEN '0' ELSE '1' END || coalesce(a.label, a.id::text) AS source_order,
                 coalesce(nullif(btrim(a.label), ''), nullif(btrim(a.area), ''), a.id::text) AS label,
                 jsonb_build_object(
                   'type', 'address',
                   'id', a.id,
                   'label', a.label,
                   'recipient_name', a.recipient_name,
                   'phone', a.phone,
                   'address_lines', a.address_lines,
                   'landmark', a.landmark,
                   'area', a.area,
                   'city', a.city,
                   'state', a.state,
                   'postal_code', a.postal_code,
                   'lat', a.lat,
                   'lng', a.lng
                 ) AS row_json
            FROM public.addresses a
           WHERE v_uid IS NOT NULL
             AND a.customer_id = v_uid
             AND (lower(coalesce(a.label, '')) LIKE '%' || v_pat || '%'
                  OR lower(coalesce(a.area, '')) LIKE '%' || v_pat || '%'
                  OR lower(coalesce(a.city, '')) LIKE '%' || v_pat || '%'
                  OR lower(coalesce(a.state, '')) LIKE '%' || v_pat || '%'
                  OR lower(coalesce(a.landmark, '')) LIKE '%' || v_pat || '%'
                  OR lower(coalesce(a.address_lines, '')) LIKE '%' || v_pat || '%'
                  OR lower(coalesce(a.postal_code, '')) LIKE '%' || v_pat || '%'
                  OR lower(coalesce(a.recipient_name, '')) LIKE '%' || v_pat || '%')
        ) m
       ORDER BY m.match_rank, m.source_order, m.label
       LIMIT v_cap
    ) picked;

  RETURN v_rows;
END;
$$;

COMMENT ON FUNCTION public.search_area_suggestions IS
  'M4 autocomplete: active service_areas values plus the caller''s own saved addresses, ranked '
  'exact > prefix > substring and capped at 10. SECURITY DEFINER, so the private half is '
  'filtered on auth.uid() here rather than by RLS, and is empty for an anonymous caller. '
  'Exposes no amounts.';

-- Areas are already public through delivery_zone_map, so anon may search them; the address half
-- self-limits through auth.uid().
REVOKE ALL ON FUNCTION public.search_area_suggestions(text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.search_area_suggestions(text, integer) TO anon, authenticated;

NOTIFY pgrst, 'reload schema';
