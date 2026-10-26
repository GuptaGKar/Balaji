-- M1: enum types (IMPLEMENTATION_PLAN.md §4.2)
-- Idempotent enum creation guards to prevent 42710 errors on existing databases.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
        CREATE TYPE user_role AS ENUM ('customer', 'staff', 'manager', 'delivery');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'order_status') THEN
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
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_method') THEN
        CREATE TYPE payment_method AS ENUM ('cod', 'online');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_status') THEN
        CREATE TYPE payment_status AS ENUM (
          'unpaid',
          'pending',
          'paid',
          'failed',
          'refund_pending',
          'partially_refunded',
          'refunded'
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'quantity_type') THEN
        CREATE TYPE quantity_type AS ENUM ('weighted', 'packed', 'WEIGHTED', 'PACKED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'service_area_kind') THEN
        CREATE TYPE service_area_kind AS ENUM ('pincode', 'neighbourhood');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'inventory_reason') THEN
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
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'bill_source') THEN
        CREATE TYPE bill_source AS ENUM ('ORDER', 'POS');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'bill_status') THEN
        CREATE TYPE bill_status AS ENUM ('ISSUED', 'VOID');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tax_mode') THEN
        CREATE TYPE tax_mode AS ENUM ('none', 'inclusive', 'exclusive');
    END IF;
END $$;
