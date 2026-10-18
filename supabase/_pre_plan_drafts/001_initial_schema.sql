-- Balaji Provision Store - Initial Database Schema
-- Version 1.0 - Core tables for hyperlocal grocery platform

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- ENUMS
-- ============================================
CREATE TYPE user_role AS ENUM ('customer', 'manager', 'delivery_person', 'admin');
CREATE TYPE order_status AS ENUM ('pending', 'confirmed', 'preparing', 'ready_for_pickup', 'out_for_delivery', 'delivered', 'cancelled', 'returned');
CREATE TYPE payment_method AS ENUM ('cod', 'online');
CREATE TYPE payment_status AS ENUM ('pending', 'paid', 'failed', 'refunded', 'partial_refund');
CREATE TYPE product_type AS ENUM ('weighted', 'packed');
CREATE TYPE unit_type AS ENUM ('g', 'kg', 'pack', 'piece');
CREATE TYPE bill_type AS ENUM ('online_order', 'manual_pos');
CREATE TYPE stock_movement_type AS ENUM ('sale', 'return', 'adjustment', 'restock', 'waste', 'correction');
CREATE TYPE audit_action AS ENUM ('create', 'update', 'delete', 'activate', 'deactivate', 'assign', 'status_change', 'price_change', 'stock_change', 'threshold_change', 'service_area_change');

-- ============================================
-- CORE TABLES
-- ============================================

-- Profiles (extends auth.users)
CREATE TABLE profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    role user_role NOT NULL DEFAULT 'customer',
    full_name TEXT,
    phone TEXT UNIQUE,
    email TEXT,
    avatar_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ
);

-- Categories
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Products
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sku TEXT UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    product_type product_type NOT NULL DEFAULT 'packed',
    unit unit_type NOT NULL DEFAULT 'pack',
    price_paise INTEGER NOT NULL CHECK (price_paise >= 0), -- price per unit (per kg for weighted, per pack for packed)
    price_basis_unit unit_type NOT NULL DEFAULT 'pack', -- e.g., 'kg' for weighted, 'pack' for packed
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0), -- in base units (grams for weighted)
    min_stock_alert INTEGER NOT NULL DEFAULT 0,
    image_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_available BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES profiles(id),
    updated_by UUID REFERENCES profiles(id)
);

-- Service Areas (neighbourhoods/PIN codes)
CREATE TABLE service_areas (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL, -- neighbourhood name
    pin_code TEXT, -- 6-digit PIN code
    boundary_geojson JSONB, -- optional: polygon for map-based areas
    is_active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES profiles(id),
    updated_by UUID REFERENCES profiles(id)
);

-- Settings (configurable by manager)
CREATE TABLE settings (
    key TEXT PRIMARY KEY,
    value JSONB NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID REFERENCES profiles(id)
);

-- Insert default settings
INSERT INTO settings (key, value, description) VALUES
    ('delivery_charge_threshold_paise', '25000', 'Minimum subtotal (paise) before delivery fee applies. Orders below incur delivery_fee_paise.'),
    ('delivery_fee_paise', '3000', 'Delivery fee (paise) charged when subtotal < delivery_charge_threshold_paise'),
    ('free_delivery_threshold_paise', '50000', 'Minimum subtotal (paise) for free delivery. At/above this, delivery fee is 0.'),
    ('store_phone', '"9837803293"', 'Store contact phone number'),
    ('store_name', '"Balaji Provision Store"', 'Store display name'),
    ('order_cutoff_time', '"22:00"', 'Daily order cutoff time (24hr format)'),
    ('operating_hours_start', '"08:00"', 'Store opening time'),
    ('operating_hours_end', '"22:00"', 'Store closing time');

-- Orders
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_number TEXT NOT NULL UNIQUE, -- human-readable: BAL-YYYYMMDD-XXXX
    customer_id UUID NOT NULL REFERENCES profiles(id),
    delivery_person_id UUID REFERENCES profiles(id), -- assigned delivery person
    status order_status NOT NULL DEFAULT 'pending',
    payment_method payment_method NOT NULL DEFAULT 'cod',
    payment_status payment_status NOT NULL DEFAULT 'pending',
    payment_intent_id TEXT, -- for online payments
    subtotal_paise INTEGER NOT NULL DEFAULT 0 CHECK (subtotal_paise >= 0),
    delivery_fee_paise INTEGER NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
    discount_paise INTEGER NOT NULL DEFAULT 0 CHECK (discount_paise >= 0),
    tax_paise INTEGER NOT NULL DEFAULT 0 CHECK (tax_paise >= 0),
    total_paise INTEGER NOT NULL DEFAULT 0 CHECK (total_paise >= 0),
    -- Delivery address snapshot
    delivery_address_line1 TEXT NOT NULL,
    delivery_address_line2 TEXT,
    delivery_landmark TEXT,
    delivery_pin_code TEXT NOT NULL,
    delivery_area_id UUID REFERENCES service_areas(id), -- which service area was matched
    delivery_instructions TEXT,
    -- Contact snapshot
    customer_name TEXT NOT NULL,
    customer_phone TEXT NOT NULL,
    -- Timestamps
    estimated_delivery_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    ready_for_pickup_at TIMESTAMPTZ,
    picked_up_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Order Items (with immutable price/name/unit snapshots)
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE SET NULL,
    product_name TEXT NOT NULL, -- snapshot
    product_sku TEXT, -- snapshot
    product_type product_type NOT NULL, -- snapshot
    unit unit_type NOT NULL, -- snapshot
    price_basis_unit unit_type NOT NULL, -- snapshot
    price_paise INTEGER NOT NULL CHECK (price_paise >= 0), -- snapshot: price per price_basis_unit
    quantity INTEGER NOT NULL CHECK (quantity > 0), -- in base units (grams for weighted)
    line_total_paise INTEGER NOT NULL CHECK (line_total_paise >= 0), -- calculated: price_paise * quantity (with unit conversion)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Delivery Person Assignments
