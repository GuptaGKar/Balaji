"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { createClient } from "@/lib/supabase";
import { formatRupees } from "@/lib/money";
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  Loader2,
  Minus,
  Plus,
  ReceiptText,
  Search,
  Trash2,
} from "lucide-react";

type PosProduct = {
  id: string;
  sku: string;
  name: string;
  quantity_type: string;
  price_paise: number;
  price_basis_qty: number;
  unit_label: string;
  step_qty: number;
  min_qty: number;
  max_qty: number | null;
  stock_qty: number;
  available: boolean;
};

type CartEntry = { product: PosProduct; qty: number };

type QuoteLine = {
  product_id: string;
  display_qty: string;
  price_basis_label: string;
  unit_price_paise: number;
  line_total_paise: number;
};

type Quote = {
  lines: QuoteLine[];
  item_subtotal_paise: number;
  discount_paise: number;
  delivery_fee_paise: number;
  tax_paise: number;
  grand_total_paise: number;
  error?: string;
  message?: string;
};

type Receipt = {
  bill_id: string;
  bill_number: string;
  breakdown: Quote;
};

function newKey() {
  return (
    globalThis.crypto?.randomUUID?.() ?? `pos-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

function basisLabel(product: PosProduct) {
  if (product.quantity_type.toUpperCase() === "WEIGHTED") {
    return product.price_basis_qty === 1000 ? "per kg" : `per ${product.price_basis_qty} g`;
  }
  return `per ${product.unit_label}`;
}

export default function PosPage() {
  const [supabase] = useState(() => createClient());
  const [query, setQuery] = useState("");
  const [products, setProducts] = useState<PosProduct[]>([]);
  const [searchLoading, setSearchLoading] = useState(true);
  const [searchError, setSearchError] = useState("");
  const [cart, setCart] = useState<CartEntry[]>([]);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteError, setQuoteError] = useState("");
  const [customerName, setCustomerName] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const idempotencyKey = useRef(newKey());

  const items = useMemo(
    () => cart.map((entry) => ({ product_id: entry.product.id, qty_base: entry.qty })),
    [cart],
  );
  const saleSignature = useMemo(
    () => JSON.stringify({ items, customerName, customerPhone }),
    [items, customerName, customerPhone],
  );

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setSearchLoading(true);
      setSearchError("");
      const { data, error } = await supabase.rpc("pos_search_products", {
        p_query: query.trim(),
      });
      if (controller.signal.aborted) return;
      if (error) {
        setSearchError(error.message);
        setProducts([]);
      } else {
        setProducts((data as PosProduct[]) ?? []);
      }
      setSearchLoading(false);
    }, 300);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [query, supabase]);

  useEffect(() => {
    idempotencyKey.current = newKey();
  }, [saleSignature]);

  useEffect(() => {
    if (items.length === 0) {
      setQuote(null);
      setQuoteError("");
      setQuoteLoading(false);
      return;
    }
    let cancelled = false;
    setQuoteLoading(true);
    const timer = window.setTimeout(async () => {
      const { data, error } = await supabase.rpc("pos_quote", { p_items: items });
      if (cancelled) return;
      const result = data as Quote | null;
      if (error) {
        setQuoteError(error.message);
        setQuote(null);
      } else if (result?.error) {
        setQuoteError(result.message || result.error);
        setQuote(null);
      } else {
        setQuoteError("");
        setQuote(result);
      }
      setQuoteLoading(false);
    }, 300);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [items, supabase]);

  const addProduct = (product: PosProduct) => {
    if (!product.available || product.stock_qty <= 0) return;
    setCart((current) => {
      const existing = current.find((entry) => entry.product.id === product.id);
      if (existing) return current;
      const initialQty = Math.max(
        product.min_qty,
        product.quantity_type.toUpperCase() === "WEIGHTED" ? product.min_qty : 1,
      );
      return [...current, { product, qty: Math.min(initialQty, product.stock_qty) }];
    });
    setReceipt(null);
    setSubmitError("");
  };

  const setQty = (productId: string, nextQty: number) => {
    setCart((current) =>
      current.map((entry) => {
        if (entry.product.id !== productId) return entry;
        const min = entry.product.min_qty;
        const max = Math.min(
          entry.product.stock_qty,
          entry.product.max_qty ?? entry.product.stock_qty,
        );
        return { ...entry, qty: Math.min(Math.max(Math.round(nextQty), min), max) };
      }),
    );
    setReceipt(null);
    setSubmitError("");
  };

  const remove = (productId: string) => {
    setCart((current) => current.filter((entry) => entry.product.id !== productId));
    setReceipt(null);
    setSubmitError("");
  };

  const completeSale = async () => {
    if (items.length === 0 || !quote || quoteError) return;
    setSubmitting(true);
    setSubmitError("");
    try {
      const { data, error } = await supabase.rpc("pos_complete_sale", {
        p_items: items,
        p_payment_method: "cod",
        p_customer_name: customerName.trim() || null,
        p_customer_phone: customerPhone.trim() || null,
        p_idempotency_key: idempotencyKey.current,
      });
      const result = data as (Receipt & { ok?: boolean; error?: string; message?: string }) | null;
      if (error) throw error;
      if (!result?.ok) throw new Error(result?.message || result?.error || "Sale failed");
      setReceipt(result);
      setCart([]);
      setQuote(null);
      setCustomerName("");
      setCustomerPhone("");
      setQuery("");
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "Could not complete the sale");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-5 pb-12">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <Link
            href="/billing"
            className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-500 hover:text-slate-900"
          >
            <ArrowLeft className="h-4 w-4" /> Back to bills
          </Link>
          <h1 className="mt-1 text-2xl font-extrabold tracking-tight text-slate-900">
            Point of sale
          </h1>
          <p className="text-xs font-medium text-slate-500 mt-1">
            Server-priced cash sale. No delivery fee. Stock is checked on every quote.
          </p>
        </div>
      </div>

      {receipt ? (
        <div
          className="flex flex-col items-start gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 p-6"
          role="status"
        >
          <CheckCircle2 className="h-8 w-8 text-emerald-600" />
          <div>
            <h2 className="text-lg font-extrabold text-emerald-900">Sale completed</h2>
            <p className="mt-1 font-mono text-sm font-bold text-emerald-800">
              {receipt.bill_number}
            </p>
            <p className="mt-1 text-sm text-emerald-800">
              Total {formatRupees(Number(receipt.breakdown.grand_total_paise))} · paid in cash
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link
              href={`/billing/${receipt.bill_id}`}
              className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-bold text-white hover:bg-emerald-700"
            >
              View / print bill
            </Link>
            <button
              type="button"
              onClick={() => setReceipt(null)}
              className="rounded-xl border border-emerald-300 bg-white px-4 py-2 text-xs font-bold text-emerald-800"
            >
              New sale
            </button>
          </div>
        </div>
      ) : null}

      <div className="grid grid-cols-1 xl:grid-cols-5 gap-5">
        <section className="xl:col-span-3 space-y-3" aria-label="POS product search">
          <div className="relative">
            <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search product name or SKU"
              aria-label="Search products for sale"
              className="w-full rounded-2xl border border-slate-300 bg-white py-3 pl-10 pr-4 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
          </div>

          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            {searchLoading ? (
              <div className="flex items-center justify-center gap-2 p-12 text-sm font-semibold text-slate-500">
                <Loader2 className="h-5 w-5 animate-spin text-emerald-600" /> Searching…
              </div>
            ) : searchError ? (
              <div className="flex flex-col items-center gap-2 p-10 text-center" role="alert">
                <AlertCircle className="h-7 w-7 text-red-500" />
                <p className="text-sm font-semibold text-red-700">{searchError}</p>
              </div>
            ) : products.length === 0 ? (
              <div className="p-10 text-center text-sm text-slate-500">No matching products.</div>
            ) : (
              <ul className="divide-y divide-slate-100">
                {products.map((product) => {
                  const disabled = !product.available || product.stock_qty <= 0;
                  const inCart = cart.some((entry) => entry.product.id === product.id);
                  return (
                    <li key={product.id} className="flex items-center gap-3 p-3">
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-bold text-slate-900">{product.name}</p>
                        <p className="text-xs text-slate-500">
                          {product.sku} · {formatRupees(product.price_paise)} {basisLabel(product)}{" "}
                          · stock {product.stock_qty}
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => addProduct(product)}
                        disabled={disabled || inCart}
                        className="shrink-0 rounded-xl bg-emerald-600 px-3 py-2 text-xs font-bold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                      >
                        {inCart ? "Added" : disabled ? "Unavailable" : "Add"}
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </section>

        <section className="xl:col-span-2 space-y-3" aria-label="Current sale">
          <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
            <h2 className="text-sm font-extrabold text-slate-900">Current sale</h2>
            {cart.length === 0 ? (
              <div className="py-10 text-center">
                <ReceiptText className="mx-auto mb-2 h-9 w-9 text-slate-300" />
                <p className="text-xs text-slate-500">Add products to start a sale.</p>
              </div>
            ) : (
              <ul className="mt-3 space-y-3">
                {cart.map((entry) => {
                  const line = quote?.lines.find((row) => row.product_id === entry.product.id);
                  const weighted = entry.product.quantity_type.toUpperCase() === "WEIGHTED";
                  const step = Math.max(1, entry.product.step_qty);
                  return (
                    <li key={entry.product.id} className="rounded-xl border border-slate-200 p-3">
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <p className="truncate text-xs font-bold text-slate-900">
                            {entry.product.name}
                          </p>
                          <p className="text-[11px] text-slate-500">
                            {line
                              ? `${line.display_qty} · ${formatRupees(line.unit_price_paise)} ${line.price_basis_label}`
                              : formatRupees(entry.product.price_paise)}
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={() => remove(entry.product.id)}
                          className="rounded-lg p-1 text-slate-400 hover:bg-red-50 hover:text-red-600"
                          aria-label={`Remove ${entry.product.name}`}
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                      <div className="mt-2 flex items-center justify-between gap-2">
                        <div className="flex items-center gap-1">
                          <button
                            type="button"
                            onClick={() => setQty(entry.product.id, entry.qty - step)}
                            className="rounded-lg border border-slate-300 p-1.5 text-slate-600 hover:bg-slate-50"
                            aria-label={`Decrease ${entry.product.name}`}
                          >
                            <Minus className="h-3.5 w-3.5" />
                          </button>
                          <label className="sr-only" htmlFor={`qty-${entry.product.id}`}>
                            Quantity for {entry.product.name} in {weighted ? "grams" : "packs"}
                          </label>
                          <input
                            id={`qty-${entry.product.id}`}
                            type="number"
                            inputMode="numeric"
                            min={entry.product.min_qty}
                            max={Math.min(
                              entry.product.stock_qty,
                              entry.product.max_qty ?? entry.product.stock_qty,
                            )}
                            step={step}
                            value={entry.qty}
                            onChange={(event) =>
                              setQty(entry.product.id, Number(event.target.value))
                            }
                            className="w-20 rounded-lg border border-slate-300 px-2 py-1 text-center text-xs font-bold focus:outline-none focus:ring-2 focus:ring-emerald-500"
                          />
                          <button
                            type="button"
                            onClick={() => setQty(entry.product.id, entry.qty + step)}
                            className="rounded-lg border border-slate-300 p-1.5 text-slate-600 hover:bg-slate-50"
                            aria-label={`Increase ${entry.product.name}`}
                          >
                            <Plus className="h-3.5 w-3.5" />
                          </button>
                          <span className="ml-1 text-[10px] font-semibold text-slate-400">
                            {weighted ? "g" : entry.product.unit_label}
                          </span>
                        </div>
                        <p className="text-xs font-extrabold text-slate-900">
                          {line ? formatRupees(line.line_total_paise) : "—"}
                        </p>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}

            <div className="mt-4 space-y-2 border-t border-slate-200 pt-3 text-xs">
              {quoteLoading ? (
                <p className="flex items-center gap-2 text-slate-500">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" /> Recalculating…
                </p>
              ) : quoteError ? (
                <p className="font-semibold text-red-700" role="alert">
                  {quoteError}
                </p>
              ) : quote ? (
                <>
                  <div className="flex justify-between text-slate-500">
                    <span>Subtotal</span>
                    <span className="font-semibold text-slate-800">
                      {formatRupees(Number(quote.item_subtotal_paise))}
                    </span>
                  </div>
                  {Number(quote.tax_paise) > 0 ? (
                    <div className="flex justify-between text-slate-500">
                      <span>Tax</span>
                      <span className="font-semibold text-slate-800">
                        {formatRupees(Number(quote.tax_paise))}
                      </span>
                    </div>
                  ) : null}
                  <div className="flex justify-between border-t border-slate-200 pt-2 text-sm font-extrabold text-slate-900">
                    <span>Total</span>
                    <span>{formatRupees(Number(quote.grand_total_paise))}</span>
                  </div>
                </>
              ) : (
                <p className="text-slate-400">No quote yet.</p>
              )}
            </div>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm space-y-3">
            <h2 className="text-sm font-extrabold text-slate-900">Customer (optional)</h2>
            <div>
              <label htmlFor="pos-name" className="mb-1 block text-[11px] font-bold text-slate-600">
                Name
              </label>
              <input
                id="pos-name"
                type="text"
                value={customerName}
                onChange={(event) => setCustomerName(event.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
            <div>
              <label
                htmlFor="pos-phone"
                className="mb-1 block text-[11px] font-bold text-slate-600"
              >
                Phone
              </label>
              <input
                id="pos-phone"
                type="tel"
                value={customerPhone}
                onChange={(event) => setCustomerPhone(event.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
            <p className="rounded-lg bg-slate-50 px-3 py-2 text-[11px] font-semibold text-slate-500">
              Payment: cash / COD. Online payment is disabled.
            </p>
            {submitError ? (
              <p className="text-xs font-semibold text-red-700" role="alert">
                {submitError}
              </p>
            ) : null}
            <button
              type="button"
              onClick={completeSale}
              disabled={
                submitting || cart.length === 0 || quoteLoading || Boolean(quoteError) || !quote
              }
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-3 text-sm font-bold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <CheckCircle2 className="h-4 w-4" />
              )}
              {submitting
                ? "Completing sale…"
                : quote
                  ? `Complete sale · ${formatRupees(Number(quote.grand_total_paise))}`
                  : "Complete sale"}
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}
