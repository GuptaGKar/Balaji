import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, idempotency-key',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL')!,
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
      { auth: { persistSession: false } }
    );

    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    const { data: { user } } = await supabase.auth.getUser(authHeader.replace('Bearer ', ''));
    if (!user) {
      return new Response(JSON.stringify({ error: 'Invalid token' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Check idempotency key
    const idempotencyKey = req.headers.get('idempotency-key');
    if (idempotencyKey) {
      const { data: existing } = await supabase
        .from('orders')
        .select('id, order_number')
        .eq('customer_id', user.id)
        .eq('payment_intent_id', idempotencyKey)
        .maybeSingle();
      
      if (existing) {
        return new Response(JSON.stringify({ 
          order_id: existing.id,
          order_number: existing.order_number,
          idempotent: true
        }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      }
    }

    const body = await req.json();
    const { 
      cart_items, 
      delivery_address_line1,
      delivery_address_line2,
      delivery_landmark,
      delivery_pin_code,
      delivery_instructions,
      payment_method = 'cod',
      customer_name,
      customer_phone
    } = body;

    if (!cart_items || !Array.isArray(cart_items) || cart_items.length === 0) {
      return new Response(JSON.stringify({ error: 'Cart is empty' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Validate required fields
    if (!delivery_address_line1 || !delivery_pin_code || !customer_name || !customer_phone) {
      return new Response(JSON.stringify({ error: 'Missing required delivery fields' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Validate service area
    const { data: serviceArea, error: saError } = await supabase
      .from('service_areas')
      .select('id, name')
      .eq('pin_code', delivery_pin_code)
      .eq('is_active', true)
      .single();

    if (saError || !serviceArea) {
      return new Response(JSON.stringify({ 
        error: 'Service not available in this area',
        code: 'SERVICE_UNAVAILABLE'
      }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Start transaction
    const { data: order, error: orderError } = await supabase.rpc('place_order_transaction', {
      p_customer_id: user.id,
      p_service_area_id: serviceArea.id,
      p_delivery_address_line1: delivery_address_line1,
      p_delivery_address_line2: delivery_address_line2 || '',
      p_delivery_landmark: delivery_landmark || '',
      p_delivery_pin_code: delivery_pin_code,
      p_delivery_instructions: delivery_instructions || '',
      p_customer_name: customer_name,
      p_customer_phone: customer_phone,
      p_payment_method: payment_method,
      p_cart_items: cart_items,
      p_idempotency_key: idempotencyKey || null
    });

    if (orderError) {
      console.error('Order placement error:', orderError);
      return new Response(JSON.stringify({ error: orderError.message }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    return new Response(JSON.stringify(order), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    });

  } catch (error) {
    console.error('Place order error:', error);
    return new Response(JSON.stringify({ error: 'Internal server error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    });
  }
});