CREATE TABLE delivery_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    delivery_person_id UUID NOT NULL REFERENCES profiles(id),
    assigned_by UUID REFERENCES profiles(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    proof_of_delivery JSONB, -- {type: 'otp'|'photo'|'signature', data: ..., timestamp: ...}
    notes TEXT
);

-- Bills (for both online orders and manual POS)
CREATE TABLE bills (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    bill_number TEXT NOT NULL UNIQUE, -- human-readable: BILL-YYYYMMDD-XXXX
    bill_type bill_type NOT NULL,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL, -- for online orders
    customer_id UUID REFERENCES profiles(id), -- for manual POS (walk-in customer)
    customer_name TEXT,
    customer_phone TEXT,
    subtotal_paise INTEGER NOT NULL DEFAULT 0 CHECK (subtotal_paise >= 0),
    delivery_fee_paise INTEGER NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
    discount_paise INTEGER NOT NULL DEFAULT 0 CHECK (discount_paise >= 0),
    tax_paise INTEGER NOT NULL DEFAULT 0 CHECK (tax_paise >= 0),
    total_paise INTEGER NOT NULL DEFAULT 0 CHECK (total_paise >= 0),
    payment_method payment_method NOT NULL DEFAULT 'cod',
    payment_status payment_status NOT NULL DEFAULT 'pending',
    payment_reference TEXT, -- transaction ID for online
    -- For manual POS: staff who created the bill
    created_by UUID REFERENCES profiles(id),
    -- Voiding/correction
    is_void BOOLEAN NOT NULL DEFAULT false,
    voided_at TIMESTAMPTZ,
    voided_by UUID REFERENCES profiles(id),
    void_reason TEXT,
    original_bill_id UUID REFERENCES bills(id), -- for corrections
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Bill Items
CREATE TABLE bill_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    bill_id UUID NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE SET NULL,
    product_name TEXT NOT NULL,
    product_sku TEXT,
    product_type product_type NOT NULL,
    unit unit_type NOT NULL,
    price_basis_unit unit_type NOT NULL,
    price_paise INTEGER NOT NULL CHECK (price_paise >= 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    line_total_paise INTEGER NOT NULL CHECK (line_total_paise >= 0),
    -- For manual POS: whether price was overridden
    is_price_override BOOLEAN NOT NULL DEFAULT false,
    original_price_paise INTEGER, -- if overridden
    override_reason TEXT,
    override_by UUID REFERENCES profiles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Stock Movements (audit trail for inventory)
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    movement_type stock_movement_type NOT NULL,
    quantity_change INTEGER NOT NULL, -- positive for increase, negative for decrease
    quantity_before INTEGER NOT NULL CHECK (quantity_before >= 0),
    quantity_after INTEGER NOT NULL CHECK (quantity_after >= 0),
    unit_cost_paise INTEGER, -- for restock movements
    reference_type TEXT, -- 'order', 'bill', 'manual', 'adjustment'
    reference_id UUID, -- order_id, bill_id, etc.
    reason TEXT,
    created_by UUID REFERENCES profiles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Audit Logs (for sensitive admin actions)
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    actor_id UUID REFERENCES profiles(id),
    actor_role user_role,
    action audit_action NOT NULL,
    entity_type TEXT NOT NULL, -- 'product', 'order', 'settings', 'service_area', 'delivery_person', 'bill', 'stock'
    entity_id UUID,
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================
-- INDEXES
-- ============================================
CREATE INDEX idx_profiles_role ON profiles(role);
CREATE INDEX idx_profiles_phone ON profiles(phone);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(is_active, is_available);
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_service_areas_active ON service_areas(is_active);
CREATE INDEX idx_service_areas_pin ON service_areas(pin_code);
CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_delivery_person ON orders(delivery_person_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created ON orders(created_at DESC);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_delivery_assignments_person ON delivery_assignments(delivery_person_id);
CREATE INDEX idx_delivery_assignments_order ON delivery_assignments(order_id);
CREATE INDEX idx_bills_order ON bills(order_id);
CREATE INDEX idx_bills_customer ON bills(customer_id);
CREATE INDEX idx_bills_created ON bills(created_at DESC);
CREATE INDEX idx_bill_items_bill ON bill_items(bill_id);
CREATE INDEX idx_stock_movements_product ON stock_movements(product_id);
CREATE INDEX idx_stock_movements_created ON stock_movements(created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at DESC);

-- ============================================
-- TRIGGERS FOR updated_at
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_profiles_updated_at BEFORE UPDATE ON profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_categories_updated_at BEFORE UPDATE ON categories FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_products_updated_at BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_service_areas_updated_at BEFORE UPDATE ON service_areas FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_orders_updated_at BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_bills_updated_at BEFORE UPDATE ON bills FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();