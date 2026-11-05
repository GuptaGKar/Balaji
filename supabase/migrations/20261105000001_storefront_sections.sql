-- ============================================================
-- 20261105000001  Storefront sections, section items, multi-category products
-- ============================================================
-- Lets a manager compose the Home and Categories pages as ordered sections of products or
-- categories, and lets one product belong to several categories at once.
--
-- What this migration deliberately does NOT do:
--   * it inserts no sections and no items. With zero active rows both apps must render exactly
--     the layout they render today, so the feature is dark until a manager composes it.
--   * it touches no money. No price, fee, threshold, coupon or quote column, function or helper
--     is read, renamed or converted here. Money stays integer paise everywhere.
--   * it does not drop or repurpose products.category_id. That column stays the product's
--     PRIMARY category; product_categories is the additional-membership set around it.
--
-- Security notes that matter more than the schema:
--   * get_storefront is SECURITY INVOKER on purpose. It returns ids only, and RLS does the
--     filtering, so an anonymous shopper can never see an inactive section. If it were ever
--     changed to DEFINER it would start returning inactive content to customers.
--   * the payload carries no price, no stock and no fee key at all. Suites 047 assert that,
--     because the point of ids-only is that the app resolves every commercial fact from the
--     catalogue it already loads, from the server.
--   * manager writes use the same public.current_role() = 'manager'::public.user_role test the
--     rest of the schema uses. Grants are listed explicitly; GRANT ALL is not used here.
--   * save_storefront_section replaces items only after every validation passes, and the whole
--     thing is one statement batch, so a rejected 31st item cannot leave a half-saved section.
--   * p_expected_updated_at gives two managers optimistic concurrency: the loser is told the
--     section changed instead of quietly overwriting the winner.

-- ------------------------------------------------------------
-- 1. Sections
-- ------------------------------------------------------------
CREATE TABLE public.storefront_sections (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  page          text NOT NULL CHECK (page IN ('home','categories')),
  title         text NOT NULL CHECK (char_length(title) BETWEEN 1 AND 40),
  subtitle      text NULL CHECK (subtitle IS NULL OR char_length(subtitle) <= 80),
  section_type  text NOT NULL CHECK (section_type IN ('products','categories')),
  sort_order    integer NOT NULL,
  active        boolean NOT NULL DEFAULT true,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  updated_by    uuid NULL REFERENCES public.profiles(id) ON DELETE SET NULL
);

COMMENT ON TABLE public.storefront_sections IS
  'Manager-composed sections for the customer Home and Categories pages. Empty means the app uses its built-in layout.';

CREATE INDEX storefront_sections_page_order_idx
  ON public.storefront_sections (page, sort_order);

-- ------------------------------------------------------------
-- 2. Section items
-- ------------------------------------------------------------
CREATE TABLE public.storefront_section_items (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  section_id   uuid NOT NULL REFERENCES public.storefront_sections(id) ON DELETE CASCADE,
  product_id   uuid NULL REFERENCES public.products(id) ON DELETE CASCADE,
  category_id  uuid NULL REFERENCES public.categories(id) ON DELETE CASCADE,
  sort_order   integer NOT NULL,
  -- exactly one of the two references is set
  CHECK ((product_id IS NULL) <> (category_id IS NULL))
);

COMMENT ON TABLE public.storefront_section_items IS
  'Ordered product or category references for one section. Ids only - never prices or stock.';

-- Partial uniques: a plain UNIQUE (section_id, product_id) would treat the NULL side of a
-- category row as distinct and allow duplicate categories.
CREATE UNIQUE INDEX storefront_section_items_product_uniq
  ON public.storefront_section_items (section_id, product_id)
  WHERE product_id IS NOT NULL;

CREATE UNIQUE INDEX storefront_section_items_category_uniq
  ON public.storefront_section_items (section_id, category_id)
  WHERE category_id IS NOT NULL;

CREATE INDEX storefront_section_items_section_order_idx
  ON public.storefront_section_items (section_id, sort_order);

