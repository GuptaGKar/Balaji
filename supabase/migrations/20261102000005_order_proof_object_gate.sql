-- M12: re-check uploaded object presence at the actual dispatch/delivery transition.
BEGIN;

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
       JOIN storage.objects o ON o.bucket_id = 'order-proofs' AND o.name = p.path
       WHERE p.order_id = NEW.id AND p.stage = 'dispatch'
         AND NOT COALESCE((to_jsonb(o)->>'is_delete_marker')::boolean, false)
         AND to_jsonb(o)->>'archived_at' IS NULL
     ) THEN
    RAISE EXCEPTION 'Upload a photo or video of the packed items before marking this order out for delivery.'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.status::text = 'delivered'
     AND OLD.status::text IS DISTINCT FROM NEW.status::text
     AND NOT EXISTS (
       SELECT 1 FROM public.order_proofs p
       JOIN storage.objects o ON o.bucket_id = 'order-proofs' AND o.name = p.path
       WHERE p.order_id = NEW.id AND p.stage = 'delivery'
         AND NOT COALESCE((to_jsonb(o)->>'is_delete_marker')::boolean, false)
         AND to_jsonb(o)->>'archived_at' IS NULL
     ) THEN
    RAISE EXCEPTION 'Upload a delivery photo or video before marking this order delivered.'
      USING ERRCODE = '23514';
  END IF;

  RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.enforce_order_proof_status() FROM PUBLIC, anon, authenticated;
NOTIFY pgrst, 'reload schema';
COMMIT;
