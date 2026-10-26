-- M1: inventory movements and billing/POS tables (IMPLEMENTATION_PLAN.md §4.2)

CREATE TABLE IF NOT EXISTS inventory_movements (
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

CREATE INDEX IF NOT EXISTS idx_inventory_movements_product
  ON inventory_movements(product_id, created_at DESC);

CREATE TABLE IF NOT EXISTS bill_counters (
  fy text PRIMARY KEY,
  last_no bigint NOT NULL DEFAULT 0 CHECK (last_no >= 0)
);

CREATE TABLE IF NOT EXISTS pos_sales (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  idempotency_key text NOT NULL UNIQUE,
  cashier_id uuid NOT NULL REFERENCES profiles(id),
  payment_method payment_method NOT NULL DEFAULT 'cod',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bills (
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

CREATE INDEX IF NOT EXISTS idx_bills_order ON bills(order_id);
CREATE INDEX IF NOT EXISTS idx_bills_pos_sale ON bills(pos_sale_id);
CREATE INDEX IF NOT EXISTS idx_bills_issued ON bills(issued_at DESC);

CREATE TABLE IF NOT EXISTS bill_items (
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

CREATE INDEX IF NOT EXISTS idx_bill_items_bill ON bill_items(bill_id);
