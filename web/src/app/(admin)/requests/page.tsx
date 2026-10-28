"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { createClient } from "@/lib/supabase";
import { SafeImage } from "@/components/SafeImage";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { AlertCircle, CheckCircle2, Loader2, PackageSearch, XCircle } from "lucide-react";

type RequestStatus = "new" | "accepted" | "rejected" | "added";

type ProductRequest = {
  id: string;
  customer_id: string;
  item_name: string;
  quantity_text: string | null;
  note: string | null;
  photo_path: string | null;
  status: RequestStatus;
  staff_reply: string | null;
  linked_product_id: string | null;
  created_at: string;
};

type Customer = { id: string; display_name: string | null; phone: string | null };
type ProductOption = { id: string; name: string; sku: string };

const STATUS_STYLES: Record<RequestStatus, string> = {
  new: "bg-amber-100 text-amber-800",
  accepted: "bg-blue-100 text-blue-800",
  added: "bg-emerald-100 text-emerald-800",
  rejected: "bg-slate-200 text-slate-700",
};

function photoUrl(path: string): string {
  const base = (process.env.NEXT_PUBLIC_SUPABASE_URL || "").replace(/\/+$/, "");
  return `${base}/storage/v1/object/public/order-images/${path}`;
}

export default function ProductRequestsPage() {
  const supabase = createClient();

  const [requests, setRequests] = useState<ProductRequest[]>([]);
  const [customers, setCustomers] = useState<Record<string, Customer>>({});
  const [products, setProducts] = useState<ProductOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [statusFilter, setStatusFilter] = useState<RequestStatus | "all">("new");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [actionError, setActionError] = useState("");
  const [replyDrafts, setReplyDrafts] = useState<Record<string, string>>({});
  const [productDrafts, setProductDrafts] = useState<Record<string, string>>({});

  const fetchRequests = useCallback(async () => {
    setLoadError("");
    setLoading(true);

    let query = supabase
      .from("product_requests")
      .select(
        "id, customer_id, item_name, quantity_text, note, photo_path, status, staff_reply, linked_product_id, created_at",
      )
      .order("created_at", { ascending: false })
      .limit(200);
    if (statusFilter !== "all") query = query.eq("status", statusFilter);

    const { data, error } = await query;
    if (error) {
      setLoadError(error.message);
      setLoading(false);
      return;
    }

    const rows = (data as ProductRequest[]) || [];
    setRequests(rows);

    const customerIds = Array.from(new Set(rows.map((row) => row.customer_id)));
    if (customerIds.length > 0) {
      const { data: profiles } = await supabase
        .from("profiles")
        .select("id, display_name, phone")
        .in("id", customerIds);
      const map: Record<string, Customer> = {};
      ((profiles as Customer[] | null) || []).forEach((profile) => {
        map[profile.id] = profile;
      });
      setCustomers(map);
    } else {
      setCustomers({});
    }

    setLoading(false);
  }, [statusFilter, supabase]);

  useEffect(() => {
    fetchRequests();
  }, [fetchRequests]);

  useEffect(() => {
    supabase
      .from("products")
      .select("id, name, sku")
      .eq("active", true)
      .order("name", { ascending: true })
      .limit(500)
      .then(({ data }) => setProducts((data as ProductOption[]) || []));
  }, [supabase]);

  const updateRequest = async (
    request: ProductRequest,
    status: "accepted" | "rejected" | "added",
  ) => {
    setActionError("");
    setBusyId(request.id);
    const linkedProductId = status === "added" ? productDrafts[request.id] || null : null;
    const { data, error } = await supabase.rpc("staff_update_product_request", {
      p_request_id: request.id,
      p_status: status,
      p_staff_reply: replyDrafts[request.id] || null,
      p_linked_product_id: linkedProductId,
    });
    setBusyId(null);

    if (error) {
      setActionError(error.message);
      return;
    }
    const result = data as { error?: string; message?: string; status?: string } | null;
    if (result?.error) {
      setActionError(result.message || result.error);
      return;
    }
    await fetchRequests();
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Product Requests</h1>
          <p className="mt-1 text-sm text-slate-500">
            Customers ask the store to stock a new item. Accept it, add the product in the
            catalogue, then close the request as added and link the product.
          </p>
        </div>
        <Link
          href="/catalogue"
          className="rounded-xl bg-orange-600 px-4 py-2 text-sm font-semibold text-white hover:bg-orange-700"
        >
          Add product in catalogue
        </Link>
      </div>

      <div className="flex flex-wrap gap-2">
        {(["new", "accepted", "added", "rejected", "all"] as const).map((value) => (
          <button
            key={value}
            type="button"
            onClick={() => setStatusFilter(value)}
            className={cn(
              "rounded-full border px-3 py-1.5 text-xs font-semibold capitalize",
              statusFilter === value
                ? "border-orange-600 bg-orange-50 text-orange-700"
                : "border-slate-200 bg-white text-slate-600 hover:border-slate-300",
            )}
          >
            {value === "all" ? "All" : value}
          </button>
        ))}
      </div>

      {actionError && (
        <div className="flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          <AlertCircle className="mt-0.5 h-4 w-4 flex-none" />
          <span>{actionError}</span>
        </div>
      )}

      {loading ? (
        <div className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading requests…
        </div>
      ) : loadError ? (
        <div className="rounded-2xl border border-red-200 bg-red-50 p-6 text-sm text-red-700">
          {loadError}
        </div>
      ) : requests.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-slate-200 bg-white p-10 text-center">
          <PackageSearch className="h-8 w-8 text-slate-300" />
          <p className="text-sm font-semibold text-slate-700">No requests here</p>
          <p className="text-xs text-slate-500">
            Customers can ask for a new item from the customer app; it will appear in this list.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Item</th>
                <th className="px-4 py-3">Customer</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Reply &amp; action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {requests.map((request) => {
                const customer = customers[request.customer_id];
                const busy = busyId === request.id;
                const closed = request.status === "added" || request.status === "rejected";
                return (
                  <tr key={request.id} className="align-top">
                    <td className="px-4 py-4">
                      <p className="font-semibold text-slate-900">{request.item_name}</p>
                      {request.quantity_text && (
                        <p className="text-xs text-slate-500">{request.quantity_text}</p>
                      )}
                      {request.note && (
                        <p className="mt-1 max-w-xs text-xs text-slate-600">{request.note}</p>
                      )}
                      {request.photo_path && (
                        <a
                          href={photoUrl(request.photo_path)}
                          target="_blank"
                          rel="noreferrer"
                          className="mt-2 inline-block"
                        >
                          <SafeImage
                            src={photoUrl(request.photo_path)}
                            alt={`Photo for ${request.item_name}`}
                            className="h-16 w-24 rounded-lg border border-slate-200 object-cover"
                            fallback={<span className="text-xs text-slate-400">No photo</span>}
                          />
                        </a>
                      )}
                      <p className="mt-1 text-[11px] text-slate-400">
                        {formatDateTime(request.created_at)}
                      </p>
                    </td>
                    <td className="px-4 py-4">
                      <p className="text-sm text-slate-800">
                        {customer?.display_name || "Customer"}
                      </p>
                      <p className="text-xs text-slate-500">{customer?.phone || "No phone"}</p>
                    </td>
                    <td className="px-4 py-4">
                      <span
                        className={cn(
                          "rounded-full px-2 py-1 text-[11px] font-bold uppercase",
                          STATUS_STYLES[request.status],
                        )}
                      >
                        {request.status}
                      </span>
                      {request.linked_product_id && (
                        <p className="mt-2 text-[11px] text-slate-500">Product linked</p>
                      )}
                    </td>
                    <td className="px-4 py-4">
                      {closed ? (
                        <p className="max-w-xs text-xs text-slate-600">
                          {request.staff_reply || "Closed"}
                        </p>
                      ) : (
                        <div className="space-y-2">
                          <textarea
                            value={replyDrafts[request.id] ?? request.staff_reply ?? ""}
                            onChange={(event) =>
                              setReplyDrafts((prev) => ({
                                ...prev,
                                [request.id]: event.target.value,
                              }))
                            }
                            rows={2}
                            maxLength={500}
                            placeholder="Reply the customer will see"
                            className="w-full rounded-lg border border-slate-200 px-2 py-1.5 text-xs focus:border-orange-500 focus:outline-none"
                          />
                          <div className="flex flex-wrap gap-2">
                            {request.status === "new" && (
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => updateRequest(request, "accepted")}
                                className="inline-flex items-center gap-1 rounded-lg bg-blue-600 px-2.5 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                              >
                                <CheckCircle2 className="h-3.5 w-3.5" /> Accept
                              </button>
                            )}
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => updateRequest(request, "rejected")}
                              className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-semibold text-slate-600 disabled:opacity-50"
                            >
                              <XCircle className="h-3.5 w-3.5" /> Reject
                            </button>
                          </div>
                          {request.status === "accepted" && (
                            <div className="flex flex-wrap items-center gap-2">
                              <select
                                value={productDrafts[request.id] || ""}
                                onChange={(event) =>
                                  setProductDrafts((prev) => ({
                                    ...prev,
                                    [request.id]: event.target.value,
                                  }))
                                }
                                className="max-w-[220px] rounded-lg border border-slate-200 px-2 py-1.5 text-xs"
                              >
                                <option value="">Product added to catalogue…</option>
                                {products.map((product) => (
                                  <option key={product.id} value={product.id}>
                                    {product.name} ({product.sku})
                                  </option>
                                ))}
                              </select>
                              <button
                                type="button"
                                disabled={busy || !productDrafts[request.id]}
                                onClick={() => updateRequest(request, "added")}
                                className="rounded-lg bg-emerald-600 px-2.5 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                              >
                                {busy ? "Saving…" : "Mark added"}
                              </button>
                            </div>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
