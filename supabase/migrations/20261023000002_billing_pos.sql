-- M9: Billing & POS (IMPLEMENTATION_PLAN.md §4.8, §9, §12)
-- Bill issuance for online orders, POS sale, snapshots, gapless FY numbering,
-- authorised bill access, voiding, and RLS tightening so bill writes only go
-- through these SECURITY DEFINER functions.

-- ---------------------------------------------------------------------------
-- 0) Schema hardening
-- ---------------------------------------------------------------------------

ALTER TABLE public.bill_items
  ADD COLUMN IF NOT EXISTS line_no integer NOT NULL DEFAULT 0;

-- One bill per source record. Prevents concurrent issuers creating duplicates.
CREATE UNIQUE INDEX IF NOT EXISTS uq_bills_order_id
  ON public.bills (order_id)
  WHERE source = 'ORDER' AND order_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bills_pos_sale_id
  ON public.bills (pos_sale_id)
  WHERE source = 'POS' AND pos_sale_id IS NOT NULL;

-- Bill writes must go through issue_bill_for_order / pos_complete_sale, both of
-- which are SECURITY DEFINER and validate role, pricing and stock. Direct table
-- writes previously allowed staff to forge bills.
DROP POLICY IF EXISTS "bills_write_staff" ON public.bills;
DROP POLICY IF EXISTS "bill_items_write_staff" ON public.bill_items;
DROP POLICY IF EXISTS "pos_sales_write_staff" ON public.pos_sales;
DROP POLICY IF EXISTS "bill_counters_manager" ON public.bill_counters;

CREATE POLICY "bill_counters_read_manager" ON public.bill_counters
  FOR SELECT TO authenticated
  USING (public.current_role() = 'manager'::public.user_role);

-- ---------------------------------------------------------------------------
-- 1) Financial-year helper + gapless bill number
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.bill_financial_year(p_at timestamptz DEFAULT now())
RETURNS text
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_timezone text;
  v_local timestamp;
  v_year integer;
  v_month integer;
  v_start_year integer;
BEGIN
  SELECT COALESCE(timezone, 'Asia/Kolkata') INTO v_timezone
  FROM public.store_settings;
  v_timezone := COALESCE(v_timezone, 'Asia/Kolkata');

  v_local := p_at AT TIME ZONE v_timezone;
  v_year := EXTRACT(YEAR FROM v_local)::integer;
  v_month := EXTRACT(MONTH FROM v_local)::integer;
  v_start_year := CASE WHEN v_month >= 4 THEN v_year ELSE v_year - 1 END;

  RETURN v_start_year::text || '-' || lpad(((v_start_year + 1) % 100)::text, 2, '0');
END;
$$;

CREATE OR REPLACE FUNCTION public.next_bill_number(p_at timestamptz DEFAULT now())
RETURNS text
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_fy text;
  v_no bigint;
BEGIN
  v_fy := public.bill_financial_year(p_at);

  -- Row-locked upsert: concurrent transactions serialize here, and rolled-back
  -- numbers are reused so committed numbers stay gapless.
  INSERT INTO public.bill_counters (fy, last_no)
  VALUES (v_fy, 1)
  ON CONFLICT (fy) DO UPDATE
    SET last_no = bill_counters.last_no + 1
  RETURNING last_no INTO v_no;

  RETURN 'BPS/' || v_fy || '/' || lpad(v_no::text, 6, '0');
END;
$$;

REVOKE ALL ON FUNCTION public.bill_financial_year(timestamptz) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.next_bill_number(timestamptz) FROM PUBLIC, anon, authenticated;