-- ------------------------------------------------------------
-- 3. Caps and type agreement, as triggers with readable messages
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.enforce_storefront_section_limit()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
  v_count integer;
BEGIN
  SELECT count(*) INTO v_count
    FROM public.storefront_sections
   WHERE page = NEW.page AND id IS DISTINCT FROM NEW.id;

  IF v_count >= 20 THEN
    RAISE EXCEPTION 'A page can have at most 20 sections. This page already has %, so remove one before adding another.', v_count
      USING HINT = 'Deactivate a section instead of deleting it if you only want it off the app.';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS storefront_sections_page_cap ON public.storefront_sections;
CREATE TRIGGER storefront_sections_page_cap
  BEFORE INSERT OR UPDATE OF page ON public.storefront_sections
  FOR EACH ROW EXECUTE FUNCTION public.enforce_storefront_section_limit();

CREATE OR REPLACE FUNCTION public.enforce_storefront_section_item_limit()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
  v_count integer;
BEGIN
  SELECT count(*) INTO v_count
    FROM public.storefront_section_items
   WHERE section_id = NEW.section_id AND id IS DISTINCT FROM NEW.id;

  IF v_count >= 30 THEN
    RAISE EXCEPTION 'A section can hold at most 30 items. Section % already has %.', NEW.section_id, v_count
      USING HINT = 'Split the list across two sections if you need more than 30.';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS storefront_section_items_cap ON public.storefront_section_items;
CREATE TRIGGER storefront_section_items_cap
  BEFORE INSERT OR UPDATE OF section_id ON public.storefront_section_items
  FOR EACH ROW EXECUTE FUNCTION public.enforce_storefront_section_item_limit();

CREATE OR REPLACE FUNCTION public.enforce_storefront_item_type()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
  v_type text;
BEGIN
  SELECT section_type INTO v_type
    FROM public.storefront_sections
   WHERE id = NEW.section_id;

  IF v_type IS NULL THEN
    RAISE EXCEPTION 'Section % does not exist, so this item cannot be checked against it.', NEW.section_id;
  END IF;

  IF v_type = 'products' AND NEW.product_id IS NULL THEN
    RAISE EXCEPTION 'A "products" section only accepts products; this row has no product_id.';
  END IF;

  IF v_type = 'categories' AND NEW.category_id IS NULL THEN
    RAISE EXCEPTION 'A "categories" section only accepts categories; this row has no category_id.';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS storefront_section_items_type ON public.storefront_section_items;
CREATE TRIGGER storefront_section_items_type
  BEFORE INSERT OR UPDATE ON public.storefront_section_items
  FOR EACH ROW EXECUTE FUNCTION public.enforce_storefront_item_type();

DROP TRIGGER IF EXISTS set_updated_at_storefront_sections ON public.storefront_sections;
CREATE TRIGGER set_updated_at_storefront_sections
  BEFORE UPDATE ON public.storefront_sections
  FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- ------------------------------------------------------------
-- 4. Multi-category membership
-- ------------------------------------------------------------
CREATE TABLE public.product_categories (
  product_id  uuid NOT NULL REFERENCES public.products(id) ON DELETE CASCADE,
  category_id uuid NOT NULL REFERENCES public.categories(id) ON DELETE CASCADE,
  sort_order  integer NOT NULL DEFAULT 0,
  PRIMARY KEY (product_id, category_id)
);

COMMENT ON TABLE public.product_categories IS
  'Additional category membership. products.category_id remains the PRIMARY category and is always present here.';

CREATE INDEX product_categories_category_idx ON public.product_categories (category_id);

-- Backfill: every existing product gets its primary category as a membership row.
INSERT INTO public.product_categories (product_id, category_id)
SELECT p.id, p.category_id
  FROM public.products p
 WHERE p.category_id IS NOT NULL
ON CONFLICT (product_id, category_id) DO NOTHING;

-- Keep the primary pair present whenever the primary is set or moved.
CREATE OR REPLACE FUNCTION public.sync_product_primary_category()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
  IF NEW.category_id IS NOT NULL THEN
    INSERT INTO public.product_categories (product_id, category_id)
    VALUES (NEW.id, NEW.category_id)
    ON CONFLICT (product_id, category_id) DO NOTHING;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS sync_product_primary_category ON public.products;
