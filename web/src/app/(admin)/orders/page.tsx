"use client";

import React, { useCallback, useEffect, useState } from "react";
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
  ShoppingBag,
} from "lucide-react";
import { Toaster, toast } from "sonner";

type Order = {
  id: string;
  public_order_number: string;
  status: string;
  grand_total_paise: number;
  created_at: string;
  customer_id: string;
  payment_method: string;
  payment_status: string;
  cod_collected_paise: number | null;
  address_snapshot: any;
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
};

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [orderItems, setOrderItems] = useState<OrderItem[]>([]);
  const [modalLoading, setModalLoading] = useState(false);
  const [updatingId, setUpdatingId] = useState<string | null>(null);

  const supabase = createClient();

  const fetchOrders = useCallback(async () => {
    let query = supabase.from("orders").select("*").order("created_at", { ascending: false });
    if (statusFilter !== "all") query = query.eq("status", statusFilter);
    const { data, error } = await query;
    if (error) {
      toast.error("Failed to fetch orders");
      return;
    }
    setOrders(data || []);
    setLoading(false);
  }, [statusFilter, supabase]);

  // Realtime subscription with channel cleanup and visibilitychange refetching
  useEffect(() => {
    fetchOrders();

    const channel = supabase
      .channel("orders_channel")
      .on(
        "postgres_changes",
        { event: "*", schema: "public", table: "orders" },
        () => {
          fetchOrders();
        }
      )
      .subscribe((status) => {
        if (status === "SUBSCRIBED") {
          fetchOrders();
        }
      });

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        fetchOrders();
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      supabase.removeChannel(channel);
    };
  }, [fetchOrders, supabase]);

  const fetchOrderDetails = async (order: Order) => {
    setSelectedOrder(order);
    setModalLoading(true);
    const { data, error } = await supabase.from("order_items").select("*").eq("order_id", order.id);
    if (!error) setOrderItems(data || []);
    setModalLoading(false);
  };

  const updateOrderStatus = async (orderId: string, newStatus: string) => {
    setUpdatingId(orderId);
    try {
      const { error: rpcError } = await supabase.rpc("transition_order", {
        p_order_id: orderId,
        p_to_status: newStatus,
        p_reason: "Updated by Store Admin"
      });

      if (rpcError) {
        const updateData: any = {
          status: newStatus,
          ...(newStatus === "accepted" ? { accepted_at: new Date().toISOString() } : {}),
          ...(newStatus === "delivered" ? { delivered_at: new Date().toISOString(), payment_status: "paid" } : {}),
        };
        const { error: directError } = await supabase.from("orders").update(updateData).eq("id", orderId);
        if (directError) throw new Error(`${directError.code}: ${directError.message}`);
      }

      toast.success(`Order status changed to ${newStatus.replace("_", " ").toUpperCase()}`);
      if (selectedOrder?.id === orderId) {
        setSelectedOrder((prev) => (prev ? { ...prev, status: newStatus } : null));
      }
      fetchOrders();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update order status");
    } finally {
      setUpdatingId(null);
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
    const config: Record<string, { label: string; bg: string; icon: React.ReactNode }> = {
      pending_review: { label: "PENDING REVIEW", bg: "bg-amber-100 text-amber-800 border-amber-300", icon: <Clock className="h-3.5 w-3.5" /> },
      accepted: { label: "ACCEPTED", bg: "bg-blue-100 text-blue-800 border-blue-300", icon: <CheckCircle className="h-3.5 w-3.5" /> },
      preparing: { label: "PREPARING", bg: "bg-purple-100 text-purple-800 border-purple-300", icon: <Package className="h-3.5 w-3.5" /> },
      out_for_delivery: { label: "OUT FOR DELIVERY", bg: "bg-emerald-100 text-emerald-800 border-emerald-300", icon: <Truck className="h-3.5 w-3.5" /> },
      delivered: { label: "DELIVERED ✓", bg: "bg-green-100 text-green-800 border-green-300", icon: <CheckCircle className="h-3.5 w-3.5" /> },
      cancelled: { label: "CANCELLED", bg: "bg-red-100 text-red-800 border-red-300", icon: <XCircle className="h-3.5 w-3.5" /> },
      rejected: { label: "REJECTED", bg: "bg-red-100 text-red-800 border-red-300", icon: <XCircle className="h-3.5 w-3.5" /> },
    };
    const c = config[status] || { label: status.toUpperCase(), bg: "bg-slate-100 text-slate-800 border-slate-300", icon: <Clock className="h-3.5 w-3.5" /> };
    return (
      <span className={cn("inline-flex items-center gap-1.5 px-3 py-1 text-xs font-bold rounded-full border", c.bg)}>
        {c.icon}
        {c.label}
      </span>
    );
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  return (
    <div className="w-full space-y-6 pb-12">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Store Orders Queue</h1>
          <p className="text-xs font-medium text-slate-500 mt-0.5">Manage incoming mobile app orders and update delivery status</p>
        </div>

        <button
          onClick={fetchOrders}
          className="flex items-center gap-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-bold rounded-xl border border-slate-300 transition-all self-start sm:self-auto"
        >
          <RefreshCw className="h-4 w-4" /> Refresh Orders
        </button>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4 bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search by order #, recipient or phone..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
        </div>

        <div className="flex items-center gap-2 overflow-x-auto scrollbar-hide py-1">
          {["all", "pending_review", "accepted", "preparing", "out_for_delivery", "delivered", "cancelled"].map((st) => (
            <button
              key={st}
              onClick={() => setStatusFilter(st)}
              className={cn(
                "px-3.5 py-1.5 text-xs font-bold rounded-xl transition-all whitespace-nowrap",
                statusFilter === st
                  ? "bg-emerald-600 text-white shadow-sm"
                  : "bg-slate-100 text-slate-600 hover:bg-slate-200"
              )}
            >
              {st === "all" ? "All Orders" : st.replace("_", " ").toUpperCase()}
            </button>
          ))}
        </div>
      </div>

      {/* Orders Table */}
      <div className="w-full bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="overflow-x-auto w-full">
          <table className="w-full text-left border-collapse min-w-[800px]">
            <thead className="bg-slate-50 border-b border-slate-200 text-xs font-bold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="px-5 py-3.5">Order #</th>
                <th className="px-5 py-3.5">Recipient Details</th>
                <th className="px-5 py-3.5">Amount (COD)</th>
                <th className="px-5 py-3.5">Status</th>
                <th className="px-5 py-3.5">Date & Time</th>
                <th className="px-5 py-3.5 text-right">Store Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 text-sm text-slate-700">
              {filteredOrders.map((o) => {
                const isUpdating = updatingId === o.id;
                const addr = o.address_snapshot || {};
                return (
                  <tr key={o.id} className="hover:bg-slate-50/80 transition-colors">
                    <td className="px-5 py-4 font-extrabold text-slate-900 font-mono">
                      #{o.public_order_number}
                    </td>
                    <td className="px-5 py-4">
                      <p className="font-bold text-slate-900">{addr.recipient_name || "Customer"}</p>
                      <p className="text-xs text-slate-500">{addr.phone || "No phone"}</p>
                      <p className="text-xs text-slate-400 truncate max-w-[200px]">
                        {addr.address_lines || "Local Delivery"}
                      </p>
                    </td>
                    <td className="px-5 py-4 font-bold text-emerald-700 text-base">
                      {formatRupees(o.grand_total_paise || 0)}
                      <span className="text-[11px] block font-normal text-slate-400">Cash on Delivery</span>
                    </td>
                    <td className="px-5 py-4">{getStatusBadge(o.status)}</td>
                    <td className="px-5 py-4 text-xs font-medium text-slate-500">
                      {formatDateTime(o.created_at)}
                    </td>
                    <td className="px-5 py-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        {/* Admin Action Buttons */}
                        {o.status === "pending_review" && (
                          <button
                            onClick={() => updateOrderStatus(o.id, "accepted")}
                            disabled={isUpdating}
                            className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-lg shadow-sm"
                          >
                            Accept Order
                          </button>
                        )}

                        {o.status === "accepted" && (
                          <button
                            onClick={() => updateOrderStatus(o.id, "preparing")}
                            disabled={isUpdating}
                            className="px-3 py-1.5 bg-purple-600 hover:bg-purple-700 text-white text-xs font-bold rounded-lg shadow-sm"
                          >
                            Mark Preparing
                          </button>
                        )}

                        {(o.status === "preparing" || o.status === "accepted") && (
                          <button
                            onClick={() => updateOrderStatus(o.id, "out_for_delivery")}
                            disabled={isUpdating}
                            className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-lg shadow-sm"
                          >
                            Dispatch Delivery
                          </button>
                        )}

                        {o.status === "out_for_delivery" && (
                          <button
                            onClick={() => updateOrderStatus(o.id, "delivered")}
                            disabled={isUpdating}
                            className="px-3 py-1.5 bg-green-700 hover:bg-green-800 text-white text-xs font-bold rounded-lg shadow-sm"
                          >
                            Mark Delivered ✓
                          </button>
                        )}

                        <button
                          onClick={() => fetchOrderDetails(o)}
                          className="p-2 text-slate-500 hover:text-slate-900 hover:bg-slate-100 rounded-lg transition-colors"
                          title="View Details"
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
            No orders found in queue. Place an order on the Android app to see it appear here in real-time!
          </div>
        )}
      </div>

      {/* Order Details Modal */}
      {selectedOrder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
          <div className="bg-white rounded-3xl border border-slate-200 max-w-2xl w-full p-6 shadow-2xl space-y-5 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="text-lg font-extrabold text-slate-900">
                  Order #{selectedOrder.public_order_number}
                </h3>
                <p className="text-xs text-slate-400">Placed on {formatDateTime(selectedOrder.created_at)}</p>
              </div>
              <button
                onClick={() => setSelectedOrder(null)}
                className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Recipient & Status */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-4 bg-slate-50 rounded-2xl border border-slate-200">
              <div>
                <p className="text-xs font-bold text-slate-400 uppercase">Customer Address</p>
                <p className="text-sm font-bold text-slate-900 mt-1">{selectedOrder.address_snapshot?.recipient_name || "Customer"}</p>
                <p className="text-xs text-slate-600">{selectedOrder.address_snapshot?.phone}</p>
                <p className="text-xs text-slate-500 mt-0.5">{selectedOrder.address_snapshot?.address_lines}</p>
              </div>

              <div>
                <p className="text-xs font-bold text-slate-400 uppercase">Current Order Status</p>
                <div className="mt-1">{getStatusBadge(selectedOrder.status)}</div>
                <p className="text-xs font-bold text-emerald-700 mt-2">
                  Total: {formatRupees(selectedOrder.grand_total_paise || 0)} (COD)
                </p>
              </div>
            </div>

            {/* Order Items */}
            <div>
              <h4 className="text-sm font-bold text-slate-900 mb-2">Order Line Items</h4>
              {modalLoading ? (
                <div className="flex items-center justify-center py-6">
                  <Loader2 className="h-6 w-6 animate-spin text-emerald-600" />
                </div>
              ) : (
                <div className="space-y-2 border border-slate-200 rounded-2xl p-3 bg-slate-50">
                  {orderItems.map((item) => (
                    <div key={item.id} className="flex items-center justify-between py-2 border-b border-slate-200 last:border-none text-xs">
                      <div>
                        <p className="font-bold text-slate-900">{item.name_snapshot}</p>
                        <p className="text-slate-500">Qty: {item.qty_base} {item.unit_label_snapshot}</p>
                      </div>
                      <p className="font-extrabold text-slate-900">{formatRupees(item.line_total_paise || 0)}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Modal Actions */}
            <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
              {selectedOrder.status === "pending_review" && (
                <button
                  onClick={() => updateOrderStatus(selectedOrder.id, "accepted")}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-xl"
                >
                  Accept Order
                </button>
              )}
              {selectedOrder.status === "out_for_delivery" && (
                <button
                  onClick={() => updateOrderStatus(selectedOrder.id, "delivered")}
                  className="px-4 py-2 bg-green-700 hover:bg-green-800 text-white text-xs font-bold rounded-xl"
                >
                  Mark Delivered ✓
                </button>
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