-- ---------------------------------------------------------------------------
-- 2) Authorised bill read model (immutable bill snapshots)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.get_bill(p_bill_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_role public.user_role := public.current_role();
  v_bill public.bills%ROWTYPE;
  v_order_public_number text;
  v_order_owner uuid;
  v_items jsonb;
  v_settings public.store_settings%ROWTYPE;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  SELECT * INTO v_bill FROM public.bills WHERE id = p_bill_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Bill not found');
  END IF;

  IF v_bill.source = 'ORDER' AND v_bill.order_id IS NOT NULL THEN
    SELECT o.public_order_number, o.customer_id
    INTO v_order_public_number, v_order_owner
    FROM public.orders o
    WHERE o.id = v_bill.order_id;
  END IF;

  IF v_role NOT IN ('staff', 'manager')
     AND NOT (v_role = 'customer' AND v_order_owner = v_user_id) THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'You cannot view this bill');
  END IF;

  SELECT * INTO v_settings FROM public.store_settings;

  SELECT COALESCE(
           jsonb_agg(
             jsonb_build_object(
               'id', bi.id,
               'line_no', bi.line_no,
               'product_id', bi.product_id,
               'sku', bi.sku,
               'name', bi.name,
               'quantity_type', bi.quantity_type::text,
               'qty_base', bi.qty_base,
               'unit_label', bi.unit_label,
               'display_qty',
                 CASE
                   WHEN upper(bi.quantity_type::text) = 'WEIGHTED' AND bi.price_basis_qty = 1000
                     THEN to_char(bi.qty_base / 1000.0, 'FM999999990.000') || ' kg'
                   WHEN upper(bi.quantity_type::text) = 'WEIGHTED'
                     THEN bi.qty_base::text || ' g'
                   ELSE bi.qty_base::text || ' ' || bi.unit_label
                 END,
               'price_basis_qty', bi.price_basis_qty,
               'price_basis_label',
                 CASE
                   WHEN upper(bi.quantity_type::text) = 'WEIGHTED' AND bi.price_basis_qty = 1000
                     THEN 'per kg'
                   WHEN upper(bi.quantity_type::text) = 'WEIGHTED'
                     THEN 'per ' || bi.price_basis_qty::text || ' g'
                   ELSE 'per ' || bi.unit_label
                 END,
               'unit_price_paise', bi.unit_price_paise,
               'line_total_paise', bi.line_total_paise
             )
             ORDER BY bi.line_no, bi.id
           ),
           '[]'::jsonb
         )
  INTO v_items
  FROM public.bill_items bi
  WHERE bi.bill_id = p_bill_id;

  RETURN jsonb_build_object(
    'bill', jsonb_build_object(
      'id', v_bill.id,
      'bill_number', v_bill.bill_number,
      'source', v_bill.source::text,
      'status', v_bill.status::text,
      'order_id', v_bill.order_id,
      'order_public_number', v_order_public_number,
      'pos_sale_id', v_bill.pos_sale_id,
      'issued_at', v_bill.issued_at,
      'customer_name', v_bill.customer_name,
      'customer_phone', v_bill.customer_phone,
      'subtotal_paise', v_bill.subtotal_paise,
      'discount_paise', v_bill.discount_paise,
      'delivery_fee_paise', v_bill.delivery_fee_paise,
      'tax_paise', v_bill.tax_paise,
      'grand_total_paise', v_bill.grand_total_paise,
      'payment_method', v_bill.payment_method::text,
      'payment_status', v_bill.payment_status::text,
      'voided_at', v_bill.voided_at,
      'void_reason', CASE WHEN v_role IN ('staff', 'manager') THEN v_bill.void_reason ELSE NULL END
    ),
    'items', v_items,
    'store', jsonb_build_object(
      'name', 'Balaji Provision Store',
      'phone', COALESCE(v_settings.contact_phone, ''),
      'timezone', COALESCE(v_settings.timezone, 'Asia/Kolkata')
    )
  );
END;
$$;