CREATE TRIGGER sync_product_primary_category
  AFTER INSERT OR UPDATE OF category_id ON public.products
  FOR EACH ROW EXECUTE FUNCTION public.sync_product_primary_category();

-- Reject deleting the pair that is still the product's primary.
CREATE OR REPLACE FUNCTION public.protect_product_primary_category()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
  v_primary uuid;
BEGIN
  SELECT category_id INTO v_primary FROM public.products WHERE id = OLD.product_id;

  IF v_primary IS NOT NULL AND v_primary = OLD.category_id THEN
    RAISE EXCEPTION 'Category % is the primary category of product %, so it cannot be removed here. Set products.category_id to another category first.',
      OLD.category_id, OLD.product_id
      USING HINT = 'The primary category is what reports, coupon scope and variant grouping use.';
  END IF;

  RETURN OLD;
END;
$$;

DROP TRIGGER IF EXISTS protect_product_primary_category ON public.product_categories;
CREATE TRIGGER protect_product_primary_category
  BEFORE DELETE ON public.product_categories
  FOR EACH ROW EXECUTE FUNCTION public.protect_product_primary_category();

-- ------------------------------------------------------------
-- 5. Row-Level Security
-- ------------------------------------------------------------
ALTER TABLE public.storefront_sections       ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.storefront_section_items  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.product_categories        ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS storefront_sections_read_active ON public.storefront_sections;
CREATE POLICY storefront_sections_read_active ON public.storefront_sections
  FOR SELECT TO anon, authenticated
  USING (active = true);

DROP POLICY IF EXISTS storefront_sections_manager_all ON public.storefront_sections;
CREATE POLICY storefront_sections_manager_all ON public.storefront_sections
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::public.user_role)
  WITH CHECK (public.current_role() = 'manager'::public.user_role);

DROP POLICY IF EXISTS storefront_section_items_read_active ON public.storefront_section_items;
CREATE POLICY storefront_section_items_read_active ON public.storefront_section_items
  FOR SELECT TO anon, authenticated
  USING (EXISTS (
    SELECT 1 FROM public.storefront_sections s
     WHERE s.id = storefront_section_items.section_id AND s.active = true
  ));

DROP POLICY IF EXISTS storefront_section_items_manager_all ON public.storefront_section_items;
CREATE POLICY storefront_section_items_manager_all ON public.storefront_section_items
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::public.user_role)
  WITH CHECK (public.current_role() = 'manager'::public.user_role);

-- Membership is public-read: the app resolves which products sit in a category from the
-- catalogue it already fetches, so this only has to be readable, not filtered.
DROP POLICY IF EXISTS product_categories_read ON public.product_categories;
CREATE POLICY product_categories_read ON public.product_categories
  FOR SELECT TO anon, authenticated
  USING (true);

DROP POLICY IF EXISTS product_categories_manager_all ON public.product_categories;
CREATE POLICY product_categories_manager_all ON public.product_categories
  FOR ALL TO authenticated
  USING (public.current_role() = 'manager'::public.user_role)
  WITH CHECK (public.current_role() = 'manager'::public.user_role);

