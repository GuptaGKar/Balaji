-- M1: enum types (IMPLEMENTATION_PLAN.md §4.2)
-- Values are fixed business vocabularies; changes require a new migration.

CREATE TYPE user_role AS ENUM ('customer', 'staff', 'manager', 'delivery');

CREATE TYPE order_status AS ENUM (
  'pending_review',
  'accepted',
  'preparing',
  'ready_for_dispatch',
  'out_for_delivery',
  'delivered',
  'rejected',
  'cancelled',
  'delivery_failed'
);

CREATE TYPE payment_method AS ENUM ('cod', 'online');

CREATE TYPE payment_status AS ENUM (
  'unpaid',
  'pending',
  'paid',
  'failed',
  'refund_pending',
  'partially_refunded',
  'refunded'
);

CREATE TYPE quantity_type AS ENUM ('WEIGHTED', 'PACKED');

CREATE TYPE service_area_kind AS ENUM ('pincode', 'neighbourhood');

CREATE TYPE inventory_reason AS ENUM (
  'order_placed',
  'order_cancelled',
  'order_rejected',
  'item_removed',
  'pos_sale',
  'pos_void',
  'manual_adjustment',
  'correction'
);

CREATE TYPE bill_source AS ENUM ('ORDER', 'POS');

CREATE TYPE bill_status AS ENUM ('ISSUED', 'VOID');

CREATE TYPE tax_mode AS ENUM ('none', 'inclusive', 'exclusive');
