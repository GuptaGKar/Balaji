-- M12: preserve the existing proof RPC contract and enforce assignment/upload authorization.
BEGIN;

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

  -- The definer bypasses RLS, so assignment must be checked inside the RPC.
  IF v_role = 'delivery' AND NOT EXISTS (
    SELECT 1 FROM public.order_assignments a
    WHERE a.order_id = p_order_id
      AND a.delivery_user_id = v_user_id
      AND a.unassigned_at IS NULL
  ) THEN
    RETURN jsonb_build_object('error', 'FORBIDDEN', 'message', 'Order is not assigned to this rider');
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

  -- A path alone is not evidence: the Storage upload must have completed first.
  -- Optional JSON fields keep this compatible with older managed Storage schemas.
  IF NOT EXISTS (
    SELECT 1 FROM storage.objects o
    WHERE o.bucket_id = 'order-proofs' AND o.name = v_path
      AND NOT COALESCE((to_jsonb(o)->>'is_delete_marker')::boolean, false)
      AND to_jsonb(o)->>'archived_at' IS NULL
  ) THEN
    RETURN jsonb_build_object('error', 'VALIDATION_FAILED', 'message', 'Upload the proof file before attaching it');
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

REVOKE ALL ON FUNCTION public.attach_order_proof(uuid, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.attach_order_proof(uuid, text, text, text) TO authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;
