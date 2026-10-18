-- Balaji Provision Store - Order Placement Transaction Function
-- Version 1.0 - Atomic order creation with stock validation

-- Place order with atomic transaction
CREATE OR REPLACE FUNCTION place_order_transaction(
    p_customer_id UUID,
    p_service_area_id UUID,
    p_delivery_address_line1 TEXT,
    p_delivery_address_line2 TEXT,
    p_delivery_landmark TEXT,
    p_delivery_pin_code TEXT,
    p_delivery_instructions TEXT,
    p_customer_name TEXT,
    p_customer_phone TEXT,
    p_payment_method payment_method,
    p_cart_items JSONB,
    p_idempotency_key TEXT DEFAULT NULL
)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_order_id UUID;
    v_order_number TEXT;
    v_subtotal_paise INTEGER := 0;
    v_delivery_fee_paise INTEGER := 0;
    v_total_paise INTEGER := 0;
    v_charge_threshold INTEGER;
    v_free_threshold INTEGER;
    v_delivery_fee INTEGER;
    v_item JSONB;
    v_product RECORD;
    v_base_qty INTEGER;
    v_line_total INTEGER;
    v_basis_in_base INTEGER;
BEGIN
    -- Get settings
    SELECT value->>0 INTO v_charge_threshold FROM settings WHERE key = 'delivery_charge_threshold_paise';
    SELECT value->>0 INTO v_free_threshold FROM settings WHERE key = 'free_delivery_threshold_paise';
    SELECT value->>0 INTO v_delivery_fee FROM settings WHERE key = 'delivery_fee_paise';
    
    v_charge_threshold := COALESCE(v_charge_threshold::INTEGER, 25000);
    v_free_threshold := COALESCE(v_free_threshold::INTEGER, 50000);
    v_delivery_fee := COALESCE(v_delivery_fee::INTEGER, 3000);

    -- Validate and calculate totals for each item
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_cart_items)
    LOOP
        -- Get product with row lock
        SELECT * INTO v_product 
        FROM products 
        WHERE id = (v_item->>'product_id')::UUID 
        AND is_active = true 
        AND is_available = true
        FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Product not available: %', v_item->>'product_id';
        END IF;

        v_base_qty := (v_item->>'quantity')::INTEGER;
        
        IF v_base_qty <= 0 THEN
            RAISE EXCEPTION 'Invalid quantity for product %', v_product.name;
        END IF;

        IF v_base_qty > v_product.stock_quantity THEN
            RAISE EXCEPTION 'Insufficient stock for %. Available: % %', v_product.name, v_product.stock_quantity, v_product.unit;
        END IF;

        -- Calculate line total
        IF v_product.product_type = 'weighted' THEN
            v_basis_in_base := CASE v_product.price_basis_unit 
                WHEN 'kg' THEN 1000 
                ELSE 1 
            END;
            v_line_total := (v_product.price_paise * v_base_qty) / v_basis_in_base;
        ELSE
            v_line_total := v_product.price_paise * v_base_qty;
        END IF;

        v_subtotal_paise := v_subtotal_paise + v_line_total;
    END LOOP;

    -- Calculate delivery fee (server-side authoritative)
    IF v_subtotal_paise >= v_free_threshold THEN
        v_delivery_fee_paise := 0;
    ELSIF v_subtotal_paise < v_charge_threshold THEN
        v_delivery_fee_paise := v_delivery_fee;
    ELSE
        v_delivery_fee_paise := 0;
    END IF;

    v_total_paise := v_subtotal_paise + v_delivery_fee_paise;

    -- Generate order number
    v_order_number := generate_order_number();

    -- Create order
    INSERT INTO orders (
        order_number,
        customer_id,
        service_area_id,
        delivery_address_line1,
        delivery_address_line2,
        delivery_landmark,
        delivery_pin_code,
        delivery_instructions,
        customer_name,
        customer_phone,
        payment_method,
        payment_status,
        subtotal_paise,
        delivery_fee_paise,
        total_paise,
        status,
        payment_intent_id
    ) VALUES (
        v_order_number,
        p_customer_id,
        p_service_area_id,
        p_delivery_address_line1,
        p_delivery_address_line2,
        p_delivery_landmark,
        p_delivery_pin_code,
        p_delivery_instructions,
        p_customer_name,
        p_customer_phone,
        p_payment_method,
        CASE WHEN p_payment_method = 'online' THEN 'pending' ELSE 'paid' END,
        v_subtotal_paise,
        v_delivery_fee_paise,
        v_total_paise,
        'pending',
        p_idempotency_key
    ) RETURNING id INTO v_order_id;

    -- Create order items and reserve stock
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_cart_items)
    LOOP
        SELECT * INTO v_product 
        FROM products 
        WHERE id = (v_item->>'product_id')::UUID 
        FOR UPDATE;

        v_base_qty := (v_item->>'quantity')::INTEGER;

        IF v_product.product_type = 'weighted' THEN
            v_basis_in_base := CASE v_product.price_basis_unit 
                WHEN 'kg' THEN 1000 
                ELSE 1 
            END;
            v_line_total := (v_product.price_paise * v_base_qty) / v_basis_in_base;
        ELSE
            v_line_total := v_product.price_paise * v_base_qty;
        END IF;

        -- Insert order item with snapshot
        INSERT INTO order_items (
            order_id,
            product_id,
            product_name,
            product_sku,
            product_type,
            unit,
            price_basis_unit,
            price_paise,
            quantity,
            line_total_paise
        ) VALUES (
            v_order_id,
            v_product.id,
            v_product.name,
            v_product.sku,
            v_product.product_type,
            v_product.unit,
            v_product.price_basis_unit,
            v_product.price_paise,
            v_base_qty,
            v_line_total
        );

        -- Reserve stock (decrement)
        UPDATE products 
        SET stock_quantity = stock_quantity - v_base_qty,
            updated_at = NOW()
        WHERE id = v_product.id;
    END LOOP;

    -- Create bill for online order
    INSERT INTO bills (
        bill_number,
        bill_type,
        order_id,
        customer_id,
        customer_name,
        customer_phone,
        subtotal_paise,
        delivery_fee_paise,
        total_paise,
        payment_method,
        payment_status,
        created_by
    ) VALUES (
        generate_bill_number(),
        'online_order',
        v_order_id,
        p_customer_id,
        p_customer_name,
        p_customer_phone,
        v_subtotal_paise,
        v_delivery_fee_paise,
        v_total_paise,
        p_payment_method,
        CASE WHEN p_payment_method = 'online' THEN 'pending' ELSE 'paid' END,
        p_customer_id
    );

    RETURN jsonb_build_object(
        'order_id', v_order_id,
        'order_number', v_order_number,
        'subtotal_paise', v_subtotal_paise,
        'delivery_fee_paise', v_delivery_fee_paise,
        'total_paise', v_total_paise,
        'status', 'pending',
        'payment_method', p_payment_method,
        'payment_status', CASE WHEN p_payment_method = 'online' THEN 'pending' ELSE 'paid' END
    );
END;
$$;

-- Grant execute to authenticated users
GRANT EXECUTE ON FUNCTION place_order_transaction TO authenticated;