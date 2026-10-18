-- M1: catalogue tables (IMPLEMENTATION_PLAN.md §4.2)

CREATE TABLE categories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  slug text NOT NULL UNIQUE,
  sort_order integer NOT NULL DEFAULT 0,
  active boolean NOT NULL DEFAULT true
);

CREATE INDEX idx_categories_active ON categories(active);

-- products: money in integer paise; quantities in integer base units
-- (grams for WEIGHTED, pack count for PACKED). No floating point anywhere.
--
-- price_basis_qty = base units covered by price_paise:
--   PACKED  -> always 1 (price is per pack/piece)
--   WEIGHTED-> 1000 (per kg) or 100 (per 100 g)
-- step_qty  = smallest allowed quantity step in base units (default 10 g for WEIGHTED).
-- Products are archived (active=false), never hard-deleted once referenced.
CREATE TABLE products (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  sku text NOT NULL UNIQUE,
  name text NOT NULL,
  description text,
  category_id uuid REFERENCES categories(id) ON DELETE SET NULL,
  image_path text,
  quantity_type quantity_type NOT NULL,
  price_paise bigint NOT NULL CHECK (price_paise >= 0),
  price_basis_qty bigint NOT NULL,
  unit_label text NOT NULL,
  pack_size_label text,
  step_qty bigint NOT NULL CHECK (step_qty > 0),
  min_qty bigint NOT NULL CHECK (min_qty > 0),
  max_qty bigint CHECK (max_qty IS NULL OR max_qty >= min_qty),
  stock_qty bigint NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
  low_stock_threshold bigint NOT NULL DEFAULT 0 CHECK (low_stock_threshold >= 0),
  active boolean NOT NULL DEFAULT true,
  available boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- PACKED: exactly one piece per priced unit, integer steps only.
ALTER TABLE products ADD CONSTRAINT chk_products_packed
  CHECK (quantity_type <> 'PACKED' OR (price_basis_qty = 1 AND step_qty = 1));

-- WEIGHTED: price basis must be per 100 g or per kg.
ALTER TABLE products ADD CONSTRAINT chk_products_weighted_basis
  CHECK (quantity_type <> 'WEIGHTED' OR price_basis_qty IN (100, 1000));

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_catalogue ON products(quantity_type, active, available);

CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON products
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Hard-delete protection: a product referenced by order or bill items must be
-- archived, not deleted, so historical snapshots stay resolvable (plan §4.2).
CREATE OR REPLACE FUNCTION prevent_product_delete_when_referenced()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM order_items WHERE product_id = OLD.id)
      OR EXISTS (SELECT 1 FROM bill_items WHERE product_id = OLD.id) THEN
    RAISE EXCEPTION
      'Cannot delete product %: referenced by order or bill items. Archive it instead (set active=false).',
      OLD.sku;
  END IF;
  RETURN OLD;
END;
$$;

CREATE TRIGGER trg_products_prevent_delete BEFORE DELETE ON products
  FOR EACH ROW EXECUTE FUNCTION prevent_product_delete_when_referenced();
