-- Migration: 20261028000003_order_proofs.sql
-- Description:
--   Dispatch and delivery proof. Staff must upload a photo or video before an order
--   can go out for delivery, and the rider must upload one before it can be marked
--   delivered. The rule is enforced in the database (trigger on orders.status), not
--   only in the clients, so the web dashboard, the staff app and the rider screen all
--   obey it.
--
--   1. public.order_proofs — one row per (order, stage), with kind image|video.
--   2. `order-proofs` storage bucket (50 MB, image + video mime types), public read,
--      writes restricted to the caller's own <uid>/ folder.
--   3. attach_order_proof() — role-checked upload record, replaces an earlier proof of
--      the same stage so a bad photo can be retaken before the transition.
--   4. enforce_order_proof_status() — BEFORE UPDATE trigger that rejects
--      out_for_delivery without a dispatch proof and delivered without a delivery proof.
--      It only applies when a JWT is present; service-role maintenance without a JWT
--      is unaffected.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Proof table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.order_proofs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES public.orders(id) ON DELETE CASCADE,
  stage text NOT NULL CHECK (stage IN ('dispatch', 'delivery')),
  kind text NOT NULL CHECK (kind IN ('image', 'video')),
  path text NOT NULL CHECK (char_length(path) BETWEEN 3 AND 255),
  uploaded_by uuid REFERENCES public.profiles(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (order_id, stage)
);

CREATE INDEX IF NOT EXISTS idx_order_proofs_order ON public.order_proofs (order_id);

ALTER TABLE public.order_proofs ENABLE ROW LEVEL SECURITY;

-- Customers can see the proof for their own orders; staff, managers and riders see all.
DROP POLICY IF EXISTS "order_proofs_read" ON public.order_proofs;
CREATE POLICY "order_proofs_read"
  ON public.order_proofs
  FOR SELECT TO authenticated
  USING (
    public.current_role()::text IN ('staff', 'manager', 'delivery')
    OR EXISTS (
      SELECT 1 FROM public.orders o
      WHERE o.id = order_proofs.order_id
        AND o.customer_id = auth.uid()
    )
  );

-- Rows are written only through attach_order_proof(), so clients get SELECT only.
REVOKE ALL ON public.order_proofs FROM anon, authenticated;
GRANT SELECT ON public.order_proofs TO authenticated;

-- ---------------------------------------------------------------------------
-- 2. Storage bucket
-- ---------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'order-proofs',
  'order-proofs',
  true,
  52428800, -- 50 MB: a short phone video
  ARRAY[
    'image/jpeg', 'image/png', 'image/webp',
    'video/mp4', 'video/quicktime', 'video/webm'
  ]::text[]
)
ON CONFLICT (id) DO UPDATE SET
  public = true,
  file_size_limit = 52428800,
  allowed_mime_types = ARRAY[
    'image/jpeg', 'image/png', 'image/webp',
    'video/mp4', 'video/quicktime', 'video/webm'
  ]::text[];

DROP POLICY IF EXISTS "order_proofs_public_read" ON storage.objects;
CREATE POLICY "order_proofs_public_read"
  ON storage.objects
  FOR SELECT
  TO public
  USING (bucket_id = 'order-proofs');

DROP POLICY IF EXISTS "order_proofs_owner_insert" ON storage.objects;
CREATE POLICY "order_proofs_owner_insert"
  ON storage.objects
  FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'order-proofs'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "order_proofs_owner_update" ON storage.objects;
