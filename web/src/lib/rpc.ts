import { createServerSupabaseClient } from "./supabase-server";

export async function callRPC<T>(
  fn: string,
  params: Record<string, unknown>,
): Promise<{ data: T | null; error: { code: string; message: string } | null }> {
  const supabase = await createServerSupabaseClient();
  const { data, error } = await supabase.rpc(fn, params);
  if (error) {
    return { data: null, error: { code: error.code || "RPC_ERROR", message: error.message } };
  }
  return { data: data as T, error: null };
}

export async function callRPCWithUser<T>(
  fn: string,
  params: Record<string, unknown>,
): Promise<{ data: T | null; error: { code: string; message: string } | null }> {
  return callRPC<T>(fn, params);
}

// Rider-specific RPC
export interface RiderOrder {
  id: string;
  public_order_number: string;
  address_snapshot: any;
  payment_method: string;
  cod_collected_paise: number | null;
  grand_total_paise: number;
  status: string;
  customer_note: string;
  created_at: string;
  items: {
    id: string;
    name: string;
    qty: number;
    unit: string;
    unitPrice: number;
    total: number;
  }[];
}

export async function getRiderOrders(): Promise<{
  data: RiderOrder[] | null;
  error: { code: string; message: string } | null;
}> {
  const supabase = await createServerSupabaseClient();
  const { data, error } = await supabase.rpc("get_rider_orders");
  if (error) {
    return { data: null, error: { code: error.code || "RPC_ERROR", message: error.message } };
  }
  return { data: data as RiderOrder[], error: null };
}
