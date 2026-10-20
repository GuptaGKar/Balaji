"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { createClient } from "@/lib/supabase";
import { formatRupees } from "@/lib/money";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  Loader2,
  Search,
  Eye,
  X,
  CheckCircle,
  Clock,
  Truck,
  Package,
  XCircle,
  RefreshCw,
  AlertTriangle,
} from "lucide-react";
import { Toaster, toast } from "sonner";

type OrderStatus =
  | "pending_review"
  | "accepted"
  | "preparing"
  | "ready_for_dispatch"
  | "out_for_delivery"
  | "delivered"
  | "rejected"
  | "cancelled"
  | "delivery_failed";

type Order = {
  id: string;
  public_order_number: string;
  status: OrderStatus;
  grand_total_paise: number;
  created_at: string;
  customer_id: string | null;
  payment_method: "cod" | "online";
  payment_status: string;
  cod_collected_paise: number | null;
  address_snapshot: {
    recipient_name?: string;
    phone?: string;
    address_lines?: string;
  } | null;
  customer_note: string | null;
  item_subtotal_paise: number;
  delivery_fee_paise: number;
};

type OrderItem = {
  id: string;
  sku_snapshot: string;
  name_snapshot: string;
  quantity_type_snapshot: string;
  unit_label_snapshot: string;
  qty_base: number;
  unit_price_paise: number;
  line_total_paise: number;
  removed_at: string | null;
  removed_reason: string | null;
};

function statusLabel(status: string) {
  return status.replace(/_/g, " ").toUpperCase();
}

