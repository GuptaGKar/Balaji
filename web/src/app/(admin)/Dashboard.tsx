"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { formatRupees } from "@/lib/money";
import { formatRelativeTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  Package,
  ShoppingCart,
  TrendingUp,
  AlertTriangle,
  Clock,
  CheckCircle,
  XCircle,
  Loader2,
} from "lucide-react";

interface Stats {
  totalProducts: number;
  lowStockCount: number;
  activeOrders: number;
  todayRevenue: number;
}

interface RecentOrder {
  id: string;
  public_order_number: string;
  status: string;
  grand_total_paise: number;
  created_at: string;
}

export default function Dashboard() {
  const [stats, setStats] = useState<Stats | null>(null);
  const [recentOrders, setRecentOrders] = useState<RecentOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const fetchData = async () => {
      try {
        const supabase = createClient();

        // Fetch stats
        const [productsRes, ordersRes] = await Promise.all([
          supabase
            .from("catalog_products")
            .select("id, stock_qty, low_stock_threshold, active", { count: "exact" }),
          supabase
            .from("orders")
            .select("id, public_order_number, status, grand_total_paise, created_at, customer_id")
            .gte("created_at", new Date().toISOString().split("T")[0])
            .order("created_at", { ascending: false }),
        ]);

        if (productsRes.error) throw productsRes.error;
        if (ordersRes.error) throw ordersRes.error;

        const products = productsRes.data || [];
        const lowStock = products.filter(
          (p) => p.active && p.stock_qty > 0 && p.stock_qty <= p.low_stock_threshold,
        ).length;

        const orders = ordersRes.data || [];
        const todayRevenue = orders
          .filter((o) => o.status !== "cancelled" && o.status !== "rejected")
          .reduce((sum, o) => sum + (o.grand_total_paise || 0), 0);

        const activeOrders = orders.filter(
          (o) => !["delivered", "cancelled", "rejected", "delivery_failed"].includes(o.status),
        ).length;

        setStats({
          totalProducts: productsRes.count || 0,
          lowStockCount: lowStock,
          activeOrders,
          todayRevenue,
        });

        // Fetch recent orders with customer info
        const recentOrdersRes = await supabase
          .from("orders")
          .select("id, public_order_number, status, grand_total_paise, created_at, customer_id")
          .order("created_at", { ascending: false })
          .limit(5);

        if (recentOrdersRes.error) throw recentOrdersRes.error;

        setRecentOrders(recentOrdersRes.data || []);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load dashboard");
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

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

  const statusIcons: Record<string, React.ReactNode> = {
    pending_review: <Clock className="h-3.5 w-3.5" />,
    accepted: <CheckCircle className="h-3.5 w-3.5" />,
    preparing: <Package className="h-3.5 w-3.5" />,
    ready_for_dispatch: <ShoppingCart className="h-3.5 w-3.5" />,
    out_for_delivery: <TrendingUp className="h-3.5 w-3.5" />,
    delivered: <CheckCircle className="h-3.5 w-3.5" />,
    cancelled: <XCircle className="h-3.5 w-3.5" />,
    rejected: <XCircle className="h-3.5 w-3.5" />,
    delivery_failed: <AlertTriangle className="h-3.5 w-3.5" />,
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12">
        <AlertTriangle className="h-12 w-12 text-red-500 mx-auto mb-4" />
        <p className="text-red-600">{error}</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-500">Welcome back! Here&apos;s what&apos;s happening today.</p>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Products"
          value={stats?.totalProducts.toLocaleString() || "0"}
          icon={<Package className="h-6 w-6" />}
          color="bg-blue-500"
        />
        <StatCard
          title="Low Stock Items"
          value={stats?.lowStockCount.toLocaleString() || "0"}
          icon={<AlertTriangle className="h-6 w-6" />}
          color="bg-yellow-500"
        />
        <StatCard
          title="Active Orders"
          value={stats?.activeOrders.toLocaleString() || "0"}
          icon={<ShoppingCart className="h-6 w-6" />}
          color="bg-indigo-500"
        />
        <StatCard
          title="Today's Revenue"
          value={formatRupees(stats?.todayRevenue || 0)}
          icon={<TrendingUp className="h-6 w-6" />}
          color="bg-green-500"
        />
      </div>

      {/* Recent Orders */}
      <div className="bg-white rounded-lg border border-gray-200">
        <div className="px-6 py-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">Recent Orders</h2>
        </div>
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
                  Total
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Time
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {recentOrders.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-6 py-8 text-center text-gray-500">
                    No orders yet
                  </td>
                </tr>
              ) : (
                recentOrders.map((order) => (
                  <tr key={order.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 text-sm font-medium text-gray-900">
                      {order.public_order_number}
                    </td>
                    <td className="px-6 py-4">
                      <span
                        className={cn(
                          "inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-full",
                          statusColors[order.status] || "bg-gray-100 text-gray-800",
                        )}
                      >
                        {statusIcons[order.status]}
                        {order.status.replace("_", " ")}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-900">
                      {formatRupees(order.grand_total_paise)}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-500">
                      {formatRelativeTime(order.created_at)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function StatCard({
  title,
  value,
  icon,
  color,
}: {
  title: string;
  value: string;
  icon: React.ReactNode;
  color: string;
}) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-gray-500">{title}</p>
          <p className="mt-1 text-2xl font-bold text-gray-900">{value}</p>
        </div>
        <div className={cn("p-3 rounded-full", color)}>{icon}</div>
      </div>
    </div>
  );
}
