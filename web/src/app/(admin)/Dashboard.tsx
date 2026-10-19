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

        // 1. Fetch exact server-side statistics via get_dashboard_stats RPC
        const { data: statsData, error: statsError } = await supabase.rpc("get_dashboard_stats");
        if (statsError) {
          // Fallback to table count if RPC not yet created
          const { count } = await supabase.from("products").select("id", { count: "exact", head: true });
          setStats({
            totalProducts: count || 0,
            lowStockCount: 0,
            activeOrders: 0,
            todayRevenue: 0,
          });
        } else if (statsData) {
          setStats({
            totalProducts: Number(statsData.total_products || 0),
            lowStockCount: Number(statsData.low_stock_count || 0),
            activeOrders: Number(statsData.active_orders || 0),
            todayRevenue: Number(statsData.today_revenue_paise || 0),
          });
        }

        // 2. Fetch recent orders
        const { data: ordersData, error: ordersError } = await supabase
          .from("orders")
          .select("id, public_order_number, status, grand_total_paise, created_at")
          .order("created_at", { ascending: false })
          .limit(10);

        if (ordersError) throw new Error(`${ordersError.code}: ${ordersError.message}`);
        setRecentOrders(ordersData || []);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load dashboard data");
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  const statusColors: Record<string, string> = {
    pending_review: "bg-amber-100 text-amber-800",
    accepted: "bg-blue-100 text-blue-800",
    preparing: "bg-purple-100 text-purple-800",
    ready_for_dispatch: "bg-indigo-100 text-indigo-800",
    out_for_delivery: "bg-emerald-100 text-emerald-800",
    delivered: "bg-green-100 text-green-800",
    cancelled: "bg-slate-100 text-slate-800",
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
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12 bg-white rounded-2xl border border-red-200 p-6">
        <AlertTriangle className="h-12 w-12 text-red-500 mx-auto mb-4" />
        <p className="text-red-600 font-bold">{error}</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900">Dashboard</h1>
          <p className="text-xs font-medium text-slate-500">Live store operations summary & today&apos;s activity</p>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Products"
          value={stats?.totalProducts.toLocaleString() || "0"}
          icon={<Package className="h-6 w-6 text-white" />}
          color="bg-emerald-600"
        />
        <StatCard
          title="Low Stock Items"
          value={stats?.lowStockCount.toLocaleString() || "0"}
          icon={<AlertTriangle className="h-6 w-6 text-white" />}
          color="bg-amber-500"
        />
        <StatCard
          title="Active Orders"
          value={stats?.activeOrders.toLocaleString() || "0"}
          icon={<ShoppingCart className="h-6 w-6 text-white" />}
          color="bg-blue-600"
        />
        <StatCard
          title="Today's Revenue"
          value={formatRupees(stats?.todayRevenue || 0)}
          icon={<TrendingUp className="h-6 w-6 text-white" />}
          color="bg-green-600"
        />
      </div>

      {/* Recent Orders */}
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="px-6 py-4 border-b border-slate-200">
          <h2 className="text-lg font-bold text-slate-900">Recent Store Orders</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-slate-50 border-b border-slate-200 text-xs font-bold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="px-6 py-3.5">Order #</th>
                <th className="px-6 py-3.5">Status</th>
                <th className="px-6 py-3.5">Total Amount</th>
                <th className="px-6 py-3.5">Time</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 text-sm text-slate-700">
              {recentOrders.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-6 py-8 text-center text-slate-400 font-medium">
                    No recent orders
                  </td>
                </tr>
              ) : (
                recentOrders.map((order) => (
                  <tr key={order.id} className="hover:bg-slate-50/80 transition-colors">
                    <td className="px-6 py-4 font-extrabold text-slate-900 font-mono">
                      #{order.public_order_number}
                    </td>
                    <td className="px-6 py-4">
                      <span
                        className={cn(
                          "inline-flex items-center gap-1 px-2.5 py-0.5 text-xs font-bold rounded-full border",
                          statusColors[order.status] || "bg-slate-100 text-slate-800 border-slate-300",
                        )}
                      >
                        {statusIcons[order.status]}
                        {order.status.replace("_", " ").toUpperCase()}
                      </span>
                    </td>
                    <td className="px-6 py-4 font-bold text-emerald-700">
                      {formatRupees(order.grand_total_paise)}
                    </td>
                    <td className="px-6 py-4 text-xs font-medium text-slate-500">
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
    <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-xs font-bold text-slate-500 uppercase tracking-wider">{title}</p>
          <p className="mt-1 text-2xl font-extrabold text-slate-900">{value}</p>
        </div>
        <div className={cn("p-3 rounded-2xl shadow-md", color)}>{icon}</div>
      </div>
    </div>
  );
}