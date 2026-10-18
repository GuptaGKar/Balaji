-- M1: inventory movements and billing/POS tables (IMPLEMENTATION_PLAN.md §4.2)

-- Append-only stock audit trail. products.stock_qty must always equal the sum of
-- movements for that product; a reconciliation test runs in CI and nightly (§12).
CREATE TABLE inventory_movements (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id uuid NOT NULL REFERENCES products(id),
  delta_qty bigint NOT NULL,
  reason inventory_reason NOT NULL,
  reference_type text,
  reference_id uuid,
  actor_id uuid REFERENCES profiles(id),
  note text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_inventory_movements_product
  ON inventory_movements(product_id, created_at DESC);

-- Gapless bill numbering: one counter row per financial year, incremented
-- row-locked inside the sale transaction (plan §4.2, D-BILLNO: BPS/<FY>/<6-digit>).
CREATE TABLE bill_counters (
  fy text PRIMARY KEY,
  last_no bigint NOT NULL DEFAULT 0 CHECK (last_no >= 0)
);

-- POS sales header; idempotency_key makes duplicate submits a no-op (§4.8).
CREATE TABLE pos_sales (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  idempotency_key text NOT NULL UNIQUE,
  cashier_id uuid NOT NULL REFERENCES profiles(id),
  payment_method payment_method NOT NULL DEFAULT 'cod',
  created_at timestamptz NOT NULL DEFAULT now()
);

-- One billing system for online orders and in-store POS sales (plan §9).
-- Bill content is rendered from snapshots, never recomputed from the catalogue.
CREATE TABLE bills (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  bill_number text NOT NULL UNIQUE,
  source bill_source NOT NULL,
  order_id uuid REFERENCES orders(id) ON DELETE SET NULL,
  pos_sale_id uuid REFERENCES pos_sales(id) ON DELETE SET NULL,
  status bill_status NOT NULL DEFAULT 'ISSUED',
  issued_at timestamptz NOT NULL DEFAULT now(),
  issued_by uuid REFERENCES profiles(id),
  customer_name text,
  customer_phone text,
  subtotal_paise bigint NOT NULL DEFAULT 0 CHECK (subtotal_paise >= 0),
  discount_paise bigint NOT NULL DEFAULT 0 CHECK (discount_paise >= 0),
  delivery_fee_paise bigint NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
  tax_paise bigint NOT NULL DEFAULT 0 CHECK (tax_paise >= 0),
  grand_total_paise bigint NOT NULL DEFAULT 0 CHECK (grand_total_paise >= 0),
  payment_method payment_method NOT NULL DEFAULT 'cod',
  payment_status payment_status NOT NULL DEFAULT 'unpaid',
  void_reason text,
  voided_by uuid REFERENCES profiles(id),
  voided_at timestamptz
);

CREATE INDEX idx_bills_order ON bills(order_id);
CREATE INDEX idx_bills_pos_sale ON bills(pos_sale_id);
CREATE INDEX idx_bills_issued ON bills(issued_at DESC);

-- Immutable line-item snapshots. A bill is never edited; corrections happen via
-- void + new bill (M9), with the original linked by the void audit trail.
CREATE TABLE bill_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  bill_id uuid NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
  product_id uuid REFERENCES products(id) ON DELETE SET NULL,
  sku text NOT NULL,
  name text NOT NULL,
  quantity_type quantity_type NOT NULL,
  qty_base bigint NOT NULL CHECK (qty_base > 0),
  unit_label text NOT NULL,
  price_basis_qty bigint NOT NULL,
  unit_price_paise bigint NOT NULL CHECK (unit_price_paise >= 0),
  line_total_paise bigint NOT NULL CHECK (line_total_paise >= 0)
);

CREATE INDEX idx_bill_items_bill ON bill_items(bill_id);