CREATE POLICY "order_proofs_owner_update"
  ON storage.objects
  FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'order-proofs'
    AND (storage.foldername(name))[1] = auth.uid()::text
  )
  WITH CHECK (
    bucket_id = 'order-proofs'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "order_proofs_owner_delete" ON storage.objects;
CREATE POLICY "order_proofs_owner_delete"
  ON storage.objects
  FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'order-proofs'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- ---------------------------------------------------------------------------
-- 3. Record a proof
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.attach_order_proof(
  p_order_id uuid,
  p_stage text,
  p_kind text,
  p_path text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user_id uuid := auth.uid();
  v_role text := public.current_role()::text;
  v_order public.orders%ROWTYPE;
  v_path text := btrim(COALESCE(p_path, ''));
BEGIN
  IF v_user_id IS NULL THEN
    RETURN jsonb_build_object('error', 'UNAUTHENTICATED', 'message', 'Login required');
  END IF;

  IF p_stage NOT IN ('dispatch', 'delivery') THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Unknown proof stage');
  END IF;

  -- Dispatch proof is staff work, delivery proof is the rider's.
  IF p_stage = 'dispatch' AND v_role NOT IN ('staff', 'manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Staff access required for dispatch proof');
  END IF;
  IF p_stage = 'delivery' AND v_role NOT IN ('delivery', 'staff', 'manager') THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Rider access required for delivery proof');
  END IF;

  IF p_kind NOT IN ('image', 'video') THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Proof must be an image or a video');
  END IF;

  IF v_path NOT LIKE v_user_id::text || '/%' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Invalid proof path');
  END IF;

  IF p_kind = 'video' AND v_path !~* '\.(mp4|mov|webm)$' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Video must be mp4, mov or webm');
  END IF;
  IF p_kind = 'image' AND v_path !~* '\.(jpg|jpeg|png|webp)$' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Photo must be jpg, png or webp');
  END IF;

  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('error', 'NOT_FOUND', 'message', 'Order not found');
  END IF;

  -- Dispatch proof can be taken while the order is still being picked or packed.
  IF p_stage = 'dispatch' AND v_order.status::text IN
     ('out_for_delivery', 'delivered', 'cancelled', 'rejected', 'delivery_failed') THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED',
      'message', 'The order has already left the store');
  END IF;

  -- Delivery proof belongs to the trip that is in progress.
  IF p_stage = 'delivery' AND v_order.status::text <> 'out_for_delivery' THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED',
      'message', 'Delivery proof can only be added while the order is out for delivery');
  END IF;

  INSERT INTO public.order_proofs (order_id, stage, kind, path, uploaded_by)
  VALUES (p_order_id, p_stage, p_kind, v_path, v_user_id)
  ON CONFLICT (order_id, stage) DO UPDATE SET
    kind = EXCLUDED.kind,
    path = EXCLUDED.path,
    uploaded_by = EXCLUDED.uploaded_by,
    created_at = now();

  RETURN jsonb_build_object('order_id', p_order_id, 'stage', p_stage, 'kind', p_kind, 'path', v_path);
END;
$$;

REVOKE ALL ON FUNCTION public.attach_order_proof(uuid, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.attach_order_proof(uuid, text, text, text) TO authenticated;

-- ---------------------------------------------------------------------------
-- 4. Enforce the proof before the two transitions
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.enforce_order_proof_status()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  -- Only user-authenticated changes are policed. Service-role maintenance
  -- (migrations, support fixes) runs without a JWT and stays possible.
  IF auth.uid() IS NULL THEN
    RETURN NEW;
  END IF;

  IF NEW.status::text = 'out_for_delivery'
     AND OLD.status::text IS DISTINCT FROM NEW.status::text
     AND NOT EXISTS (
       SELECT 1 FROM public.order_proofs p
       WHERE p.order_id = NEW.id AND p.stage = 'dispatch'
     ) THEN
    RAISE EXCEPTION 'Upload a photo or video of the packed items before marking this order out for delivery.'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.status::text = 'delivered'
     AND OLD.status::text IS DISTINCT FROM NEW.status::text
     AND NOT EXISTS (
       SELECT 1 FROM public.order_proofs p
       WHERE p.order_id = NEW.id AND p.stage = 'delivery'
     ) THEN
    RAISE EXCEPTION 'Upload a delivery photo or video before marking this order delivered.'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_orders_require_proof ON public.orders;
CREATE TRIGGER trg_orders_require_proof
  BEFORE UPDATE OF status ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.enforce_order_proof_status();

NOTIFY pgrst, 'reload schema';

COMMIT;
