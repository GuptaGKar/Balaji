import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
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

    const { cart_items, delivery_pin_code } = await req.json();

    if (!cart_items || !Array.isArray(cart_items) || cart_items.length === 0) {
      return new Response(JSON.stringify({ error: 'Cart is empty' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    if (!delivery_pin_code) {
      return new Response(JSON.stringify({ error: 'Delivery PIN code required' }), {
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

    // Fetch product details and validate stock
    const productIds = cart_items.map((item: any) => item.product_id);
    const { data: products, error: prodError } = await supabase
      .from('products')
      .select('id, name, sku, product_type, unit, price_paise, price_basis_unit, stock_quantity, is_active, is_available')
      .in('id', productIds);

    if (prodError) throw prodError;

    const productMap = new Map(products.map(p => [p.id, p]));

    let subtotal_paise = 0;
    const validatedItems = [];

    for (const item of cart_items) {
      const product = productMap.get(item.product_id);
      if (!product) {
        return new Response(JSON.stringify({ 
          error: `Product not found: ${item.product_id}`,
          code: 'PRODUCT_NOT_FOUND'
        }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      }

      if (!product.is_active || !product.is_available) {
        return new Response(JSON.stringify({ 
          error: `${product.name} is not available`,
          code: 'PRODUCT_UNAVAILABLE'
        }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      }

      // Validate quantity
      const baseQty = item.quantity; // already in base units from client
      if (baseQty <= 0) {
        return new Response(JSON.stringify({ 
          error: `Invalid quantity for ${product.name}`,
          code: 'INVALID_QUANTITY'
        }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      }

      if (baseQty > product.stock_quantity) {
        return new Response(JSON.stringify({ 
          error: `Insufficient stock for ${product.name}. Available: ${product.stock_quantity} ${product.unit}`,
          code: 'INSUFFICIENT_STOCK'
        }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      }

      // Calculate line total
      let lineTotal = 0;
      if (product.product_type === 'weighted') {
        const basisInGrams = product.price_basis_unit === 'kg' ? 1000 : 1;
        lineTotal = Math.round((product.price_paise * baseQty) / basisInGrams);
      } else {
        lineTotal = product.price_paise * baseQty;
      }

      subtotal_paise += lineTotal;

      validatedItems.push({
        product_id: product.id,
        product_name: product.name,
        product_sku: product.sku,
        product_type: product.product_type,
        unit: product.unit,
        price_basis_unit: product.price_basis_unit,
        price_paise: product.price_paise,
        quantity: baseQty,
        line_total_paise: lineTotal
      });
    }

    // Get settings for delivery fee calculation
    const { data: settings } = await supabase
      .from('settings')
      .select('key, value')
      .in('key', ['delivery_charge_threshold_paise', 'delivery_fee_paise', 'free_delivery_threshold_paise']);

    const settingMap = new Map(settings.map(s => [s.key, s.value]));
    const chargeThreshold = (settingMap.get('delivery_charge_threshold_paise')?.[0] as number) || 25000;
    const freeThreshold = (settingMap.get('free_delivery_threshold_paise')?.[0] as number) || 50000;
    const deliveryFee = (settingMap.get('delivery_fee_paise')?.[0] as number) || 3000;

    // Calculate delivery fee (server-side authoritative)
    let delivery_fee_paise = 0;
    if (subtotal_paise < chargeThreshold) {
      delivery_fee_paise = deliveryFee;
    } else if (subtotal_paise >= freeThreshold) {
      delivery_fee_paise = 0;
    } else {
      delivery_fee_paise = 0; // between charge threshold and free threshold
    }

    const total_paise = subtotal_paise + delivery_fee_paise;

    return new Response(JSON.stringify({
      subtotal_paise,
      delivery_fee_paise,
      total_paise,
      delivery_charge_threshold_paise: chargeThreshold,
      free_delivery_threshold_paise: freeThreshold,
      delivery_fee_paise: deliveryFee,
      service_area: serviceArea,
      items: validatedItems,
      currency: 'INR'
    }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    });

  } catch (error) {
    console.error('Checkout calculation error:', error);
    return new Response(JSON.stringify({ error: 'Internal server error' }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    });
  }
});