-- ------------------------------------------------------------
-- 6. Reads
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_storefront(p_page text)
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
        'updated_at',   s.updated_at,
        'item_ids',     i.item_ids
      ) ORDER BY s.sort_order
    ),
    '[]'::jsonb
  )
  FROM (
    SELECT sc.id, sc.title, sc.subtitle, sc.section_type, sc.updated_at, sc.sort_order
      FROM public.storefront_sections sc
     WHERE sc.page = p_page AND sc.active = true
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

COMMENT ON FUNCTION public.get_storefront(text) IS
  'Active sections for one page, ids only. SECURITY INVOKER so RLS decides visibility. Returns [] when none.';

-- ------------------------------------------------------------
-- 7. Manager writes
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.save_storefront_section(
  p_id                  uuid        DEFAULT NULL,
  p_page                text        DEFAULT 'home',
  p_title               text        DEFAULT '',
  p_subtitle            text        DEFAULT NULL,
  p_section_type        text        DEFAULT 'products',
  p_active              boolean     DEFAULT true,
  p_item_ids            uuid[]      DEFAULT array[]::uuid[],
  p_expected_updated_at timestamptz DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_id        uuid;
  v_updated   timestamptz;
  v_existing  integer;
  v_item      uuid;
  v_type_ok   boolean;
  v_position  integer := 0;
BEGIN
  -- Defence in depth: RLS already refuses a non-manager, but a clear message beats a
  -- bare "row-level security" error, and it still holds if the policy is ever relaxed.
  IF public.current_role() IS DISTINCT FROM 'manager'::public.user_role THEN
    RAISE EXCEPTION 'Only a manager can change the storefront layout.'
      USING HINT = 'Ask a manager to compose the section, or use the Storefront page in the dashboard.';
  END IF;

  IF p_page NOT IN ('home','categories') THEN
    RAISE EXCEPTION 'Unknown page %. Use home or categories.', p_page;
  END IF;

  IF p_section_type NOT IN ('products','categories') THEN
    RAISE EXCEPTION 'Unknown section type %. Use products or categories.', p_section_type;
  END IF;

  IF p_title IS NULL OR char_length(btrim(p_title)) < 1 OR char_length(p_title) > 40 THEN
    RAISE EXCEPTION 'A section heading is required and must be 1 to 40 characters.';
  END IF;

  IF p_subtitle IS NOT NULL AND char_length(p_subtitle) > 80 THEN
    RAISE EXCEPTION 'A section subtitle must be 80 characters or fewer.';
  END IF;

  IF array_length(p_item_ids, 1) > 30 THEN
    RAISE EXCEPTION 'A section can hold at most 30 items; % were given.', array_length(p_item_ids, 1);
  END IF;

  -- Reject duplicates before touching anything.
  SELECT count(*) INTO v_existing
    FROM (SELECT unnest(p_item_ids) AS x GROUP BY 1 HAVING count(*) > 1) d;
  IF v_existing > 0 THEN
    RAISE EXCEPTION 'This section lists the same item more than once; each product or category may appear only once.'
      USING HINT = format('%s repeated group(s) found in the %s ids given.', v_existing, coalesce(array_length(p_item_ids, 1), 0));
  END IF;

  -- Every id must exist and match the section type.
  FOREACH v_item IN ARRAY coalesce(p_item_ids, array[]::uuid[]) LOOP
    IF p_section_type = 'products' THEN
      SELECT EXISTS (SELECT 1 FROM public.products pr WHERE pr.id = v_item) INTO v_type_ok;
      IF NOT v_type_ok THEN
        RAISE EXCEPTION 'Product % does not exist, so this section cannot be saved.', v_item;
      END IF;
    ELSE
      SELECT EXISTS (SELECT 1 FROM public.categories c WHERE c.id = v_item) INTO v_type_ok;
      IF NOT v_type_ok THEN
        RAISE EXCEPTION 'Category % does not exist, so this section cannot be saved.', v_item;
      END IF;
    END IF;
  END LOOP;

  IF p_id IS NULL THEN
    SELECT coalesce(max(sort_order), 0) + 1 INTO v_position
      FROM public.storefront_sections WHERE page = p_page;

    INSERT INTO public.storefront_sections
      (page, title, subtitle, section_type, sort_order, active, updated_by)
    VALUES
      (p_page, btrim(p_title), p_subtitle, p_section_type, v_position, p_active, auth.uid())
    RETURNING id, updated_at INTO v_id, v_updated;
  ELSE
    IF p_expected_updated_at IS NOT NULL THEN
      UPDATE public.storefront_sections
         SET page = p_page, title = btrim(p_title), subtitle = p_subtitle,
             section_type = p_section_type, active = p_active, updated_by = auth.uid()
       WHERE id = p_id AND updated_at = p_expected_updated_at
       RETURNING id, updated_at INTO v_id, v_updated;

      IF NOT FOUND THEN
        RAISE EXCEPTION 'This section was changed by someone else since you opened it. Reload before saving again.'
          USING HINT = 'Reloading shows the current items so nothing you did is silently lost.';
      END IF;
    ELSE
      UPDATE public.storefront_sections
         SET page = p_page, title = btrim(p_title), subtitle = p_subtitle,
             section_type = p_section_type, active = p_active, updated_by = auth.uid()
       WHERE id = p_id
       RETURNING id, updated_at INTO v_id, v_updated;

      IF NOT FOUND THEN
        RAISE EXCEPTION 'Section % does not exist.', p_id;
      END IF;
    END IF;
  END IF;

  -- Replacing items only happens once every check above has passed.
  DELETE FROM public.storefront_section_items WHERE section_id = v_id;

  v_position := 0;
  FOREACH v_item IN ARRAY coalesce(p_item_ids, array[]::uuid[]) LOOP
    v_position := v_position + 1;
    IF p_section_type = 'products' THEN
      INSERT INTO public.storefront_section_items (section_id, product_id, sort_order)
      VALUES (v_id, v_item, v_position);
    ELSE
      INSERT INTO public.storefront_section_items (section_id, category_id, sort_order)
      VALUES (v_id, v_item, v_position);
    END IF;
  END LOOP;

  RETURN jsonb_build_object('id', v_id, 'updated_at', v_updated);
END;
$$;

COMMENT ON FUNCTION public.save_storefront_section IS
  'Manager-only create/update of one section plus its whole item list, in one transaction. Validates before writing; rejects a stale p_expected_updated_at.';

CREATE OR REPLACE FUNCTION public.reorder_storefront_sections(p_page text, p_ordered_ids uuid[])
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
DECLARE
  v_id uuid;
  v_pos integer := 0;
BEGIN
  IF public.current_role() IS DISTINCT FROM 'manager'::public.user_role THEN
    RAISE EXCEPTION 'Only a manager can reorder the storefront layout.';
  END IF;

  IF p_page NOT IN ('home','categories') THEN
    RAISE EXCEPTION 'Unknown page %. Use home or categories.', p_page;
  END IF;

  FOREACH v_id IN ARRAY coalesce(p_ordered_ids, array[]::uuid[]) LOOP
    IF NOT EXISTS (SELECT 1 FROM public.storefront_sections WHERE id = v_id AND page = p_page) THEN
      RAISE EXCEPTION 'Section % is not a section on page %. Sections cannot be moved between pages by reordering.', v_id, p_page;
    END IF;
  END LOOP;

  FOREACH v_id IN ARRAY coalesce(p_ordered_ids, array[]::uuid[]) LOOP
    v_pos := v_pos + 1;
    UPDATE public.storefront_sections SET sort_order = v_pos, updated_by = auth.uid()
     WHERE id = v_id;
  END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION public.delete_storefront_section(p_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public, pg_temp
AS $$
BEGIN
  IF public.current_role() IS DISTINCT FROM 'manager'::public.user_role THEN
    RAISE EXCEPTION 'Only a manager can delete a storefront section.';
  END IF;

  DELETE FROM public.storefront_section_items WHERE section_id = p_id;
  DELETE FROM public.storefront_sections WHERE id = p_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Section % does not exist.', p_id;
  END IF;
END;
$$;

-- ------------------------------------------------------------
-- 8. Grants (explicit, never GRANT ALL)
-- ------------------------------------------------------------
GRANT SELECT ON public.storefront_sections, public.storefront_section_items, public.product_categories
  TO anon, authenticated;

GRANT INSERT, UPDATE, DELETE
  ON public.storefront_sections, public.storefront_section_items, public.product_categories
  TO authenticated;

GRANT EXECUTE ON FUNCTION public.get_storefront(text) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.save_storefront_section(uuid, text, text, text, text, boolean, uuid[], timestamptz) TO authenticated;
GRANT EXECUTE ON FUNCTION public.reorder_storefront_sections(text, uuid[]) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_storefront_section(uuid) TO authenticated;

NOTIFY pgrst, 'reload schema';
