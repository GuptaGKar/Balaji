-- ============================================================
-- 20261105000004  A read for the editor that includes hidden sections
-- ============================================================
-- Found by using the dashboard for real: deactivating a section made it vanish from the
-- Storefront page entirely, because that page loads through get_storefront, which filters
-- `active = true` for the customers it serves. The manager could hide a shelf and then never
-- see it again - no way to show it, rename it or delete it except by writing SQL.
--
-- Two reads, two audiences:
--   * get_storefront(p_page)          - the customer read. Active rows only, ids only.
--   * list_storefront_sections(p_page)- the editor read. Every row the caller may see at all,
--     including inactive ones, still ids only.
--
-- This is SECURITY INVOKER, so RLS decides what "every row the caller may see" means. For a
-- manager the storefront_sections_manager_all policy covers inactive rows too, so they get the
-- complete list. For anyone else the read policy already limits them to active rows, which is
-- exactly what get_storefront would have returned - so exposing this function is not a widening:
-- a customer learns nothing here they could not already read, and no new grant is needed beyond
-- what SELECT on the table gives them.

CREATE OR REPLACE FUNCTION public.list_storefront_sections(p_page text)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
  SELECT coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id',           s.id,
        'title',        s.title,
        'subtitle',     s.subtitle,
        'section_type', s.section_type,
        'active',       s.active,
        'sort_order',   s.sort_order,
        'updated_at',   s.updated_at,
        'item_ids',     i.item_ids
      ) ORDER BY s.sort_order
    ),
    '[]'::jsonb
  )
  FROM (
    SELECT sc.id, sc.title, sc.subtitle, sc.section_type, sc.active, sc.sort_order, sc.updated_at
      FROM public.storefront_sections sc
     WHERE sc.page = p_page
  ) s
  LEFT JOIN LATERAL (
    SELECT coalesce(
             jsonb_agg(
               coalesce(it.product_id, it.category_id)
               ORDER BY it.sort_order
             ),
             '[]'::jsonb
           ) AS item_ids
      FROM public.storefront_section_items it
     WHERE it.section_id = s.id
  ) i ON TRUE
$$;

COMMENT ON FUNCTION public.list_storefront_sections(text) IS
  'Editor read for one storefront page: every section the caller may see, inactive included, with item ids only. The customer read is get_storefront(text), which hides inactive rows.';

-- Managers compose the storefront from the dashboard, which authenticates as authenticated (their
-- JWT role) or as service_role for server-side calls. anon has no business listing hidden rows.
--
-- The revoke is not optional. Supabase creates this function with EXECUTE already granted to PUBLIC
-- and anon, so the GRANT below on its own would add a grantee to a set that was already open - the
-- same trap 20261105000003 had to close for the write RPCs, and suite 050 S50-10/S50-11 fail if it
-- is repeated here.
REVOKE EXECUTE ON FUNCTION public.list_storefront_sections(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.list_storefront_sections(text) TO authenticated, service_role;

NOTIFY pgrst, 'reload schema';
