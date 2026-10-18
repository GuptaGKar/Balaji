"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { formatRelativeTime } from "@/lib/format";
import { formatRupees } from "@/lib/money";
import { cn } from "@/lib/utils";
import {
  Loader2,
  MapPin,
  Phone,
  AlertCircle,
  CheckCircle,
  Truck,
  Clock,
  RefreshCw,
} from "lucide-react";
import { Toaster, toast } from "sonner";

type Order = {
  id: string;
  public_order_number: string;
  address_snapshot: any;
  payment_method: string;
  cod_collected_paise: number | null;
  grand_total_paise: number;
  status: string;
  customer_note: string;
  created_at: string;
  items: any[];
};

export default function RiderPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const supabase = createClient();

  const fetchOrders = async () => {
    try {
      const { data, error } = await supabase.rpc("get_rider_orders");
      if (error) throw error;
      setOrders(data || []);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchOrders();
    // Poll every 15 seconds as fallback
    const interval = setInterval(fetchOrders, 15000);
    return () => clearInterval(interval);
  }, []);

  const handleStatusUpdate = async (orderId: string, newStatus: string, note?: string) => {
    try {
      const { data, error } = await supabase.rpc("rider_update_status", {
        p_order_id: orderId,
        p_to_status: newStatus,
        p_note: note || null,
      });
      if (error) throw error;
      if (data.error) throw new Error(data.error.message);
      toast.success(`Order ${newStatus.replace("_", " ")}`);
      fetchOrders();
    } catch (err: any) {
      toast.error(err.message);
    }
  };

  const handleRefresh = () => {
    setRefreshing(true);
    fetchOrders();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
      </div>
    );
  }

  if (error && orders.length === 0) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-center">
          <AlertCircle className="h-12 w-12 text-red-500 mx-auto mb-4" />
          <p className="text-red-600">{error}</p>
          <button
            onClick={fetchOrders}
            className="mt-4 px-4 py-2 bg-indigo-600 text-white rounded-lg"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="sticky top-0 z-40 bg-white border-b border-gray-200">
        <div className="flex items-center justify-between h-16 px-4">
          <h1 className="text-xl font-bold text-gray-900">Rider Dashboard</h1>
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="p-2 text-gray-500 hover:text-gray-700"
          >
            <RefreshCw className={cn("h-5 w-5", refreshing && "animate-spin")} />
          </button>
        </div>
      </header>

      {/* Orders List */}
      <main className="p-4 space-y-4">
        {orders.length === 0 ? (
          <div className="text-center py-12">
            <Truck className="h-12 w-12 text-gray-400 mx-auto mb-4" />
            <p className="text-gray-500">No assigned deliveries</p>
            <p className="text-sm text-gray-400 mt-1">Orders will appear here when assigned</p>
          </div>
        ) : (
          <div className="space-y-4">
            {orders.map((order) => (
              <OrderCard key={order.id} order={order} onStatusUpdate={handleStatusUpdate} />
            ))}
          </div>
        )}
      </main>

      <Toaster position="top-right" />
    </div>
  );
}

