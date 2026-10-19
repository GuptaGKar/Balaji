-- M1: customer addresses, orders, order items, status events, assignments,
--     and the order state machine (IMPLEMENTATION_PLAN.md §4.2, §4.3)

CREATE TABLE IF NOT EXISTS addresses (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  label text NOT NULL DEFAULT '',
  recipient_name text NOT NULL,
  phone text NOT NULL,
  address_lines text NOT NULL,
  landmark text,
  area text,
  city text NOT NULL DEFAULT '',
  state text NOT NULL DEFAULT '',
  postal_code text NOT NULL,
  lat double precision,
  lng double precision,
  is_default boolean NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_addresses_customer ON addresses(customer_id);

CREATE TABLE IF NOT EXISTS orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  public_order_number text NOT NULL UNIQUE,
  customer_id uuid NOT NULL REFERENCES profiles(id),
  address_snapshot jsonb NOT NULL,
  status order_status NOT NULL DEFAULT 'pending_review',
  payment_method payment_method NOT NULL DEFAULT 'cod',
  payment_status payment_status NOT NULL DEFAULT 'unpaid',
  item_subtotal_paise bigint NOT NULL DEFAULT 0 CHECK (item_subtotal_paise >= 0),
  discount_paise bigint NOT NULL DEFAULT 0 CHECK (discount_paise >= 0),
  delivery_fee_paise bigint NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
  tax_paise bigint NOT NULL DEFAULT 0 CHECK (tax_paise >= 0),
  grand_total_paise bigint NOT NULL DEFAULT 0 CHECK (grand_total_paise >= 0),
  fee_breakdown jsonb,
  customer_note text,
  assigned_to uuid REFERENCES profiles(id),
  idempotency_key text,
  cod_collected_paise bigint CHECK (cod_collected_paise IS NULL OR cod_collected_paise >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  accepted_at timestamptz,
  delivered_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_created ON orders(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_idempotency ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS order_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id uuid REFERENCES products(id) ON DELETE SET NULL,
  sku_snapshot text NOT NULL,
  name_snapshot text NOT NULL,
  quantity_type_snapshot quantity_type NOT NULL,
  unit_label_snapshot text NOT NULL,
  price_basis_qty_snapshot bigint NOT NULL,
  qty_base bigint NOT NULL CHECK (qty_base > 0),
  unit_price_paise bigint NOT NULL CHECK (unit_price_paise >= 0),
  line_total_paise bigint NOT NULL CHECK (line_total_paise >= 0),
  removed_at timestamptz,
  removed_reason text
);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);

CREATE TABLE IF NOT EXISTS order_status_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  from_status order_status,
  to_status order_status NOT NULL,
  actor_id uuid REFERENCES profiles(id),
  reason text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_order_status_events_order ON order_status_events(order_id, created_at DESC);

CREATE TABLE IF NOT EXISTS order_assignments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  delivery_user_id uuid NOT NULL REFERENCES profiles(id),
  assigned_by uuid REFERENCES profiles(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  unassigned_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_order_assignments_order ON order_assignments(order_id);
CREATE INDEX IF NOT EXISTS idx_order_assignments_active
  ON order_assignments(delivery_user_id) WHERE unassigned_at IS NULL;

CREATE TABLE IF NOT EXISTS order_transitions (
  from_status order_status NOT NULL,
  to_status order_status NOT NULL,
  allowed_roles user_role[] NOT NULL,
  PRIMARY KEY (from_status, to_status)
);

INSERT INTO order_transitions (from_status, to_status, allowed_roles) VALUES
  ('pending_review',    'accepted',           ARRAY['staff', 'manager']::user_role[]),
  ('pending_review',    'rejected',           ARRAY['staff', 'manager']::user_role[]),
  ('pending_review',    'cancelled',          ARRAY['customer', 'staff', 'manager']::user_role[]),
  ('accepted',          'preparing',          ARRAY['staff', 'manager']::user_role[]),
  ('accepted',          'cancelled',          ARRAY['staff', 'manager']::user_role[]),
  ('preparing',         'ready_for_dispatch', ARRAY['staff', 'manager']::user_role[]),
  ('preparing',         'cancelled',          ARRAY['staff', 'manager']::user_role[]),
  ('ready_for_dispatch','out_for_delivery',   ARRAY['staff', 'manager', 'delivery']::user_role[]),
  ('ready_for_dispatch','cancelled',          ARRAY['staff', 'manager']::user_role[]),
  ('out_for_delivery',  'delivered',          ARRAY['staff', 'manager', 'delivery']::user_role[]),
  ('out_for_delivery',  'delivery_failed',    ARRAY['staff', 'manager', 'delivery']::user_role[])
ON CONFLICT (from_status, to_status) DO NOTHING;
