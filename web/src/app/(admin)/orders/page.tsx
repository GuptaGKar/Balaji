"use client";

import React from "react";
import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { formatRupees } from "@/lib/money";
import { formatRelativeTime, formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { Loader2, Search, Eye, X } from "lucide-react";
import { Toaster, toast } from "sonner";

type Order = {
  id: string;
  public_order_number: string;
  status: string;
  grand_total_paise: number;
  created_at: string;
  customer_id: string;
  payment_method: string;
  cod_collected_paise: number | null;
  address_snapshot: any;
  customer_note: string | null;
  fee_breakdown: any;
  item_subtotal_paise: number;
  discount_paise: number;
  delivery_fee_paise: number;
  tax_paise: number;
};

type OrderItem = {
  id: string;
  sku_snapshot: string;
  name_snapshot: string;
  quantity_type_snapshot: string;
  unit_label_snapshot: string;
  price_basis_qty_snapshot: number;
  qty_base: number;
  unit_price_paise: number;
  line_total_paise: number;
  removed_at: string | null;
  removed_reason: string | null;
};

type OrderEvent = {
  id: string;
  from_status: string | null;
  to_status: string;
  actor_id: string | null;
  reason: string | null;
  created_at: string;
};

type Profile = {
  id: string;
  display_name: string;
  role: string;
  login_id: string | null;
  is_active: boolean;
};

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [orderItems, setOrderItems] = useState<OrderItem[]>([]);
  const [orderEvents, setOrderEvents] = useState<OrderEvent[]>([]);
  const [riders, setRiders] = useState<Profile[]>([]);
  const [modalLoading, setModalLoading] = useState(false);
  const [transitionLoading, setTransitionLoading] = useState<string | null>(null);
  const [assignLoading, setAssignLoading] = useState<string | null>(null);
  const [realtimeChannel, setRealtimeChannel] = useState<any>(null);

  const supabase = createClient();

  const fetchOrders = useCallback(async () => {
    let query = supabase.from("orders").select("*").order("created_at", { ascending: false });
    if (statusFilter !== "all") query = query.eq("status", statusFilter);
    const { data, error } = await query;
    if (error) {
      toast.error("Failed to load orders");
      return;
    }
    setOrders(data || []);
    setLoading(false);
  }, [statusFilter, supabase]);

  const fetchRiders = useCallback(async () => {
    const { data, error } = await supabase
      .from("profiles")
      .select("id, display_name, login_id, is_active, role")
      .eq("role", "delivery")
      .eq("is_active", true);
    if (!error) setRiders(data || []);
  }, [supabase]);

  const fetchOrderDetails = useCallback(
    async (orderId: string) => {
      setModalLoading(true);
      try {
        const [itemsRes, eventsRes] = await Promise.all([
          supabase.from("order_items").select("*").eq("order_id", orderId),
          supabase
            .from("order_status_events")
            .select("*")
            .eq("order_id", orderId)
            .order("created_at", { ascending: true }),
        ]);
        if (!itemsRes.error) setOrderItems(itemsRes.data || []);
        if (!eventsRes.error) setOrderEvents(eventsRes.data || []);
      } finally {
        setModalLoading(false);
      }
    },
    [supabase],
  );

  const handleTransition = async (orderId: string, toStatus: string, reason: string) => {
    setTransitionLoading(orderId);
    try {
      const { error } = await supabase.rpc("transition_order", {
        p_order_id: orderId,
        p_to_status: toStatus,
        p_reason: reason,
      });
      if (error) throw error;
      toast.success(`Order ${toStatus.replace("_", " ")}`);
      await fetchOrderDetails(orderId);
      fetchOrders();
    } catch (err: any) {
      toast.error(err.message || "Transition failed");
    } finally {
      setTransitionLoading(null);
    }
  };

  const handleAssign = async (orderId: string, riderId: string) => {
    setAssignLoading(orderId);
    try {
      const { error } = await supabase.rpc("assign_order", {
        p_order_id: orderId,
        p_delivery_user_id: riderId,
      });
      if (error) throw error;
      toast.success("Rider assigned");
      fetchOrders();
    } catch (err: any) {
      toast.error(err.message || "Assignment failed");
    } finally {
      setAssignLoading(null);
    }
  };

  const handleReject = async (orderId: string) => {
    const reason = prompt("Reason for rejection (required for stock restore):");
    if (!reason) return;
    await handleTransition(orderId, "rejected", reason);
  };

  const handleCancel = async (orderId: string) => {
    const reason = prompt("Reason for cancellation (stock will be restored):");
    if (!reason) return;
    await handleTransition(orderId, "cancelled", reason);
  };

  const openOrder = (order: Order) => {
    setSelectedOrder(order);
    fetchOrderDetails(order.id);
  };

  // Realtime subscription for new orders
  useEffect(() => {
    const channel = supabase
      .channel("orders_realtime")
      .on("postgres_changes", { event: "INSERT", schema: "public", table: "orders" }, (payload) => {
        const newOrder = payload.new as Order;
        if (statusFilter === "all" || newOrder.status === statusFilter) {
          setOrders((prev) => [newOrder, ...prev]);
        }
        toast.info(`New order: ${newOrder.public_order_number}`);
      })
      .subscribe();

    setRealtimeChannel(channel);
    return () => {
      supabase.removeChannel(channel);
    };
  }, [statusFilter, supabase]);

  useEffect(() => {
    fetchOrders();
    fetchRiders();
  }, [fetchOrders, fetchRiders]);

  useEffect(() => {
    if (selectedOrder) fetchOrderDetails(selectedOrder.id);
  }, [selectedOrder, fetchOrderDetails]);

  const filteredOrders = orders.filter((o) =>
    o.public_order_number.toLowerCase().includes(search.toLowerCase()),
  );

  const statusColors: Record<string, string> = {
    pending_review: "bg-yellow-100 text-yellow-800",
    accepted: "bg-blue-100 text-blue-800",
    preparing: "bg-purple-100 text-purple-800",
    ready_for_dispatch: "bg-indigo-100 text-indigo-800",
    out_for_delivery: "bg-orange-100 text-orange-800",
    delivered: "bg-green-100 text-green-800",
    cancelled: "bg-gray-100 text-gray-800",
    rejected: "bg-red-100 text-red-800",
    delivery_failed: "bg-red-100 text-red-800",
  };

  const getAllowedTransitions = (currentStatus: string) => {
    const transitions: Record<string, { to: string; label: string; requiresReason: boolean }[]> = {
      pending_review: [
        { to: "accepted", label: "Accept", requiresReason: false },
        { to: "rejected", label: "Reject", requiresReason: true },
        { to: "cancelled", label: "Cancel", requiresReason: true },
      ],
      accepted: [
        { to: "preparing", label: "Start Preparing", requiresReason: false },
        { to: "cancelled", label: "Cancel", requiresReason: true },
      ],
      preparing: [
        { to: "ready_for_dispatch", label: "Ready for Dispatch", requiresReason: false },
        { to: "cancelled", label: "Cancel", requiresReason: true },
      ],
      ready_for_dispatch: [
        { to: "out_for_delivery", label: "Dispatch", requiresReason: false },
        { to: "cancelled", label: "Cancel", requiresReason: true },
      ],
      out_for_delivery: [
        { to: "delivered", label: "Mark Delivered", requiresReason: false },
        { to: "delivery_failed", label: "Delivery Failed", requiresReason: true },
      ],
    };
    return transitions[currentStatus] || [];
  };

  if (loading)
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
      </div>
    );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Orders</h1>
          <p className="text-gray-500">Manage customer orders</p>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
        <div className="flex flex-col sm:flex-row gap-4">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search order #..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
          >
            {[
              { value: "all", label: "All Statuses" },
              { value: "pending_review", label: "Pending Review" },
              { value: "accepted", label: "Accepted" },
              { value: "preparing", label: "Preparing" },
              { value: "ready_for_dispatch", label: "Ready for Dispatch" },
              { value: "out_for_delivery", label: "Out for Delivery" },
              { value: "delivered", label: "Delivered" },
              { value: "cancelled", label: "Cancelled" },
              { value: "rejected", label: "Rejected" },
              { value: "delivery_failed", label: "Delivery Failed" },
            ].map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Order #
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Payment
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Total
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  COD Collected
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Time
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {filteredOrders.map((order) => (
                <tr key={order.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 font-medium text-gray-900">
                    {order.public_order_number}
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={cn(
                        "inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                        statusColors[order.status] || "bg-gray-100 text-gray-800",
                      )}
                    >
                      {order.status.replace("_", " ")}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={cn(
                        "inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                        order.payment_method === "cod"
                          ? "bg-green-100 text-green-800"
                          : "bg-blue-100 text-blue-800",
                      )}
                    >
                      {order.payment_method.toUpperCase()}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-gray-900">
                    {formatRupees(order.grand_total_paise)}
                  </td>
                  <td className="px-6 py-4">
                    {order.cod_collected_paise !== null ? (
                      <span
                        className={cn(
                          "inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                          order.cod_collected_paise >= order.grand_total_paise
                            ? "bg-green-100 text-green-800"
                            : "bg-yellow-100 text-yellow-800",
                        )}
                      >
                        {formatRupees(order.cod_collected_paise)}
                      </span>
                    ) : (
                      <span className="text-gray-400">—</span>
                    )}
                  </td>
                  <td className="px-6 py-4 text-gray-500">
                    {formatRelativeTime(order.created_at)}
                  </td>
                  <td className="px-6 py-4 text-right">
                    <button
                      onClick={() => openOrder(order)}
                      className="p-2 text-gray-500 hover:text-indigo-600 hover:bg-indigo-50 rounded"
                      aria-label="View"
                    >
                      <Eye className="h-4 w-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {filteredOrders.length === 0 && (
          <div className="p-12 text-center text-gray-500">No orders found</div>
        )}
      </div>

      {/* Order Detail Modal */}
      {selectedOrder && (
        <div className="fixed inset-0 z-50 overflow-y-auto" role="dialog" aria-modal="true">
          <div className="flex min-h-full items-center justify-center p-4">
            <div className="fixed inset-0 bg-black/50" onClick={() => setSelectedOrder(null)} />
            <div className="relative w-full max-w-4xl bg-white rounded-lg shadow-xl max-h-[90vh] overflow-y-auto">
              <div className="flex items-center justify-between px-6 py-4 border-b">
                <h2 className="text-xl font-bold">Order #{selectedOrder.public_order_number}</h2>
                <button
                  onClick={() => setSelectedOrder(null)}
                  className="p-2 text-gray-500 hover:text-gray-700"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {modalLoading ? (
                <div className="flex items-center justify-center h-64">
                  <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
                </div>
              ) : (
                <div className="p-6 space-y-6">
                  {/* Order Header */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <h3 className="font-semibold text-gray-900">Customer Info</h3>
                      <p className="text-sm text-gray-500">
                        {selectedOrder.address_snapshot?.recipient_name || "N/A"}
                      </p>
                      <p className="text-sm text-gray-500">
                        {selectedOrder.address_snapshot?.phone || "N/A"}
                      </p>
                      <p className="text-sm text-gray-500">
                        {selectedOrder.address_snapshot?.address_lines || ""},{" "}
                        {selectedOrder.address_snapshot?.area || ""},{" "}
                        {selectedOrder.address_snapshot?.city || ""} -{" "}
                        {selectedOrder.address_snapshot?.postal_code || ""}
                      </p>
                    </div>
                    <div>
                      <h3 className="font-semibold text-gray-900">Payment</h3>
                      <p className="text-sm text-gray-500">
                        Method: {selectedOrder.payment_method.toUpperCase()}
                      </p>
                      <p className="text-sm text-gray-500">
                        Total: {formatRupees(selectedOrder.grand_total_paise)}
                      </p>
                      <p className="text-sm text-gray-500">
                        COD Collected:{" "}
                        {selectedOrder.cod_collected_paise !== null
                          ? formatRupees(selectedOrder.cod_collected_paise)
                          : "—"}
                      </p>
                    </div>
                  </div>

                  {/* Items */}
                  <div>
                    <h3 className="font-semibold text-gray-900 mb-2">Items</h3>
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead className="bg-gray-50">
                          <tr>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                              Item
                            </th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                              Qty
                            </th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                              Unit Price
                            </th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                              Total
                            </th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-200">
                          {orderItems.map((item) => (
                            <tr
                              key={item.id}
                              className={item.removed_at ? "line-through text-gray-400" : ""}
                            >
                              <td className="px-4 py-2">{item.name_snapshot}</td>
                              <td className="px-4 py-2">
                                {item.qty_base} {item.unit_label_snapshot}
                              </td>
                              <td className="px-4 py-2">{formatRupees(item.unit_price_paise)}</td>
                              <td className="px-4 py-2">{formatRupees(item.line_total_paise)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>

                  {/* Fee Breakdown */}
                  {selectedOrder.fee_breakdown && (
                    <div className="bg-gray-50 rounded-lg p-4">
                      <h4 className="font-semibold mb-2">Fee Breakdown</h4>
                      <div className="grid grid-cols-2 gap-2 text-sm">
                        <div>
                          Eligible Subtotal:{" "}
                          <span className="font-medium">
                            {formatRupees(selectedOrder.fee_breakdown.eligible_subtotal_paise)}
                          </span>
                        </div>
                        <div>
                          Delivery Fee:{" "}
                          <span className="font-medium">
                            {formatRupees(selectedOrder.delivery_fee_paise)}
                          </span>
                        </div>
                        <div>
                          Threshold:{" "}
                          <span className="font-medium">
                            {formatRupees(
                              selectedOrder.fee_breakdown.delivery_charge_threshold_paise || 0,
                            )}
                          </span>
                        </div>
                        <div>
                          Rule:{" "}
                          <span className="font-medium">
                            {selectedOrder.fee_breakdown.rule_note || "N/A"}
                          </span>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Status Transitions */}
                  <div>
                    <h3 className="font-semibold text-gray-900 mb-3">Status Actions</h3>
                    <div className="flex flex-wrap gap-2">
                      {getAllowedTransitions(selectedOrder.status).map((t) => (
                        <button
                          key={t.to}
                          onClick={() => {
                            const reason = t.requiresReason
                              ? prompt(`Reason for ${t.label}:`)
                              : undefined;
                            if (t.requiresReason && !reason) return;
                            handleTransition(selectedOrder.id, t.to, reason || "");
                          }}
                          disabled={transitionLoading === selectedOrder.id}
                          className={cn(
                            "px-3 py-1.5 text-sm font-medium rounded-lg transition-colors",
                            transitionLoading === selectedOrder.id
                              ? "opacity-50 cursor-not-allowed"
                              : t.to === "cancelled" ||
                                  t.to === "rejected" ||
                                  t.to === "delivery_failed"
                                ? "bg-red-100 text-red-700 hover:bg-red-200"
                                : "bg-indigo-100 text-indigo-700 hover:bg-indigo-200",
                          )}
                        >
                          {t.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Assign Rider */}
                  {["ready_for_dispatch", "out_for_delivery"].includes(selectedOrder.status) && (
                    <div>
                      <h3 className="font-semibold text-gray-900 mb-2">Assign Rider</h3>
                      <div className="flex gap-2">
                        <select
                          className="flex-1 px-3 py-2 border border-gray-300 rounded-lg"
                          onChange={(e) => handleAssign(selectedOrder.id, e.target.value)}
                          disabled={assignLoading === selectedOrder.id}
                        >
                          <option value="">Select Rider</option>
                          {riders
                            .filter((r) => r.is_active)
                            .map((r) => (
                              <option key={r.id} value={r.id}>
                                {r.display_name} ({r.login_id})
                              </option>
                            ))}
                        </select>
                        <button
                          onClick={() => handleAssign(selectedOrder.id, "")}
                          disabled={assignLoading === selectedOrder.id}
                          className="px-3 py-2 bg-indigo-600 text-white rounded-lg"
                        >
                          Assign
                        </button>
                      </div>
                    </div>
                  )}

                  {/* Event Timeline */}
                  <div>
                    <h3 className="font-semibold text-gray-900 mb-3">Event Timeline</h3>
                    <div className="space-y-3">
                      {orderEvents.map((event, idx) => (
                        <div key={event.id} className="flex items-start gap-3">
                          <div className="relative flex-shrink-0">
                            <div className="w-3 h-3 bg-indigo-600 rounded-full" />
                            {idx < orderEvents.length - 1 && (
                              <div className="absolute left-1.5 top-6 bottom-0 w-0.5 bg-gray-200" />
                            )}
                          </div>
                          <div className="flex-1">
                            <div className="flex items-center gap-2">
                              <span className="font-medium text-gray-900">
                                {event.to_status.replace("_", " ")}
                              </span>
                              <span className="text-xs text-gray-500">
                                {formatRelativeTime(event.created_at)}
                              </span>
                            </div>
                            {event.reason && (
                              <p className="text-sm text-gray-500 mt-1">Reason: {event.reason}</p>
                            )}
                            {event.actor_id && (
                              <p className="text-xs text-gray-400">By: {event.actor_id}</p>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Customer Note */}
                  {selectedOrder.customer_note && (
                    <div className="bg-gray-50 rounded-lg p-4">
                      <h4 className="font-semibold mb-1">Customer Note</h4>
                      <p className="text-gray-700">{selectedOrder.customer_note}</p>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      <Toaster position="top-right" />
    </div>
  );
}