function itemQuantity(item: OrderItem) {
  if (item.quantity_type_snapshot === "weighted") {
    return `${(item.qty_base / 1000).toFixed(3)} kg`;
  }
  return `${item.qty_base} × ${item.unit_label_snapshot || "pack"}`;
}

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [orderItems, setOrderItems] = useState<OrderItem[]>([]);
  const [modalLoading, setModalLoading] = useState(false);
  const [updatingId, setUpdatingId] = useState<string | null>(null);
  const [removingItemId, setRemovingItemId] = useState<string | null>(null);

  const supabase = createClient();

  const fetchOrders = useCallback(async () => {
    setLoadError("");
    let query = supabase
      .from("orders")
      .select(
        "id, public_order_number, status, grand_total_paise, created_at, customer_id, payment_method, payment_status, cod_collected_paise, address_snapshot, customer_note, item_subtotal_paise, delivery_fee_paise",
      )
      .order("created_at", { ascending: false })
      .limit(500);
    if (statusFilter !== "all") query = query.eq("status", statusFilter);
    const { data, error } = await query;
    if (error) {
      setLoadError(error.message);
      setLoading(false);
      return;
    }
    setOrders((data as Order[]) || []);
    setLoading(false);
  }, [statusFilter, supabase]);

  const fetchOrdersRef = useRef(fetchOrders);
  useEffect(() => {
    fetchOrdersRef.current = fetchOrders;
  }, [fetchOrders]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  useEffect(() => {
    const channel = supabase
      .channel("orders_channel")
      .on("postgres_changes", { event: "*", schema: "public", table: "orders" }, () => {
        fetchOrdersRef.current();
      })
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [supabase]);

  const fetchOrderDetails = async (order: Order) => {
    setSelectedOrder(order);
    setOrderItems([]);
    setModalLoading(true);
    const { data, error } = await supabase
      .from("order_items")
      .select(
        "id, sku_snapshot, name_snapshot, quantity_type_snapshot, unit_label_snapshot, qty_base, unit_price_paise, line_total_paise, removed_at, removed_reason",
      )
      .eq("order_id", order.id)
      .limit(200);
    if (error) {
      toast.error("Failed to load order items");
    } else {
      setOrderItems((data as OrderItem[]) || []);
    }
    setModalLoading(false);
  };

  const updateOrderStatus = async (order: Order, newStatus: OrderStatus) => {
    let reason: string | null = null;
    if (newStatus === "rejected" || newStatus === "cancelled" || newStatus === "delivery_failed") {
      const input = window.prompt(`Reason for marking this order ${statusLabel(newStatus).toLowerCase()} (required)`);
      if (!input || !input.trim()) {
        toast.error("A reason is required for this status change");
        return;
      }
      reason = input.trim();
    }

    setUpdatingId(order.id);
    try {
      const { data, error } = await supabase.rpc("transition_order", {
        p_order_id: order.id,
        p_to_status: newStatus,
        p_reason: reason,
      });
      if (error) throw error;
      if (data?.error) throw new Error(data.message || data.error);

      toast.success(`Order #${order.public_order_number} → ${statusLabel(newStatus).toLowerCase()}`);
      setSelectedOrder((current) =>
        current && current.id === order.id ? { ...current, status: newStatus } : current,
      );
      await fetchOrders();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update status");
    } finally {
      setUpdatingId(null);
    }
  };

  const removeUnavailableItem = async (order: Order, item: OrderItem) => {
    const reason = window.prompt(
      `Why is "${item.name_snapshot}" unavailable? The customer will see the updated bill.`,
    );
    if (!reason || !reason.trim()) {
      toast.error("A reason is required to remove an item");
      return;
    }

    setRemovingItemId(item.id);
    try {
      const { data, error } = await supabase.rpc("remove_order_item", {
        p_order_id: order.id,
        p_item_id: item.id,
        p_reason: reason.trim(),
      });
      if (error) throw error;
      if (data?.error) throw new Error(data.message || data.error);

      const cancelled = data?.order_cancelled === true;
      toast.success(
        cancelled
          ? "Item removed. No items remain, so the order was cancelled."
          : "Item marked unavailable and the customer bill was recalculated",
      );

      setOrderItems((current) =>
        current.map((row) =>
          row.id === item.id
            ? { ...row, removed_at: new Date().toISOString(), removed_reason: reason.trim() }
            : row,
        ),
      );
      setSelectedOrder((current) =>
        current && current.id === order.id
          ? {
              ...current,
              status: cancelled ? "cancelled" : current.status,
              item_subtotal_paise: Number(data?.item_subtotal_paise ?? current.item_subtotal_paise),
              delivery_fee_paise: Number(data?.delivery_fee_paise ?? current.delivery_fee_paise),
              grand_total_paise: Number(data?.grand_total_paise ?? current.grand_total_paise),
              payment_status: String(data?.payment_status ?? current.payment_status),
            }
          : current,
      );
      await fetchOrders();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to remove item");
    } finally {
      setRemovingItemId(null);
    }
  };

  const filteredOrders = orders.filter((o) => {
    const term = search.toLowerCase();
    const orderNo = (o.public_order_number || "").toLowerCase();
    const recipient = (o.address_snapshot?.recipient_name || "").toLowerCase();
    const phone = (o.address_snapshot?.phone || "").toLowerCase();
    return orderNo.includes(term) || recipient.includes(term) || phone.includes(term);
  });

  const getStatusBadge = (status: string) => {
    const config: Record<
      string,
      { label: string; bg: string; icon: React.ReactNode }
    > = {
      pending_review: {
        label: "PENDING REVIEW",
        bg: "bg-amber-100 text-amber-800 border-amber-300",
        icon: <Clock className="h-3.5 w-3.5" />,
      },
      accepted: {
        label: "ACCEPTED",
        bg: "bg-blue-100 text-blue-800 border-blue-300",
        icon: <CheckCircle className="h-3.5 w-3.5" />,
      },
      preparing: {
        label: "PREPARING",
        bg: "bg-purple-100 text-purple-800 border-purple-300",
        icon: <Package className="h-3.5 w-3.5" />,
      },
      ready_for_dispatch: {
        label: "READY FOR DISPATCH",
        bg: "bg-indigo-100 text-indigo-800 border-indigo-300",
        icon: <Package className="h-3.5 w-3.5" />,
      },
      out_for_delivery: {
        label: "OUT FOR DELIVERY",
        bg: "bg-emerald-100 text-emerald-800 border-emerald-300",
        icon: <Truck className="h-3.5 w-3.5" />,
      },
      delivered: {
        label: "DELIVERED",
        bg: "bg-green-100 text-green-800 border-green-300",
        icon: <CheckCircle className="h-3.5 w-3.5" />,
      },
      cancelled: {
        label: "CANCELLED",
        bg: "bg-red-100 text-red-800 border-red-300",
        icon: <XCircle className="h-3.5 w-3.5" />,
      },
      rejected: {
        label: "REJECTED",
        bg: "bg-red-100 text-red-800 border-red-300",
        icon: <XCircle className="h-3.5 w-3.5" />,
      },
      delivery_failed: {
        label: "DELIVERY FAILED",
        bg: "bg-red-100 text-red-800 border-red-300",
        icon: <AlertTriangle className="h-3.5 w-3.5" />,
      },
    };
    const c = config[status] || {
      label: statusLabel(status),
      bg: "bg-slate-100 text-slate-800 border-slate-300",
      icon: <Clock className="h-3.5 w-3.5" />,
    };
    return (
      <span
        className={cn(
          "inline-flex items-center gap-1.5 px-3 py-1 text-xs font-bold rounded-full border",
          c.bg,
        )}
      >
        {c.icon}
        {c.label}
      </span>
    );
  };

  const actionButton = (order: Order, status: OrderStatus, label: string, className: string) => (
    <button
      onClick={() => updateOrderStatus(order, status)}
      disabled={updatingId === order.id}
      className={cn(
        "px-3 py-1.5 text-white text-xs font-bold rounded-lg shadow-sm disabled:opacity-50",
        className,
      )}
    >
      {updatingId === order.id ? "Updating..." : label}
    </button>
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  return (
    <div className="w-full space-y-6 pb-12">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">
            Store Orders Queue
          </h1>
          <p className="text-xs font-medium text-slate-500 mt-0.5">
            Manage incoming mobile app orders and update delivery status
          </p>
        </div>

        <button
          onClick={fetchOrders}
          className="flex items-center gap-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-bold rounded-xl border border-slate-300 transition-all self-start sm:self-auto"
        >
          <RefreshCw className="h-4 w-4" /> Refresh Orders
        </button>
      </div>

      {loadError && (
        <div
          role="alert"
          className="flex items-center justify-between rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm font-medium text-red-700"
        >
          <span>{loadError}</span>
          <button onClick={fetchOrders} className="font-bold underline">
            Retry
          </button>
        </div>
      )}

      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4 bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search by order #, recipient or phone..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Search orders"
            className="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>

        <div className="flex items-center gap-2 overflow-x-auto scrollbar-hide py-1">
          {[
            "all",
            "pending_review",
            "accepted",
            "preparing",
            "ready_for_dispatch",
            "out_for_delivery",
            "delivered",
            "rejected",
            "cancelled",
          ].map((st) => (
            <button
              key={st}
              onClick={() => setStatusFilter(st)}
              className={cn(
                "px-3.5 py-1.5 text-xs font-bold rounded-xl transition-all whitespace-nowrap",
                statusFilter === st
                  ? "bg-emerald-600 text-white shadow-sm"
                  : "bg-slate-100 text-slate-600 hover:bg-slate-200",
              )}
            >
              {st === "all" ? "All Orders" : statusLabel(st)}
            </button>
          ))}
        </div>
      </div>

      <div className="w-full bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="overflow-x-auto w-full">
          <table className="w-full text-left border-collapse min-w-[800px]">
            <thead className="bg-slate-50 border-b border-slate-200 text-xs font-bold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="px-5 py-3.5">Order #</th>
                <th className="px-5 py-3.5">Recipient Details</th>
                <th className="px-5 py-3.5">Amount</th>
                <th className="px-5 py-3.5">Status</th>
                <th className="px-5 py-3.5">Date &amp; Time</th>
                <th className="px-5 py-3.5 text-right">Store Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 text-sm text-slate-700">
              {filteredOrders.map((o) => {
                const addr = o.address_snapshot || {};
                return (
                  <tr key={o.id} className="hover:bg-slate-50/80 transition-colors">
                    <td className="px-5 py-4 font-extrabold text-slate-900 font-mono">
                      #{o.public_order_number}
                    </td>
                    <td className="px-5 py-4">
                      <p className="font-bold text-slate-900">
                        {addr.recipient_name || "Customer"}
                      </p>
                      <p className="text-xs text-slate-500">{addr.phone || "No phone"}</p>
                      <p className="text-xs text-slate-400 truncate max-w-[200px]">
                        {addr.address_lines || "Local Delivery"}
                      </p>
                    </td>
                    <td className="px-5 py-4 font-bold text-emerald-700 text-base">
                      {formatRupees(o.grand_total_paise || 0)}
                      <span className="text-[11px] block font-normal text-slate-400">
                        {o.payment_method === "cod" ? "Cash on Delivery" : "Online (unpaid)"}
                      </span>
                    </td>
                    <td className="px-5 py-4">{getStatusBadge(o.status)}</td>
                    <td className="px-5 py-4 text-xs font-medium text-slate-500">
                      {formatDateTime(o.created_at)}
                    </td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        {o.status === "pending_review" &&
                          actionButton(o, "accepted", "Accept Order", "bg-emerald-600 hover:bg-emerald-700")}
                        {o.status === "pending_review" &&
                          actionButton(o, "rejected", "Reject", "bg-red-600 hover:bg-red-700")}
                        {o.status === "accepted" &&
                          actionButton(o, "preparing", "Mark Preparing", "bg-purple-600 hover:bg-purple-700")}
                        {o.status === "preparing" &&
                          actionButton(
                            o,
                            "ready_for_dispatch",
                            "Ready for Dispatch",
                            "bg-indigo-600 hover:bg-indigo-700",
                          )}
                        {o.status === "ready_for_dispatch" &&
                          actionButton(
                            o,
                            "out_for_delivery",
                            "Dispatch Delivery",
                            "bg-emerald-600 hover:bg-emerald-700",
                          )}
                        {o.status === "out_for_delivery" &&
                          actionButton(o, "delivered", "Mark Delivered", "bg-green-700 hover:bg-green-800")}
                        {o.status === "out_for_delivery" &&
                          actionButton(
                            o,
                            "delivery_failed",
                            "Delivery Failed",
                            "bg-red-600 hover:bg-red-700",
                          )}
                        {["pending_review", "accepted", "preparing", "ready_for_dispatch"].includes(
                          o.status,
                        ) &&
                          actionButton(o, "cancelled", "Cancel", "bg-slate-500 hover:bg-slate-600")}

                        <button
                          onClick={() => fetchOrderDetails(o)}
                          className="p-2 text-slate-500 hover:text-slate-900 hover:bg-slate-100 rounded-lg transition-colors"
                          title="View Details"
                          aria-label={`View order ${o.public_order_number}`}
                        >
                          <Eye className="h-4 w-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {filteredOrders.length === 0 && (
          <div className="p-12 text-center text-slate-400 font-medium">
            No orders found in this queue.
          </div>
        )}
      </div>

      {selectedOrder && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-label={`Order ${selectedOrder.public_order_number} details`}
        >
          <div className="bg-white rounded-3xl border border-slate-200 max-w-2xl w-full p-6 shadow-2xl space-y-5 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="text-lg font-extrabold text-slate-900">
                  Order #{selectedOrder.public_order_number}
                </h3>
                <p className="text-xs text-slate-400">
                  Placed on {formatDateTime(selectedOrder.created_at)}
                </p>
              </div>
              <button
                onClick={() => setSelectedOrder(null)}
                className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
                aria-label="Close order details"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-4 bg-slate-50 rounded-2xl border border-slate-200">
              <div>
                <p className="text-xs font-bold text-slate-400 uppercase">Customer Address</p>
                <p className="text-sm font-bold text-slate-900 mt-1">
                  {selectedOrder.address_snapshot?.recipient_name || "Customer"}
                </p>
                <p className="text-xs text-slate-600">
                  {selectedOrder.address_snapshot?.phone || "No phone"}
                </p>
                <p className="text-xs text-slate-500 mt-0.5">
                  {selectedOrder.address_snapshot?.address_lines || "Local Delivery"}
                </p>
                {selectedOrder.customer_note && (
                  <p className="mt-2 text-xs text-slate-600">
                    <span className="font-bold">Note:</span> {selectedOrder.customer_note}
                  </p>
                )}
              </div>

              <div>
                <p className="text-xs font-bold text-slate-400 uppercase">Current Order Status</p>
                <div className="mt-1">{getStatusBadge(selectedOrder.status)}</div>
                <p className="text-xs font-bold text-emerald-700 mt-2">
                  Total: {formatRupees(selectedOrder.grand_total_paise || 0)}
                </p>
                <p className="text-xs text-slate-500 mt-1">
                  {selectedOrder.payment_method === "cod"
                    ? "Cash on Delivery"
                    : "Online payment"}{" "}
                  · {selectedOrder.payment_status.replace(/_/g, " ")}
                </p>
              </div>
            </div>

            <div>
              <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                <h4 className="text-sm font-bold text-slate-900">Order Line Items</h4>
                {orderItems.some((item) => item.removed_at) && (
                  <span className="text-[11px] font-bold text-red-700">
                    Customer bill updated after unavailable-item removal
                  </span>
                )}
              </div>
              {modalLoading ? (
                <div className="flex items-center justify-center py-6">
                  <Loader2 className="h-6 w-6 animate-spin text-emerald-600" />
                </div>
              ) : orderItems.length === 0 ? (
                <p className="text-sm text-slate-500">No line items found for this order.</p>
              ) : (
                <div className="space-y-2 border border-slate-200 rounded-2xl p-3 bg-slate-50">
                  {orderItems.map((item) => {
                    const removed = item.removed_at != null;
                    const canRemove =
                      !removed &&
                      ["pending_review", "accepted", "preparing"].includes(selectedOrder.status);
                    return (
                      <div
                        key={item.id}
                        className="flex items-center justify-between gap-3 py-2 border-b border-slate-200 last:border-none text-xs"
                      >
                        <div className="min-w-0">
                          <p
                            className={cn(
                              "font-bold text-slate-900",
                              removed && "line-through text-slate-400",
                            )}
                          >
                            {item.name_snapshot}
                          </p>
                          <p className="text-slate-500">
                            {itemQuantity(item)} · {formatRupees(item.unit_price_paise || 0)}
                          </p>
                          {removed && (
                            <p className="text-[11px] font-bold text-red-600">
                              NOT AVAILABLE{item.removed_reason ? ` · ${item.removed_reason}` : ""}
                            </p>
                          )}
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <p className={cn("font-extrabold", removed ? "text-slate-400 line-through" : "text-slate-900")}>
                            {removed ? "Removed" : formatRupees(item.line_total_paise || 0)}
                          </p>
                          {canRemove && (
                            <button
                              onClick={() => removeUnavailableItem(selectedOrder, item)}
                              disabled={removingItemId === item.id}
                              className="px-2.5 py-1.5 text-[11px] font-bold rounded-lg border border-red-200 text-red-700 bg-red-50 hover:bg-red-100 disabled:opacity-50"
                            >
                              {removingItemId === item.id ? "Removing..." : "Mark unavailable"}
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
              {selectedOrder.status === "pending_review" &&
                actionButton(
                  selectedOrder,
                  "accepted",
                  "Accept Order",
                  "bg-emerald-600 hover:bg-emerald-700 !px-4 !py-2 !rounded-xl",
                )}
              {selectedOrder.status === "pending_review" &&
                actionButton(
                  selectedOrder,
                  "rejected",
                  "Reject",
                  "bg-red-600 hover:bg-red-700 !px-4 !py-2 !rounded-xl",
                )}
              {selectedOrder.status === "ready_for_dispatch" &&
                actionButton(
                  selectedOrder,
                  "out_for_delivery",
                  "Dispatch Delivery",
                  "bg-emerald-600 hover:bg-emerald-700 !px-4 !py-2 !rounded-xl",
                )}
              {selectedOrder.status === "out_for_delivery" &&
                actionButton(
                  selectedOrder,
                  "delivered",
                  "Mark Delivered",
                  "bg-green-700 hover:bg-green-800 !px-4 !py-2 !rounded-xl",
                )}
              {["pending_review", "accepted", "preparing", "ready_for_dispatch"].includes(
                selectedOrder.status,
              ) &&
                actionButton(
                  selectedOrder,
                  "cancelled",
                  "Cancel Order",
                  "bg-slate-600 hover:bg-slate-700 !px-4 !py-2 !rounded-xl",
                )}
              <button
                onClick={() => setSelectedOrder(null)}
                className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold rounded-xl"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      <Toaster position="top-right" />
    </div>
  );
}