REVOKE ALL ON FUNCTION public.get_bill(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_bill(uuid) TO authenticated;

-- ---------------------------------------------------------------------------
-- 3) Issue bill for an online order (staff/manager, idempotent)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.issue_bill_for_order(p_order_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_actor uuid := auth.uid();
  v_role public.user_role := public.current_role();
  v_order public.orders%ROWTYPE;
  v_bill_id uuid;
  v_bill_number text;
  v_item public.order_items%ROWTYPE;
  v_line_no integer := 0;
  v_active_items integer := 0;
  v_customer_name text;
  v_customer_phone text;
BEGIN
  IF v_actor IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;
  IF v_role NOT IN ('staff', 'manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  PERFORM pg_advisory_xact_lock(hashtextextended('issue_bill_for_order:' || p_order_id::text, 0));

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  SELECT id INTO v_bill_id FROM public.bills
  WHERE source = 'ORDER' AND order_id = p_order_id;

  IF v_bill_id IS NOT NULL THEN
    RETURN public.get_bill(v_bill_id);
  END IF;

  IF v_order.status IN ('cancelled', 'rejected') THEN
    RETURN jsonb_build_object('error', 'INVALID_STATUS',
      'message', 'A bill cannot be issued for a cancelled or rejected order');
  END IF;

  SELECT count(*) INTO v_active_items
  FROM public.order_items
  WHERE order_id = p_order_id AND removed_at IS NULL;

  IF v_active_items = 0 THEN
    RETURN jsonb_build_object('error', 'INVALID_STATUS',
      'message', 'This order has no billable items');
  END IF;

  v_customer_name := NULLIF(btrim(COALESCE(v_order.address_snapshot->>'recipient_name', '')), '');
  v_customer_phone := NULLIF(btrim(COALESCE(v_order.address_snapshot->>'phone', '')), '');
  v_bill_number := public.next_bill_number(now());

  INSERT INTO public.bills (
    bill_number, source, order_id, status, issued_by,
    customer_name, customer_phone,
    subtotal_paise, discount_paise, delivery_fee_paise, tax_paise,
    grand_total_paise, payment_method, payment_status
  ) VALUES (
    v_bill_number, 'ORDER', p_order_id, 'ISSUED', v_actor,
    v_customer_name, v_customer_phone,
    v_order.item_subtotal_paise, v_order.discount_paise, v_order.delivery_fee_paise,
    v_order.tax_paise, v_order.grand_total_paise,
    v_order.payment_method, v_order.payment_status
  ) RETURNING id INTO v_bill_id;

  FOR v_item IN
    SELECT * FROM public.order_items
    WHERE order_id = p_order_id AND removed_at IS NULL
    ORDER BY id
  LOOP
    v_line_no := v_line_no + 1;
    INSERT INTO public.bill_items (
      bill_id, line_no, product_id, sku, name, quantity_type, qty_base,
      unit_label, price_basis_qty, unit_price_paise, line_total_paise
    ) VALUES (
      v_bill_id, v_line_no, v_item.product_id, v_item.sku_snapshot,
      v_item.name_snapshot, v_item.quantity_type_snapshot, v_item.qty_base,
      v_item.unit_label_snapshot, v_item.price_basis_qty_snapshot,
      v_item.unit_price_paise, v_item.line_total_paise
    );
  END LOOP;

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, after, request_id)
  VALUES (
    v_actor, 'ISSUE_BILL', 'bills', v_bill_id::text,
    jsonb_build_object(
      'bill_number', v_bill_number,
      'order_id', p_order_id,
      'grand_total_paise', v_order.grand_total_paise
    ),
    current_setting('request.id', true)::text
  );

  RETURN public.get_bill(v_bill_id);
END;
$$;

REVOKE ALL ON FUNCTION public.issue_bill_for_order(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.issue_bill_for_order(uuid) TO authenticated;

-- ---------------------------------------------------------------------------
-- 4) POS search + server-side pricing (no delivery fee for in-store sales)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.pos_search_products(p_query text DEFAULT '')
RETURNS TABLE (
  id uuid,
  sku text,
  name text,
  quantity_type public.quantity_type,
  price_paise bigint,
  price_basis_qty bigint,
  unit_label text,
  step_qty bigint,
  min_qty bigint,
  max_qty bigint,
  stock_qty bigint,
  available boolean
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_role public.user_role := public.current_role();
  v_term text := '%' || btrim(COALESCE(p_query, '')) || '%';
BEGIN
  IF v_role NOT IN ('staff', 'manager') THEN
    RAISE EXCEPTION 'FORBIDDEN: staff or manager required' USING ERRCODE = '42501';
  END IF;

  RETURN QUERY
  SELECT p.id, p.sku, p.name, p.quantity_type, p.price_paise,
         p.price_basis_qty, p.unit_label, p.step_qty, p.min_qty, p.max_qty,
         p.stock_qty, p.available
  FROM public.products p
  WHERE p.active
    AND (btrim(COALESCE(p_query, '')) = ''
         OR p.name ILIKE v_term
         OR p.sku ILIKE v_term)
  ORDER BY p.name
  LIMIT 50;
END;
$$;

REVOKE ALL ON FUNCTION public.pos_search_products(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.pos_search_products(text) TO authenticated;

CREATE OR REPLACE FUNCTION public.pos_price_items(p_items jsonb)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_item jsonb;
  v_line_no bigint;
  v_product public.products%ROWTYPE;
  v_qty_text text;
  v_qty_base bigint;
  v_unit_price bigint;
  v_line_total bigint;
  v_display_qty text;
  v_price_basis_label text;
  v_lines jsonb := '[]'::jsonb;
  v_subtotal bigint := 0;
  v_tax bigint := 0;
  v_grand bigint := 0;
  v_settings public.store_settings%ROWTYPE;
BEGIN
  IF p_items IS NULL OR jsonb_typeof(p_items) <> 'array' OR jsonb_array_length(p_items) = 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Add at least one item to the sale');
  END IF;

  IF (SELECT count(DISTINCT value->>'product_id') FROM jsonb_array_elements(p_items))
     <> jsonb_array_length(p_items) THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Duplicate products in the sale');
  END IF;

  SELECT * INTO v_settings FROM public.store_settings;

  FOR v_item, v_line_no IN
    SELECT value, ordinality
    FROM jsonb_array_elements(p_items) WITH ORDINALITY AS e(value, ordinality)
  LOOP
    IF COALESCE(v_item->>'product_id', '')
       !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
      RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Invalid product identifier');
    END IF;

    SELECT * INTO v_product FROM public.products WHERE id = (v_item->>'product_id')::uuid;
    IF NOT FOUND THEN
      RETURN jsonb_build_object('error', 'PRODUCT_UNAVAILABLE', 'message', 'Product not found');
    END IF;
    IF NOT v_product.active OR NOT v_product.available THEN
      RETURN jsonb_build_object('error', 'PRODUCT_UNAVAILABLE',
        'message', v_product.name || ' is not available');
    END IF;

    v_qty_text := COALESCE(v_item->>'qty_base', '');
    IF v_qty_text !~ '^\d+$' THEN
      RETURN jsonb_build_object('error', 'QTY_INVALID',
        'message', 'Quantity must be a positive whole number for ' || v_product.name);
    END IF;
    v_qty_base := v_qty_text::bigint;

    IF v_qty_base < v_product.min_qty
       OR v_qty_base > COALESCE(v_product.max_qty, v_qty_base)
       OR (upper(v_product.quantity_type::text) = 'WEIGHTED'
           AND v_qty_base % v_product.step_qty <> 0) THEN
      RETURN jsonb_build_object('error', 'QTY_INVALID',
        'message', 'Invalid quantity for ' || v_product.name);
    END IF;

    IF v_product.stock_qty < v_qty_base THEN
      RETURN jsonb_build_object('error', 'INSUFFICIENT_STOCK',
        'message', v_product.name || ' has only ' || v_product.stock_qty || ' available');
    END IF;

    v_unit_price := v_product.price_paise;
    v_line_total := (2 * v_unit_price * v_qty_base + v_product.price_basis_qty)
                      / (2 * v_product.price_basis_qty);
    v_subtotal := v_subtotal + v_line_total;

    IF upper(v_product.quantity_type::text) = 'WEIGHTED' THEN
      v_display_qty := CASE
        WHEN v_product.price_basis_qty = 1000
          THEN to_char(v_qty_base / 1000.0, 'FM999999990.000') || ' kg'
        ELSE v_qty_base::text || ' g'
      END;
      v_price_basis_label := CASE
        WHEN v_product.price_basis_qty = 1000 THEN 'per kg'
        ELSE 'per ' || v_product.price_basis_qty::text || ' g'
      END;
    ELSE
      v_display_qty := v_qty_base::text || ' ' || v_product.unit_label;
      v_price_basis_label := 'per ' || v_product.unit_label;
    END IF;

    v_lines := v_lines || jsonb_build_object(
      'line_no', v_line_no,
      'product_id', v_product.id,
      'sku', v_product.sku,
      'name', v_product.name,
      'quantity_type', v_product.quantity_type::text,
      'unit_label', v_product.unit_label,
      'price_basis_qty', v_product.price_basis_qty,
      'price_basis_label', v_price_basis_label,
      'qty_base', v_qty_base,
      'display_qty', v_display_qty,
      'unit_price_paise', v_unit_price,
      'line_total_paise', v_line_total,
      'stock_qty', v_product.stock_qty
    );
  END LOOP;

  IF v_settings.tax_mode = 'exclusive'::public.tax_mode AND v_settings.tax_rate_bps > 0 THEN
    v_tax := (v_subtotal * v_settings.tax_rate_bps) / 10000;
  END IF;
  v_grand := v_subtotal + v_tax;

  RETURN jsonb_build_object(
    'lines', v_lines,
    'item_subtotal_paise', v_subtotal,
    'discount_paise', 0,
    'delivery_fee_paise', 0,
    'tax_paise', v_tax,
    'grand_total_paise', v_grand
  );
END;
$$;

REVOKE ALL ON FUNCTION public.pos_price_items(jsonb) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.pos_quote(p_items jsonb)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF public.current_role() NOT IN ('staff', 'manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;

  RETURN public.pos_price_items(p_items);
END;
$$;

REVOKE ALL ON FUNCTION public.pos_quote(jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.pos_quote(jsonb) TO authenticated;

-- ---------------------------------------------------------------------------
-- 5) Complete an in-store sale (staff/manager, idempotent, atomic)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.pos_complete_sale(
  p_items jsonb,
  p_payment_method text,
  p_customer_name text,
  p_customer_phone text,
  p_idempotency_key text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_actor uuid := auth.uid();
  v_role public.user_role := public.current_role();
  v_payment_text text := COALESCE(NULLIF(btrim(COALESCE(p_payment_method, '')), ''), 'cod');
  v_name text := NULLIF(btrim(COALESCE(p_customer_name, '')), '');
  v_phone text := NULLIF(btrim(COALESCE(p_customer_phone, '')), '');
  v_key text := NULLIF(btrim(COALESCE(p_idempotency_key, '')), '');
  v_hash text;
  v_cached_hash text;
  v_cached_response jsonb;
  v_quote jsonb;
  v_item jsonb;
  v_line jsonb;
  v_product_id uuid;
  v_qty bigint;
  v_pos_sale_id uuid;
  v_bill_id uuid;
  v_bill_number text;
  v_response jsonb;
BEGIN
  IF v_actor IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;
  IF v_role NOT IN ('staff', 'manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff or manager required');
  END IF;
  IF v_key IS NULL THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Idempotency key required');
  END IF;

  v_hash := md5(jsonb_build_object(
    'items', COALESCE(p_items, '[]'::jsonb),
    'payment_method', v_payment_text,
    'customer_name', COALESCE(v_name, ''),
    'customer_phone', COALESCE(v_phone, '')
  )::text);

  PERFORM pg_advisory_xact_lock(hashtextextended(v_actor::text || ':pos:' || v_key, 0));

  SELECT request_hash, response INTO v_cached_hash, v_cached_response
  FROM public.idempotency_keys
  WHERE user_id = v_actor AND scope = 'pos_complete_sale' AND key = v_key;

  IF v_cached_hash IS NOT NULL THEN
    IF v_cached_hash <> v_hash THEN
      RETURN jsonb_build_object('error', 'IDEMPOTENCY_MISMATCH',
        'message', 'Idempotency key reused with different sale data');
    END IF;
    IF v_cached_response IS NOT NULL THEN
      RETURN v_cached_response;
    END IF;
  END IF;

  IF v_payment_text = 'online' THEN
    RETURN jsonb_build_object('error', 'ONLINE_PAYMENT_DISABLED',
      'message', 'Online payments are not enabled; use cash for in-store sales');
  END IF;
  IF v_payment_text <> 'cod' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Invalid payment method');
  END IF;

  IF p_items IS NULL OR jsonb_typeof(p_items) <> 'array' OR jsonb_array_length(p_items) = 0 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Add at least one item to the sale');
  END IF;

  -- Lock every product in UUID order, matching place_order, so online checkout
  -- and POS cannot both sell the last unit.
  FOR v_item IN
    SELECT value FROM jsonb_array_elements(p_items)
    ORDER BY (value->>'product_id')::uuid
  LOOP
    IF COALESCE(v_item->>'product_id', '')
       !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
      RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Invalid product identifier');
    END IF;

    PERFORM 1
    FROM public.products
    WHERE id = (v_item->>'product_id')::uuid
    FOR UPDATE;

  END LOOP;

  v_quote := public.pos_price_items(p_items);
  IF v_quote ? 'error' THEN
    RETURN v_quote;
  END IF;

  IF EXISTS (SELECT 1 FROM public.pos_sales WHERE idempotency_key = v_key) THEN
    RETURN jsonb_build_object('error', 'IDEMPOTENCY_MISMATCH',
      'message', 'This sale key has already been used');
  END IF;

  INSERT INTO public.pos_sales (idempotency_key, cashier_id, payment_method)
  VALUES (v_key, v_actor, 'cod')
  RETURNING id INTO v_pos_sale_id;

  v_bill_number := public.next_bill_number(now());

  INSERT INTO public.bills (
    bill_number, source, pos_sale_id, status, issued_by,
    customer_name, customer_phone,
    subtotal_paise, discount_paise, delivery_fee_paise, tax_paise,
    grand_total_paise, payment_method, payment_status
  ) VALUES (
    v_bill_number, 'POS', v_pos_sale_id, 'ISSUED', v_actor,
    v_name, v_phone,
    (v_quote->>'item_subtotal_paise')::bigint,
    (v_quote->>'discount_paise')::bigint,
    0,
    (v_quote->>'tax_paise')::bigint,
    (v_quote->>'grand_total_paise')::bigint,
    'cod', 'paid'
  ) RETURNING id INTO v_bill_id;

  FOR v_line IN SELECT value FROM jsonb_array_elements(v_quote->'lines')
  LOOP
    INSERT INTO public.bill_items (
      bill_id, line_no, product_id, sku, name, quantity_type, qty_base,
      unit_label, price_basis_qty, unit_price_paise, line_total_paise
    ) VALUES (
      v_bill_id,
      (v_line->>'line_no')::integer,
      (v_line->>'product_id')::uuid,
      v_line->>'sku',
      v_line->>'name',
      (v_line->>'quantity_type')::public.quantity_type,
      (v_line->>'qty_base')::bigint,
      v_line->>'unit_label',
      (v_line->>'price_basis_qty')::bigint,
      (v_line->>'unit_price_paise')::bigint,
      (v_line->>'line_total_paise')::bigint
    );

    v_product_id := (v_line->>'product_id')::uuid;
    v_qty := (v_line->>'qty_base')::bigint;

    UPDATE public.products
    SET stock_qty = stock_qty - v_qty
    WHERE id = v_product_id;

    INSERT INTO public.inventory_movements (
      product_id, delta_qty, reason, reference_type, reference_id, actor_id, note
    ) VALUES (
      v_product_id, -v_qty, 'pos_sale', 'pos_sale', v_pos_sale_id, v_actor, v_bill_number
    );
  END LOOP;

  INSERT INTO public.audit_logs (actor_id, action, entity_type, entity_id, after, request_id)
  VALUES (
    v_actor, 'POS_SALE', 'bills', v_bill_id::text,
    jsonb_build_object(
      'bill_number', v_bill_number,
      'pos_sale_id', v_pos_sale_id,
      'grand_total_paise', (v_quote->>'grand_total_paise')::bigint,
      'payment_method', 'cod'
    ),
    current_setting('request.id', true)::text
  );

  v_response := jsonb_build_object(
    'ok', true,
    'pos_sale_id', v_pos_sale_id,
    'bill_id', v_bill_id,
    'bill_number', v_bill_number,
    'breakdown', v_quote
  );

  INSERT INTO public.idempotency_keys (user_id, scope, key, request_hash, response)
  VALUES (v_actor, 'pos_complete_sale', v_key, v_hash, v_response)
  ON CONFLICT (user_id, scope, key)
  DO UPDATE SET request_hash = EXCLUDED.request_hash, response = EXCLUDED.response;

  RETURN v_response;
END;
$$;

REVOKE ALL ON FUNCTION public.pos_complete_sale(jsonb, text, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.pos_complete_sale(jsonb, text, text, text, text) TO authenticated;

-- ---------------------------------------------------------------------------
-- 6) Void a bill (manager only, reason required, restores POS stock once)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.void_bill(p_bill_id uuid, p_reason text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_actor uuid := auth.uid();
  v_role public.user_role := public.current_role();
  v_reason text := NULLIF(btrim(COALESCE(p_reason, '')), '');
  v_bill public.bills%ROWTYPE;
  v_item public.bill_items%ROWTYPE;
  v_restored boolean := false;
BEGIN
  IF v_actor IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;
  IF v_role <> 'manager' THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Manager access required');
  END IF;
  IF v_reason IS NULL THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'A void reason is required');
  END IF;

  SELECT * INTO v_bill FROM public.bills WHERE id = p_bill_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Bill not found');
  END IF;

  IF v_bill.status = 'VOID' THEN
    RETURN jsonb_build_object(
      'ok', true,
      'already_void', true,
      'bill_id', v_bill.id,
      'bill_number', v_bill.bill_number
    );
  END IF;

  -- POS sales deducted stock. Restore it exactly once, guarded by a bill-level
  -- movement check; ORDER bills never changed stock at billing time.
  IF v_bill.source = 'POS'
     AND NOT EXISTS (
       SELECT 1 FROM public.inventory_movements
       WHERE reason = 'pos_void'
         AND reference_type = 'bill'
         AND reference_id = p_bill_id
     ) THEN
    FOR v_item IN SELECT * FROM public.bill_items WHERE bill_id = p_bill_id LOOP
      IF v_item.product_id IS NOT NULL THEN
        UPDATE public.products
        SET stock_qty = stock_qty + v_item.qty_base
        WHERE id = v_item.product_id;

        INSERT INTO public.inventory_movements (
          product_id, delta_qty, reason, reference_type, reference_id, actor_id, note
        ) VALUES (
          v_item.product_id, v_item.qty_base, 'pos_void', 'bill', p_bill_id,
          v_actor, v_reason
        );
      END IF;
    END LOOP;
    v_restored := true;
  END IF;

  UPDATE public.bills
  SET status = 'VOID',
      void_reason = v_reason,
      voided_by = v_actor,
      voided_at = now()
  WHERE id = p_bill_id;

  RETURN jsonb_build_object(
    'ok', true,
    'already_void', false,
    'bill_id', v_bill.id,
    'bill_number', v_bill.bill_number,
    'stock_restored', v_restored
  );
END;
$$;

REVOKE ALL ON FUNCTION public.void_bill(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.void_bill(uuid, text) TO authenticated;
