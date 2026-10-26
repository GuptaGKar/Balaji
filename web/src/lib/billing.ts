export type BillItem = {
  id: string;
  line_no: number;
  product_id: string | null;
  sku: string;
  name: string;
  quantity_type: string;
  qty_base: number;
  unit_label: string;
  display_qty: string;
  price_basis_qty: number;
  price_basis_label: string;
  unit_price_paise: number;
  line_total_paise: number;
};

export type Bill = {
  id: string;
  bill_number: string;
  source: "ORDER" | "POS";
  status: "ISSUED" | "VOID";
  order_id: string | null;
  order_public_number: string | null;
  pos_sale_id: string | null;
  issued_at: string;
  customer_name: string | null;
  customer_phone: string | null;
  subtotal_paise: number;
  discount_paise: number;
  delivery_fee_paise: number;
  tax_paise: number;
  grand_total_paise: number;
  payment_method: string;
  payment_status: string;
  voided_at: string | null;
  void_reason: string | null;
};

export type BillData = {
  bill: Bill;
  items: BillItem[];
  store: { name: string; phone: string; timezone: string };
};

export function paymentMethodLabel(method: string): string {
  if (method === "cod") return "Cash on Delivery / Cash";
  if (method === "online") return "Online";
  return method;
}

export function paymentStatusLabel(status: string): string {
  return status.replace(/_/g, " ");
}

export function billSourceLabel(source: string): string {
  return source === "POS" ? "In-store sale" : "Online order";
}

export function formatBillDateTime(iso: string, timezone: string): string {
  try {
    return new Intl.DateTimeFormat("en-IN", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: timezone || "Asia/Kolkata",
    }).format(new Date(iso));
  } catch {
    return new Date(iso).toLocaleString("en-IN");
  }
}
