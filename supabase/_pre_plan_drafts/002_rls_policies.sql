-- Balaji Provision Store - RLS Policies and Business Logic Functions
-- Version 1.0 - Row Level Security and server-side validation

-- ============================================
-- HELPER FUNCTIONS
-- ============================================

-- Get current user's role
CREATE OR REPLACE FUNCTION get_current_user_role()
RETURNS user_role AS $$
BEGIN
    RETURN COALESCE(
        (SELECT role FROM profiles WHERE id = auth.uid()),
        'customer'
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Check if current user is manager or admin
CREATE OR REPLACE FUNCTION is_manager_or_admin()
RETURNS BOOLEAN AS $$
BEGIN
    RETURN get_current_user_role() IN ('manager', 'admin');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Check if current user is delivery person
CREATE OR REPLACE FUNCTION is_delivery_person()
RETURNS BOOLEAN AS $$
BEGIN
    RETURN get_current_user_role() = 'delivery_person';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Check if current user is the order's customer
CREATE OR REPLACE FUNCTION is_order_customer(order_uuid UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM orders WHERE id = order_uuid AND customer_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Check if current user is assigned delivery person for order
CREATE OR REPLACE FUNCTION is_assigned_delivery_person(order_uuid UUID)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM orders WHERE id = order_uuid AND delivery_person_id = auth.uid()
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Get setting value (with caching via function)
CREATE OR REPLACE FUNCTION get_setting(setting_key TEXT, OUT value JSONB)
LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    SELECT value INTO value FROM settings WHERE key = setting_key;
    IF value IS NULL THEN
        RAISE EXCEPTION 'Setting not found: %', setting_key;
    END IF;
END;
$$;

-- Get delivery charge threshold in paise
CREATE OR REPLACE FUNCTION get_delivery_charge_threshold_paise()
RETURNS INTEGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v JSONB;
BEGIN
    SELECT value INTO v FROM settings WHERE key = 'delivery_charge_threshold_paise';
    RETURN COALESCE((v->>0)::INTEGER, 25000);
END;
$$;

-- Get delivery fee in paise
CREATE OR REPLACE FUNCTION get_delivery_fee_paise()
RETURNS INTEGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v JSONB;
BEGIN
    SELECT value INTO v FROM settings WHERE key = 'delivery_fee_paise';
    RETURN COALESCE((v->>0)::INTEGER, 3000);
END;
$$;

-- Get free delivery threshold in paise
CREATE OR REPLACE FUNCTION get_free_delivery_threshold_paise()
RETURNS INTEGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v JSONB;
BEGIN
    SELECT value INTO v FROM settings WHERE key = 'free_delivery_threshold_paise';
    RETURN COALESCE((v->>0)::INTEGER, 50000);
END;
$$;

-- Calculate delivery fee based on subtotal (server-side authoritative)
CREATE OR REPLACE FUNCTION calculate_delivery_fee(subtotal_paise INTEGER)
RETURNS INTEGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    charge_threshold INTEGER;
    free_threshold INTEGER;
    fee INTEGER;
BEGIN
    charge_threshold := get_delivery_charge_threshold_paise();
    free_threshold := get_free_delivery_threshold_paise();
    fee := get_delivery_fee_paise();

    -- Free delivery threshold takes precedence
    IF subtotal_paise >= free_threshold THEN
        RETURN 0;
    END IF;

    -- Below delivery charge threshold: apply fee
    IF subtotal_paise < charge_threshold THEN
        RETURN fee;
    END IF;

    -- Between charge threshold and free threshold: no fee (or could be different logic)
    -- Current requirement: fee only applies below charge threshold
    RETURN 0;
END;
$$;

-- Validate service area for a PIN code
CREATE OR REPLACE FUNCTION validate_service_area(pin_code TEXT)
RETURNS BOOLEAN LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM service_areas
        WHERE pin_code = validate_service_area.pin_code
        AND is_active = true
    );
END;
$$;

-- Generate next order number
CREATE OR REPLACE FUNCTION generate_order_number()
RETURNS TEXT LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    prefix TEXT := 'BAL';
    date_part TEXT := TO_CHAR(NOW(), 'YYYYMMDD');
    seq INTEGER;
    order_num TEXT;
BEGIN
    LOOP
        SELECT COALESCE(MAX(
            (split_part(order_number, '-', 3))::INTEGER
        ), 0) + 1 INTO seq
        FROM orders
        WHERE order_number LIKE prefix || '-' || date_part || '-%';

        order_num := prefix || '-' || date_part || '-' || LPAD(seq::TEXT, 4, '0');

        IF NOT EXISTS (SELECT 1 FROM orders WHERE order_number = order_num) THEN
            RETURN order_num;
        END IF;
    END LOOP;
END;
$$;

-- Generate next bill number
CREATE OR REPLACE FUNCTION generate_bill_number()
RETURNS TEXT LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    prefix TEXT := 'BILL';
    date_part TEXT := TO_CHAR(NOW(), 'YYYYMMDD');
    seq INTEGER;
    bill_num TEXT;
BEGIN
    LOOP
        SELECT COALESCE(MAX(
            (split_part(bill_number, '-', 3))::INTEGER
        ), 0) + 1 INTO seq
        FROM bills
        WHERE bill_number LIKE prefix || '-' || date_part || '-%';

        bill_num := prefix || '-' || date_part || '-' || LPAD(seq::TEXT, 4, '0');

        IF NOT EXISTS (SELECT 1 FROM bills WHERE bill_number = bill_num) THEN
            RETURN bill_num;
        END IF;
    END LOOP;
END;
$$;

-- Convert quantity to base units (grams for weighted, pieces for packed)
CREATE OR REPLACE FUNCTION to_base_quantity(product_type product_type, unit unit_type, quantity NUMERIC)
RETURNS INTEGER LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    IF product_type = 'weighted' THEN
        CASE unit
            WHEN 'g' THEN RETURN quantity::INTEGER;
            WHEN 'kg' THEN RETURN (quantity * 1000)::INTEGER;
            ELSE RAISE EXCEPTION 'Invalid unit for weighted product: %', unit;
        END CASE;
    ELSE
        -- packed: only integer quantities allowed
        IF quantity != FLOOR(quantity) OR quantity <= 0 THEN
            RAISE EXCEPTION 'Packed products require positive integer quantity';
        END IF;
        RETURN quantity::INTEGER;
    END IF;
END;
$$;

-- Convert base quantity to display quantity
CREATE OR REPLACE FUNCTION from_base_quantity(product_type product_type, unit unit_type, base_quantity INTEGER)
RETURNS NUMERIC LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    IF product_type = 'weighted' THEN
        CASE unit
            WHEN 'g' THEN RETURN base_quantity::NUMERIC;
            WHEN 'kg' THEN RETURN base_quantity::NUMERIC / 1000;
            ELSE RAISE EXCEPTION 'Invalid unit for weighted product: %', unit;
        END CASE;
    ELSE
        RETURN base_quantity::NUMERIC;
    END IF;
END;
$$;

-- Calculate line total from price per basis unit and quantity
CREATE OR REPLACE FUNCTION calculate_line_total(
    price_paise INTEGER,
    price_basis_unit unit_type,
    product_type product_type,
    unit unit_type,
    quantity INTEGER -- base quantity
)
RETURNS INTEGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    basis_in_base_units INTEGER;
BEGIN
    -- Convert price basis unit to base units
    IF product_type = 'weighted' THEN
        CASE price_basis_unit
            WHEN 'g' THEN basis_in_base_units := 1;
            WHEN 'kg' THEN basis_in_base_units := 1000;
            ELSE RAISE EXCEPTION 'Invalid price basis unit for weighted: %', price_basis_unit;
        END CASE;
        RETURN (price_paise * quantity) / basis_in_base_units;
    ELSE
        -- packed: price per pack/piece, quantity is in pieces
        RETURN price_paise * quantity;
    END IF;
END;
$$;

-- ============================================
-- TRIGGER FUNCTIONS
-- ============================================

-- Auto-create profile on user signup
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    INSERT INTO profiles (id, role, email, phone, full_name)
    VALUES (NEW.id, 'customer', NEW.email, NEW.phone, NEW.raw_user_meta_data->>'full_name')
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;

-- Audit log trigger
CREATE OR REPLACE FUNCTION audit_log_trigger()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_action audit_action;
    v_old JSONB;
    v_new JSONB;
BEGIN
    -- Determine action
    IF TG_OP = 'INSERT' THEN
        v_action := 'create';
        v_old := NULL;
        v_new := to_jsonb(NEW);
    ELSIF TG_OP = 'UPDATE' THEN
        v_action := 'update';
        v_old := to_jsonb(OLD);
        v_new := to_jsonb(NEW);
    ELSIF TG_OP = 'DELETE' THEN
        v_action := 'delete';
        v_old := to_jsonb(OLD);
        v_new := NULL;
    END IF;

    -- Skip certain tables/columns from audit
    IF TG_TABLE_NAME IN ('audit_logs', 'stock_movements') THEN
        RETURN NULL;
    END IF;

    INSERT INTO audit_logs (actor_id, actor_role, action, entity_type, entity_id, old_values, new_values)
    VALUES (auth.uid(), get_current_user_role(), v_action, TG_TABLE_NAME, 
            COALESCE(NEW.id, OLD.id), v_old, v_new);

    RETURN NULL;
END;
$$;

-- Stock movement trigger for orders
CREATE OR REPLACE FUNCTION handle_order_stock_movement()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_item RECORD;
    v_qty_before INTEGER;
    v_qty_after INTEGER;
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status IN ('confirmed', 'preparing') THEN
        -- Reserve stock when order confirmed
        FOR v_item IN SELECT * FROM order_items WHERE order_id = NEW.id LOOP
            SELECT stock_quantity INTO v_qty_before FROM products WHERE id = v_item.product_id;
            v_qty_after := v_qty_before - v_item.quantity;

            IF v_qty_after < 0 THEN
                RAISE EXCEPTION 'Insufficient stock for product %', v_item.product_name;
            END IF;

            UPDATE products SET stock_quantity = v_qty_after WHERE id = v_item.product_id;

            INSERT INTO stock_movements (product_id, movement_type, quantity_change, quantity_before, quantity_after, reference_type, reference_id, reason, created_by)
            VALUES (v_item.product_id, 'sale', -v_item.quantity, v_qty_before, v_qty_after, 'order', NEW.id, 'Order confirmed', NEW.customer_id);
        END LOOP;
    ELSIF TG_OP = 'UPDATE' AND OLD.status IN ('confirmed', 'preparing') AND NEW.status IN ('cancelled', 'returned') THEN
        -- Restore stock on cancellation/return
        FOR v_item IN SELECT * FROM order_items WHERE order_id = NEW.id LOOP
            SELECT stock_quantity INTO v_qty_before FROM products WHERE id = v_item.product_id;
            v_qty_after := v_qty_before + v_item.quantity;

            UPDATE products SET stock_quantity = v_qty_after WHERE id = v_item.product_id;

            INSERT INTO stock_movements (product_id, movement_type, quantity_change, quantity_before, quantity_after, reference_type, reference_id, reason, created_by)
            VALUES (v_item.product_id, 'return', v_item.quantity, v_qty_before, v_qty_after, 'order', NEW.id, 'Order ' || NEW.status, NEW.customer_id);
        END LOOP;
    END IF;
    RETURN NEW;
END;
$$;

-- Stock movement for manual bills
CREATE OR REPLACE FUNCTION handle_bill_stock_movement()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_item RECORD;
    v_qty_before INTEGER;
    v_qty_after INTEGER;
BEGIN
    IF TG_OP = 'INSERT' AND NEW.bill_type = 'manual_pos' AND NOT NEW.is_void THEN
        FOR v_item IN SELECT * FROM bill_items WHERE bill_id = NEW.id LOOP
            SELECT stock_quantity INTO v_qty_before FROM products WHERE id = v_item.product_id;
            v_qty_after := v_qty_before - v_item.quantity;

            IF v_qty_after < 0 THEN
                RAISE EXCEPTION 'Insufficient stock for product %', v_item.product_name;
            END IF;

            UPDATE products SET stock_quantity = v_qty_after WHERE id = v_item.product_id;

            INSERT INTO stock_movements (product_id, movement_type, quantity_change, quantity_before, quantity_after, reference_type, reference_id, reason, created_by)
            VALUES (v_item.product_id, 'sale', -v_item.quantity, v_qty_before, v_qty_after, 'bill', NEW.id, 'Manual POS sale', NEW.created_by);
        END LOOP;
    ELSIF TG_OP = 'UPDATE' AND OLD.is_void = false AND NEW.is_void = true THEN
        -- Restore stock on void
        FOR v_item IN SELECT * FROM bill_items WHERE bill_id = NEW.id LOOP
            SELECT stock_quantity INTO v_qty_before FROM products WHERE id = v_item.product_id;
            v_qty_after := v_qty_before + v_item.quantity;

            UPDATE products SET stock_quantity = v_qty_after WHERE id = v_item.product_id;

            INSERT INTO stock_movements (product_id, movement_type, quantity_change, quantity_before, quantity_after, reference_type, reference_id, reason, created_by)
            VALUES (v_item.product_id, 'correction', v_item.quantity, v_qty_before, v_qty_after, 'bill', NEW.id, 'Bill voided: ' || NEW.void_reason, NEW.voided_by);
        END LOOP;
    END IF;
    RETURN NEW;
END;
$$;

-- ============================================
-- ENABLE RLS
-- ============================================
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_areas ENABLE ROW LEVEL SECURITY;
ALTER TABLE settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE delivery_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE bills ENABLE ROW LEVEL SECURITY;
ALTER TABLE bill_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

-- ============================================
-- RLS POLICIES
-- ============================================

-- Profiles: users see own, managers/admins see all
CREATE POLICY "profiles_select_own" ON profiles FOR SELECT USING (id = auth.uid() OR is_manager_or_admin());
CREATE POLICY "profiles_update_own" ON profiles FOR UPDATE USING (id = auth.uid() OR is_manager_or_admin());
CREATE POLICY "profiles_insert_admin" ON profiles FOR INSERT WITH CHECK (is_manager_or_admin());

-- Categories: all authenticated can read active, managers manage
CREATE POLICY "categories_select_active" ON categories FOR SELECT USING (is_active = true OR is_manager_or_admin());
CREATE POLICY "categories_manage" ON categories FOR ALL USING (is_manager_or_admin()) WITH CHECK (is_manager_or_admin());

-- Products: customers see active+available, managers manage all
CREATE POLICY "products_select_customer" ON products FOR SELECT USING (is_active = true AND is_available = true);
CREATE POLICY "products_select_manager" ON products FOR SELECT USING (is_manager_or_admin());
CREATE POLICY "products_manage" ON products FOR ALL USING (is_manager_or_admin()) WITH CHECK (is_manager_or_admin());

-- Service Areas: customers see active for validation, managers manage
CREATE POLICY "service_areas_select_customer" ON service_areas FOR SELECT USING (is_active = true);
CREATE POLICY "service_areas_select_manager" ON service_areas FOR SELECT USING (is_manager_or_admin());
CREATE POLICY "service_areas_manage" ON service_areas FOR ALL USING (is_manager_or_admin()) WITH CHECK (is_manager_or_admin());

-- Settings: only managers/admins can read/write
CREATE POLICY "settings_manager" ON settings FOR ALL USING (is_manager_or_admin()) WITH CHECK (is_manager_or_admin());

-- Orders: customers see own, delivery persons see assigned, managers see all
CREATE POLICY "orders_select_customer" ON orders FOR SELECT USING (customer_id = auth.uid());
CREATE POLICY "orders_select_delivery" ON orders FOR SELECT USING (delivery_person_id = auth.uid() AND is_delivery_person());
CREATE POLICY "orders_select_manager" ON orders FOR SELECT USING (is_manager_or_admin());
CREATE POLICY "orders_insert_customer" ON orders FOR INSERT WITH CHECK (customer_id = auth.uid());
CREATE POLICY "orders_update_manager" ON orders FOR UPDATE USING (is_manager_or_admin());
CREATE POLICY "orders_update_delivery" ON orders FOR UPDATE USING (delivery_person_id = auth.uid() AND is_delivery_person() AND status IN ('ready_for_pickup', 'out_for_delivery'));

-- Order Items: follow order permissions
CREATE POLICY "order_items_select" ON order_items FOR SELECT USING (
    EXISTS (SELECT 1 FROM orders WHERE id = order_id AND (customer_id = auth.uid() OR delivery_person_id = auth.uid() OR is_manager_or_admin()))
);
CREATE POLICY "order_items_insert" ON order_items FOR INSERT WITH CHECK (
    EXISTS (SELECT 1 FROM orders WHERE id = order_id AND customer_id = auth.uid())
);

-- Delivery Assignments: managers manage, delivery persons see own
CREATE POLICY "delivery_assignments_select_manager" ON delivery_assignments FOR SELECT USING (is_manager_or_admin());
CREATE POLICY "delivery_assignments_select_delivery" ON delivery_assignments FOR SELECT USING (delivery_person_id = auth.uid() AND is_delivery_person());
CREATE POLICY "delivery_assignments_manage" ON delivery_assignments FOR ALL USING (is_manager_or_admin()) WITH CHECK (is_manager_or_admin());

-- Bills: customers see own (online), managers see all, delivery persons see assigned order bills
CREATE POLICY "bills_select_customer" ON bills FOR SELECT USING (customer_id = auth.uid() OR is_manager_or_admin());
CREATE POLICY "bills_select_delivery" ON bills FOR SELECT USING (
    EXISTS (SELECT 1 FROM orders WHERE id = bills.order_id AND delivery_person_id = auth.uid() AND is_delivery_person())
);
CREATE POLICY "bills_insert_manager" ON bills FOR INSERT WITH CHECK (is_manager_or_admin());
CREATE POLICY "bills_update_manager" ON bills FOR UPDATE USING (is_manager_or_admin());

-- Bill Items: follow bill permissions
CREATE POLICY "bill_items_select" ON bill_items FOR SELECT USING (
    EXISTS (SELECT 1 FROM bills WHERE id = bill_id AND (customer_id = auth.uid() OR is_manager_or_admin()))
);
CREATE POLICY "bill_items_insert" ON bill_items FOR INSERT WITH CHECK (
    EXISTS (SELECT 1 FROM bills WHERE id = bill_id AND is_manager_or_admin())
);

-- Stock Movements: managers only
CREATE POLICY "stock_movements_manager" ON stock_movements FOR ALL USING (is_manager_or_admin()) WITH CHECK (is_manager_or_admin());

-- Audit Logs: managers only
CREATE POLICY "audit_logs_manager" ON audit_logs FOR SELECT USING (is_manager_or_admin());

-- ============================================
-- ATTACH TRIGGERS
-- ============================================

-- Auto-create profile on signup
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION handle_new_user();

-- Audit triggers on sensitive tables
CREATE TRIGGER audit_products AFTER INSERT OR UPDATE OR DELETE ON products FOR EACH ROW EXECUTE FUNCTION audit_log_trigger();
CREATE TRIGGER audit_orders AFTER INSERT OR UPDATE OR DELETE ON orders FOR EACH ROW EXECUTE FUNCTION audit_log_trigger();
CREATE TRIGGER audit_settings AFTER INSERT OR UPDATE OR DELETE ON settings FOR EACH ROW EXECUTE FUNCTION audit_log_trigger();
CREATE TRIGGER audit_service_areas AFTER INSERT OR UPDATE OR DELETE ON service_areas FOR EACH ROW EXECUTE FUNCTION audit_log_trigger();
CREATE TRIGGER audit_delivery_assignments AFTER INSERT OR UPDATE OR DELETE ON delivery_assignments FOR EACH ROW EXECUTE FUNCTION audit_log_trigger();
CREATE TRIGGER audit_bills AFTER INSERT OR UPDATE OR DELETE ON bills FOR EACH ROW EXECUTE FUNCTION audit_log_trigger();
CREATE TRIGGER audit_profiles AFTER INSERT OR UPDATE OR DELETE ON profiles FOR EACH ROW EXECUTE FUNCTION audit_log_trigger();

-- Stock movement triggers
CREATE TRIGGER order_stock_movement AFTER INSERT OR UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION handle_order_stock_movement();
CREATE TRIGGER bill_stock_movement AFTER INSERT OR UPDATE ON bills FOR EACH ROW EXECUTE FUNCTION handle_bill_stock_movement();