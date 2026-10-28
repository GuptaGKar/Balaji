-- Migration: 20261028000002_requests_reviews_order_photos.sql
-- Description:
--   1. product_requests — a customer asks the store to stock a NEW item for sale.
--      Staff and managers triage it (new → accepted → added, or rejected) and write a
--      short reply. "added" links the request to the product row that was created in
--      the catalogue, so the request is closed by a real, sellable product.
--   2. order_reviews — one 1–5 star review per delivered order, visible to the
--      customer, staff and managers. Not public.
--   3. order photos — orders.image_path plus a public-read `order-images` bucket whose
--      insert policy only allows a user to write inside their own <uid>/ folder. The
--      path is attached after checkout through attach_order_image().
--
--   Money, pricing and stock rules are untouched by this migration.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Customer product requests
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.product_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  item_name text NOT NULL
    CHECK (char_length(btrim(item_name)) BETWEEN 2 AND 120),
  quantity_text text CHECK (quantity_text IS NULL OR char_length(quantity_text) <= 40),
  note text CHECK (note IS NULL OR char_length(note) <= 500),
  photo_path text CHECK (photo_path IS NULL OR char_length(photo_path) <= 255),
  status text NOT NULL DEFAULT 'new'
    CHECK (status IN ('new', 'accepted', 'rejected', 'added')),
  staff_reply text CHECK (staff_reply IS NULL OR char_length(staff_reply) <= 500),
  linked_product_id uuid REFERENCES public.products(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  -- A request can only be closed as "added" when the catalogue row exists.
  CONSTRAINT product_requests_added_needs_product
    CHECK (status <> 'added' OR linked_product_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_product_requests_status_created
  ON public.product_requests (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_product_requests_customer_created
  ON public.product_requests (customer_id, created_at DESC);

ALTER TABLE public.product_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "product_requests_customer_select" ON public.product_requests;
CREATE POLICY "product_requests_customer_select"
  ON public.product_requests
  FOR SELECT TO authenticated
  USING (customer_id = auth.uid());

DROP POLICY IF EXISTS "product_requests_customer_insert" ON public.product_requests;
CREATE POLICY "product_requests_customer_insert"
  ON public.product_requests
  FOR INSERT TO authenticated
  WITH CHECK (
    customer_id = auth.uid()
    AND status = 'new'
    AND staff_reply IS NULL
    AND linked_product_id IS NULL
  );

DROP POLICY IF EXISTS "product_requests_staff_select" ON public.product_requests;
CREATE POLICY "product_requests_staff_select"
  ON public.product_requests
  FOR SELECT TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'));

DROP POLICY IF EXISTS "product_requests_staff_update" ON public.product_requests;
CREATE POLICY "product_requests_staff_update"
  ON public.product_requests
  FOR UPDATE TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'))
  WITH CHECK (public.current_role()::text IN ('staff', 'manager'));

GRANT SELECT, INSERT ON public.product_requests TO authenticated;
GRANT UPDATE ON public.product_requests TO authenticated;

-- ---------------------------------------------------------------------------
-- 2. One review per delivered order
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.order_reviews (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL UNIQUE REFERENCES public.orders(id) ON DELETE CASCADE,
  customer_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  rating integer NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment text CHECK (comment IS NULL OR char_length(comment) <= 1000),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_order_reviews_customer
  ON public.order_reviews (customer_id, created_at DESC);

ALTER TABLE public.order_reviews ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "order_reviews_customer_select" ON public.order_reviews;
CREATE POLICY "order_reviews_customer_select"
  ON public.order_reviews
  FOR SELECT TO authenticated
  USING (customer_id = auth.uid());

DROP POLICY IF EXISTS "order_reviews_customer_insert" ON public.order_reviews;
CREATE POLICY "order_reviews_customer_insert"
  ON public.order_reviews
  FOR INSERT TO authenticated
  WITH CHECK (
    customer_id = auth.uid()
    AND EXISTS (
      SELECT 1 FROM public.orders o
      WHERE o.id = order_id
        AND o.customer_id = auth.uid()
        AND o.status = 'delivered'
    )
  );

DROP POLICY IF EXISTS "order_reviews_staff_select" ON public.order_reviews;
CREATE POLICY "order_reviews_staff_select"
  ON public.order_reviews
  FOR SELECT TO authenticated
  USING (public.current_role()::text IN ('staff', 'manager'));

GRANT SELECT, INSERT ON public.order_reviews TO authenticated;

-- ---------------------------------------------------------------------------
-- 3. Order photo path
-- ---------------------------------------------------------------------------
ALTER TABLE public.orders
  ADD COLUMN IF NOT EXISTS image_path text
  CHECK (image_path IS NULL OR char_length(image_path) <= 255);

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'order-images',
  'order-images',
  true,
  5242880,
  ARRAY['image/jpeg', 'image/png', 'image/webp']::text[]
)
ON CONFLICT (id) DO UPDATE SET
  public = true,
  file_size_limit = 5242880,
  allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp']::text[];

DROP POLICY IF EXISTS "order_images_public_read" ON storage.objects;
CREATE POLICY "order_images_public_read"
  ON storage.objects
  FOR SELECT
  TO public
  USING (bucket_id = 'order-images');

-- A signed-in user may only write inside <their uid>/ in this bucket. The RPCs below
-- reject any path that does not start with the caller's uid as well.
DROP POLICY IF EXISTS "order_images_owner_insert" ON storage.objects;
CREATE POLICY "order_images_owner_insert"
  ON storage.objects
  FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'order-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "order_images_owner_update" ON storage.objects;
CREATE POLICY "order_images_owner_update"
  ON storage.objects
  FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'order-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
  )
  WITH CHECK (
    bucket_id = 'order-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "order_images_owner_delete" ON storage.objects;
CREATE POLICY "order_images_owner_delete"
  ON storage.objects
  FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'order-images'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- ---------------------------------------------------------------------------
-- 4. RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_product_request(
  p_item_name text,
  p_quantity_text text DEFAULT NULL,
  p_note text DEFAULT NULL,
  p_photo_path text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_profile public.profiles%ROWTYPE;
  v_name text := btrim(COALESCE(p_item_name, ''));
  v_request_id uuid;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  SELECT * INTO v_profile FROM public.profiles WHERE id = v_user_id;
  IF NOT FOUND OR v_profile.role <> 'customer' OR NOT v_profile.is_active THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Customer access required');
  END IF;

  IF char_length(v_name) < 2 OR char_length(v_name) > 120 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Item name must be 2-120 characters');
  END IF;

  IF p_photo_path IS NOT NULL AND p_photo_path NOT LIKE v_user_id::text || '/%' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Invalid photo path');
  END IF;

  INSERT INTO public.product_requests (customer_id, item_name, quantity_text, note, photo_path)
  VALUES (
    v_user_id,
    v_name,
    NULLIF(btrim(COALESCE(p_quantity_text, '')), ''),
    NULLIF(btrim(COALESCE(p_note, '')), ''),
    NULLIF(btrim(COALESCE(p_photo_path, '')), '')
  )
  RETURNING id INTO v_request_id;

  RETURN jsonb_build_object('request_id', v_request_id, 'status', 'new');
END;
$$;

CREATE OR REPLACE FUNCTION public.staff_update_product_request(
  p_request_id uuid,
  p_status text,
  p_staff_reply text DEFAULT NULL,
  p_linked_product_id uuid DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_role text := public.current_role()::text;
  v_current public.product_requests%ROWTYPE;
BEGIN
  IF v_role NOT IN ('staff', 'manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff access required');
  END IF;

  IF p_status NOT IN ('accepted', 'rejected', 'added') THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Unknown status');
  END IF;

  SELECT * INTO v_current FROM public.product_requests WHERE id = p_request_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Request not found');
  END IF;

  IF v_current.status = 'added' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'This request is already closed');
  END IF;

  IF p_status = 'added' THEN
    IF p_linked_product_id IS NULL THEN
      RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Choose the product that was added to the catalogue');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.products WHERE id = p_linked_product_id) THEN
      RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Product not found');
    END IF;
  END IF;

  UPDATE public.product_requests
  SET status = p_status,
      staff_reply = NULLIF(btrim(COALESCE(p_staff_reply, '')), ''),
      linked_product_id = CASE WHEN p_status = 'added' THEN p_linked_product_id ELSE linked_product_id END,
      updated_at = now()
  WHERE id = p_request_id;

  RETURN jsonb_build_object('request_id', p_request_id, 'status', p_status);
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_order_review(
  p_order_id uuid,
  p_rating integer,
  p_comment text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_order public.orders%ROWTYPE;
  v_review_id uuid;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  IF p_rating IS NULL OR p_rating < 1 OR p_rating > 5 THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Rating must be between 1 and 5');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND OR v_order.customer_id <> v_user_id THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  IF v_order.status <> 'delivered' THEN
    RETURN jsonb_build_object('error', 'NOT_DELIVERED', 'message', 'You can review an order after it is delivered');
  END IF;

  BEGIN
    INSERT INTO public.order_reviews (order_id, customer_id, rating, comment)
    VALUES (p_order_id, v_user_id, p_rating, NULLIF(btrim(COALESCE(p_comment, '')), ''))
    RETURNING id INTO v_review_id;
  EXCEPTION WHEN unique_violation THEN
    RETURN jsonb_build_object('error', 'ALREADY_REVIEWED', 'message', 'This order already has a review');
  END;

  RETURN jsonb_build_object('review_id', v_review_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.attach_order_image(
  p_order_id uuid,
  p_image_path text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_order public.orders%ROWTYPE;
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  IF p_image_path IS NULL OR p_image_path NOT LIKE v_user_id::text || '/%' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Invalid photo path');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND OR v_order.customer_id <> v_user_id THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  IF v_order.status NOT IN ('pending_review', 'accepted') THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'The photo can only be attached before the order is prepared');
  END IF;

  UPDATE public.orders
  SET image_path = p_image_path
  WHERE id = p_order_id;

  RETURN jsonb_build_object('order_id', p_order_id, 'image_path', p_image_path);
END;
$$;

REVOKE ALL ON FUNCTION public.create_product_request(text, text, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.staff_update_product_request(uuid, text, text, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_order_review(uuid, integer, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.attach_order_image(uuid, text) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_product_request(text, text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.staff_update_product_request(uuid, text, text, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.submit_order_review(uuid, integer, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.attach_order_image(uuid, text) TO authenticated;

NOTIFY pgrst, 'reload schema';

COMMIT;