function OrderCard({
  order,
  onStatusUpdate,
}: {
  order: Order;
  onStatusUpdate: (id: string, status: string, note?: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const [updating, setUpdating] = useState<string | null>(null);

  const statusColors: Record<string, string> = {
    ready_for_dispatch: "bg-indigo-100 text-indigo-800",
    out_for_delivery: "bg-orange-100 text-orange-800",
    delivered: "bg-green-100 text-green-800",
    delivery_failed: "bg-red-100 text-red-800",
  };

  const statusIcons: Record<string, React.ReactNode> = {
    ready_for_dispatch: <Truck className="h-3.5 w-3.5" />,
    out_for_delivery: <Truck className="h-3.5 w-3.5" />,
    delivered: <CheckCircle className="h-3.5 w-3.5" />,
    delivery_failed: <AlertCircle className="h-3.5 w-3.5" />,
  };

  const getNextActions = (status: string) => {
    switch (status) {
      case "ready_for_dispatch":
        return [{ status: "out_for_delivery", label: "Start Delivery", color: "bg-orange-600" }];
      case "out_for_delivery":
        return [
          { status: "delivered", label: "Mark Delivered", color: "bg-green-600" },
          { status: "delivery_failed", label: "Delivery Failed", color: "bg-red-600" },
        ];
      default:
        return [];
    }
  };

  return (
    <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
      {/* Header */}
      <div className="p-4 border-b border-gray-100">
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <span className="font-bold text-gray-900">#{order.public_order_number}</span>
              <span
                className={cn(
                  "inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                  statusColors[order.status] || "bg-gray-100 text-gray-800",
                )}
              >
                {statusIcons[order.status]}
                {order.status.replace("_", " ")}
              </span>
            </div>
            <p className="text-sm text-gray-500 mt-1">
              {formatRelativeTime(order.created_at)} • {formatRupees(order.grand_total_paise)}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {order.payment_method === "cod" && order.cod_collected_paise !== null && (
              <span className="px-2 py-1 text-xs font-medium rounded-full bg-yellow-100 text-yellow-800">
                COD: {formatRupees(order.cod_collected_paise)}
              </span>
            )}
          </div>
        </div>

        {/* Customer Info */}
        <div className="p-4 border-b border-gray-100">
          <div className="flex items-start gap-3">
            <MapPin className="h-5 w-5 text-gray-400 mt-0.5" />
            <div className="flex-1">
              <p className="font-medium text-gray-900">
                {order.address_snapshot?.recipient_name || "Customer"}
              </p>
              <p className="text-sm text-gray-500">{order.address_snapshot?.phone}</p>
              <p className="text-sm text-gray-600 mt-1">
                {order.address_snapshot?.address_lines}
                {order.address_snapshot?.landmark && `, ${order.address_snapshot.landmark}`}
                {order.address_snapshot?.area && `, ${order.address_snapshot.area}`}
                {order.address_snapshot?.city && `, ${order.address_snapshot.city}`}-{" "}
                {order.address_snapshot?.postal_code}
              </p>
            </div>
            <Phone className="h-5 w-5 text-indigo-600 mt-0.5" />
          </div>
        </div>

        {/* Items */}
        <div className="px-4 py-3 border-b border-gray-100">
          <p className="font-medium text-gray-900 mb-2">Items</p>
          <div className="space-y-2">
            {order.items?.map((item: any) => (
              <div key={item.id} className="flex items-center justify-between text-sm">
                <div className="flex-1">
                  <p className="font-medium">{item.name}</p>
                  <p className="text-gray-500">
                    {item.qty} x {item.unit} @ {item.unitPrice}
                  </p>
                </div>
                <span className="font-medium">{formatRupees(item.total)}</span>
              </div>
            ))}
          </div>
        </div>

        {/* COD Amount */}
        {order.payment_method === "cod" && (
          <div className="px-4 py-3 bg-gray-50 border-b border-gray-100">
            <div className="flex items-center justify-between">
              <span className="font-medium text-gray-900">COD to Collect</span>
              <span className="text-lg font-bold text-green-700">
                {formatRupees(order.cod_collected_paise || order.grand_total_paise)}
              </span>
            </div>
          </div>
        )}

        {/* Customer Note */}
        {order.customer_note && (
          <div className="px-4 py-3 bg-blue-50 border-b border-gray-100">
            <p className="font-medium text-blue-900 mb-1">Customer Note</p>
            <p className="text-sm text-blue-800">{order.customer_note}</p>
          </div>
        )}

        {/* Actions */}
        <div className="p-4 space-y-2">
          {getNextActions(order.status).map((action) => (
            <button
              key={action.status}
              onClick={() => onStatusUpdate(order.id, action.status)}
              disabled={updating === action.status}
              className={cn(
                "w-full py-2.5 px-4 rounded-lg font-medium transition-colors",
                action.color,
                "text-white hover:opacity-90 disabled:opacity-50",
              )}
            >
              {action.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

const statusColors: Record<string, string> = {
  ready_for_dispatch: "bg-indigo-100 text-indigo-800",
  out_for_delivery: "bg-orange-100 text-orange-800",
  delivered: "bg-green-100 text-green-800",
  delivery_failed: "bg-red-100 text-red-800",
};

const statusIcons: Record<string, React.ReactNode> = {
  ready_for_dispatch: <Truck className="h-3.5 w-3.5" />,
  out_for_delivery: <Truck className="h-3.5 w-3.5" />,
  delivered: <CheckCircle className="h-3.5 w-3.5" />,
  delivery_failed: <AlertCircle className="h-3.5 w-3.5" />,
};
