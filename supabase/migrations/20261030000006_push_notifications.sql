-- Migration: 20261030000006_push_notifications.sql
-- FCM plumbing that does not depend on the Firebase config files.
--
--   device_tokens  one row per installed app instance; owner-only RLS
--   push_queue     what to send and to which audience; written by triggers, drained
--                  by an Edge Function holding the FCM service account
--
-- Nothing sends here. Until the Edge Function is deployed the queue simply grows,
-- which is safe and observable.

BEGIN;

CREATE TABLE IF NOT EXISTS public.device_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  token text NOT NULL CHECK (char_length(token) BETWEEN 20 AND 4096),
  platform text NOT NULL DEFAULT 'android' CHECK (platform IN ('android')),
  app_flavour text NOT NULL CHECK (app_flavour IN ('customer', 'staff')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT device_tokens_token_unique UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON public.device_tokens (user_id);

ALTER TABLE public.device_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "device_tokens_owner_all" ON public.device_tokens;
CREATE POLICY "device_tokens_owner_all"
  ON public.device_tokens
  FOR ALL TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

GRANT SELECT, INSERT, UPDATE, DELETE ON public.device_tokens TO authenticated;

CREATE TABLE IF NOT EXISTS public.push_queue (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  audience text NOT NULL CHECK (audience IN ('customer', 'staff')),
  user_id uuid REFERENCES public.profiles(id) ON DELETE CASCADE,
  order_id uuid REFERENCES public.orders(id) ON DELETE CASCADE,
  title text NOT NULL,
  body text NOT NULL,
  data jsonb NOT NULL DEFAULT '{}'::jsonb,
  attempts integer NOT NULL DEFAULT 0,
  sent_at timestamptz,
  last_error text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_push_queue_pending
  ON public.push_queue (created_at)
  WHERE sent_at IS NULL;

ALTER TABLE public.push_queue ENABLE ROW LEVEL SECURITY;
-- No policies: only the service role (Edge Function) may read or write it.

-- New order -> every staff device.
CREATE OR REPLACE FUNCTION public.enqueue_new_order_push()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  INSERT INTO public.push_queue (audience, order_id, title, body, data)
  VALUES (
    'staff',
    NEW.id,
    'New order ' || COALESCE(NEW.public_order_number, ''),
    'Total ' || public.paise_to_rupees(NEW.grand_total_paise) || ' - tap to review',
    jsonb_build_object('type', 'new_order', 'order_id', NEW.id)
  );
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enqueue_new_order_push ON public.orders;
CREATE TRIGGER trg_enqueue_new_order_push
  AFTER INSERT ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.enqueue_new_order_push();

-- Status change -> that customer's devices.
CREATE OR REPLACE FUNCTION public.enqueue_order_status_push()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_label text;
BEGIN
  IF NEW.status IS NOT DISTINCT FROM OLD.status THEN
    RETURN NEW;
  END IF;

  v_label := replace(NEW.status::text, '_', ' ');

  INSERT INTO public.push_queue (audience, user_id, order_id, title, body, data)
  VALUES (
    'customer',
    NEW.customer_id,
    NEW.id,
    'Order ' || COALESCE(NEW.public_order_number, '') || ' - ' || v_label,
    CASE NEW.status::text
      WHEN 'accepted' THEN 'The store accepted your order.'
      WHEN 'preparing' THEN 'Your order is being packed.'
      WHEN 'ready_for_dispatch' THEN 'Your order is packed and waiting for a rider.'
      WHEN 'out_for_delivery' THEN 'Your order is on the way.'
      WHEN 'delivered' THEN 'Delivered. Thank you for shopping with us.'
      WHEN 'cancelled' THEN 'Your order was cancelled.'
      WHEN 'rejected' THEN 'The store could not accept your order.'
      ELSE 'Your order status changed.'
    END,
    jsonb_build_object('type', 'order_status', 'order_id', NEW.id, 'status', NEW.status)
  );
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enqueue_order_status_push ON public.orders;
CREATE TRIGGER trg_enqueue_order_status_push
  AFTER UPDATE OF status ON public.orders
  FOR EACH ROW EXECUTE FUNCTION public.enqueue_order_status_push();

NOTIFY pgrst, 'reload schema';

COMMIT;
