"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { formatRupees } from "@/lib/money";
import { formatRelativeTime, formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import { Loader2, Calendar, DollarSign, TrendingUp, Download, RefreshCw, AlertTriangle, CheckCircle } from "lucide-react";
import { Toaster, toast } from "sonner";

type SalesReport = {
  total_orders: number;
  total_revenue_paise: number;
  total_cod_paise: number;
  total_online_paise: number;
  avg_order_value_paise: number;
  cod_collected_paise: number;
  cod_pending_paise: number;
  by_status: Record<string, number>;
  by_payment_method: Record<string, number>;
  daily_breakdown: Array<{
    date: string;
    orders: number;
    revenue_paise: number;
  }>;
};

type CODReconciliation = {
  total_cod_orders: number;
  total_cod_paise: number;
  collected_paise: number;
  pending_paise: number;
  by_rider: Record<string, {
    orders: number;
    total_paise: number;
    collected_paise: number;
    pending_paise: number;
  }>;
  daily_breakdown: Array<{
    date: string;
    orders: number;
    total_paise: number;
    collected_paise: number;
  }>;
};

type SalesReportParams = {
  start_date: string;
  end_date: string;
  status?: string;
};

type CODReconciliationParams = {
  start_date: string;
  end_date: string;
};

export default function ReportsPage() {
  const [salesReport, setSalesReport] = useState<SalesReport | null>(null);
  const [codReconciliation, setCODReconciliation] = useState<CODReconciliation | null>(null);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<"sales" | "cod">("sales");
  const [dateRange, setDateRange] = useState<{ start: string; end: string }>({
    start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
    end: new Date().toISOString().split("T")[0],
  });
  const [statusFilter, setStatusFilter] = useState<string>("");

  const supabase = createClient();

  const fetchSalesReport = async () => {
    const supabase = createClient();
    const { data, error } = await supabase.rpc("get_sales_report", {
      p_start_date: dateRange.start,
      p_end_date: dateRange.end,
      p_status: statusFilter || null,
    });
    if (error) { toast.error("Failed to load sales report"); return; }
    setSalesReport(data?.[0] || null);
  };

  const fetchCODReconciliation = async () => {
    const supabase = createClient();
    const { data, error } = await supabase.rpc("get_cod_reconciliation", {
      p_start_date: dateRange.start,
      p_end_date: dateRange.end,
    });
    if (error) { toast.error("Failed to load COD reconciliation"); return; }
    setCODReconciliation(data?.[0] || null);
  };

  const fetchReport = async () => {
    setLoading(true);
    if (activeTab === "sales") {
      await fetchSalesReport();
    } else {
      await fetchCODReconciliation();
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchReport();
  }, [activeTab, dateRange, statusFilter]);

  const handleDateChange = (field: "start" | "end", value: string) => {
    setDateRange(prev => ({ ...prev, [field]: value }));
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Reports</h1>
          <p className="text-gray-500">Sales analytics and COD reconciliation</p>
        </div>
      </div>

      {/* Date Range & Filters */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
        <div className="flex flex-col sm:flex-row gap-4">
          <div className="flex items-center gap-4">
            <label className="text-sm font-medium text-gray-700">From</label>
            <input
              type="date"
              value={dateRange.start}
              onChange={(e) => handleDateChange("start", e.target.value)}
              className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
            />
            <label className="text-sm font-medium text-gray-700">To</label>
            <input
              type="date"
              value={dateRange.end}
              onChange={(e) => handleDateChange("end", e.target.value)}
              className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          {activeTab === "sales" && (
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">All Statuses</option>
              <option value="pending_review">Pending Review</option>
              <option value="accepted">Accepted</option>
              <option value="preparing">Preparing</option>
              <option value="ready_for_dispatch">Ready for Dispatch</option>
              <option value="out_for_delivery">Out for Delivery</option>
              <option value="delivered">Delivered</option>
              <option value="cancelled">Cancelled</option>
              <option value="rejected">Rejected</option>
              <option value="delivery_failed">Delivery Failed</option>
            </select>
          )}
          <button
            onClick={fetchReport}
            disabled={loading}
            className="btn-primary"
          >
            <RefreshCw className={cn("h-4 w-4 mr-2", loading && "animate-spin")} />
            {loading ? "Loading..." : "Generate Report"}
          </button>
        </div>
      </div>

      {/* Tab Navigation */}
      <div className="bg-white rounded-lg border border-gray-200">
        <nav className="flex border-b border-gray-200" aria-label="Report tabs">
          <button
            onClick={() => setActiveTab("sales")}
            className={cn(
              "px-6 py-3 text-sm font-medium border-b-2 transition-colors",
              activeTab === "sales"
                ? "border-indigo-600 text-indigo-600"
                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
            )}
          >
            <DollarSign className="h-4 w-4 mr-2 inline" />
            Sales Report
          </button>
          <button
            onClick={() => setActiveTab("cod")}
            className={cn(
              "px-6 py-3 text-sm font-medium border-b-2 transition-colors",
              activeTab === "cod"
                ? "border-indigo-600 text-indigo-600"
                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
            )}
          >
            <TrendingUp className="h-4 w-4 mr-2 inline" />
            COD Reconciliation
          </button>
        </nav>

        <div className="p-6">
          {activeTab === "sales" && salesReport && (
            <SalesReportView report={salesReport} />
          )}

          {activeTab === "cod" && codReconciliation && (
            <CODReconciliationView reconciliation={codReconciliation} />
          )}

          {!salesReport && activeTab === "sales" && (
            <div className="text-center py-12 text-gray-500">
              No data for selected period
            </div>
          )}

          {!codReconciliation && activeTab === "cod" && (
            <div className="text-center py-12 text-gray-500">
              No COD data for selected period
            </div>
          )}
        </div>
      </div>

      <Toaster position="top-right" />
    </div>
  );
}

function SalesReportView({ report }: { report: SalesReport }) {
  const { total_orders, total_revenue_paise, total_cod_paise, total_online_paise, avg_order_value_paise, cod_collected_paise, cod_pending_paise, by_status, by_payment_method, daily_breakdown } = report;

  return (
    <div className="space-y-6">
      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard title="Total Orders" value={total_orders.toLocaleString()} icon={<TrendingUp className="h-6 w-6" />} color="bg-blue-500" />
        <StatCard title="Total Revenue" value={formatRupees(total_revenue_paise)} icon={<DollarSign className="h-6 w-6" />} color="bg-green-500" />
        <StatCard title="COD Orders" value={formatRupees(total_cod_paise)} icon={<TrendingUp className="h-6 w-6" />} color="bg-orange-500" />
        <StatCard title="Online Orders" value={formatRupees(total_online_paise)} icon={<TrendingUp className="h-6 w-6" />} color="bg-blue-500" />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard title="Avg Order Value" value={formatRupees(avg_order_value_paise)} icon={<DollarSign className="h-6 w-6" />} color="bg-purple-500" />
        <StatCard title="COD Collected" value={formatRupees(cod_collected_paise)} icon={<CheckCircle className="h-6 w-6" />} color="bg-green-500" />
        <StatCard title="COD Pending" value={formatRupees(cod_pending_paise)} icon={<AlertTriangle className="h-6 w-6" />} color="bg-yellow-500" />
        <StatCard title="Avg Order" value={formatRupees(avg_order_value_paise)} icon={<TrendingUp className="h-6 w-6" />} color="bg-indigo-500" />
      </div>

      {/* Status Breakdown */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Orders by Status</h3>
        <div className="flex flex-wrap gap-2">
          {Object.entries(report.by_status || {}).map(([status, count]) => (
            <span key={status} className={cn(
              "inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium",
              status === "delivered" ? "bg-green-100 text-green-800" :
              status === "pending_review" ? "bg-yellow-100 text-yellow-800" :
              status === "accepted" ? "bg-blue-100 text-blue-800" :
              status === "preparing" ? "bg-purple-100 text-purple-800" :
              status === "ready_for_dispatch" ? "bg-indigo-100 text-indigo-800" :
              status === "out_for_delivery" ? "bg-orange-100 text-orange-800" :
              status === "cancelled" ? "bg-gray-100 text-gray-800" :
              status === "rejected" ? "bg-red-100 text-red-800" :
              status === "delivery_failed" ? "bg-red-100 text-red-800" :
              "bg-gray-100 text-gray-800"
            )}>
              {status.replace("_", " ")}: {count}
            </span>
          ))}
        </div>
      </div>

      {/* Payment Method Breakdown */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Payment Methods</h3>
        <div className="flex flex-wrap gap-2">
          {Object.entries(report.by_payment_method || {}).map(([method, count]) => (
            <span key={method} className={cn(
              "inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium",
              method === "cod" ? "bg-green-100 text-green-800" : "bg-blue-100 text-blue-800"
            )}>
              {method.toUpperCase()}: {count}
            </span>
          ))}
        </div>
      </div>

      {/* Daily Breakdown Chart */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Daily Trend</h3>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Date</th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Orders</th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Revenue</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {report.daily_breakdown?.map((day) => (
                <tr key={day.date} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm text-gray-900">{formatDate(day.date)}</td>
                  <td className="px-4 py-3 text-right text-sm text-gray-900">{day.orders}</td>
                  <td className="px-4 py-3 text-right text-sm text-gray-900">{formatRupees(day.revenue_paise)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function CODReconciliationView({ reconciliation }: { reconciliation: CODReconciliation }) {
  const { total_cod_orders, total_cod_paise, collected_paise, pending_paise, by_rider, daily_breakdown } = reconciliation;

  return (
    <div className="space-y-6">
      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard title="Total COD Orders" value={total_cod_orders.toLocaleString()} icon={<TrendingUp className="h-6 w-6" />} color="bg-blue-500" />
        <StatCard title="Total COD Value" value={formatRupees(total_cod_paise)} icon={<DollarSign className="h-6 w-6" />} color="bg-green-500" />
        <StatCard title="Collected" value={formatRupees(collected_paise)} icon={<CheckCircle className="h-6 w-6" />} color="bg-green-500" />
        <StatCard title="Pending" value={formatRupees(pending_paise)} icon={<AlertTriangle className="h-6 w-6" />} color="bg-yellow-500" />
      </div>

      {/* By Rider */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">COD by Rider</h3>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Rider</th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Orders</th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Total Value</th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Collected</th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Pending</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {Object.entries(report.by_rider || {}).map(([rider, stats]) => (
                <tr key={rider} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-900">{rider}</td>
                  <td className="px-4 py-3 text-right text-gray-900">{stats.orders}</td>
                  <td className="px-4 py-3 text-right text-gray-900">{formatRupees(stats.total_paise)}</td>
                  <td className="px-4 py-3 text-right text-green-700">{formatRupees(stats.collected_paise)}</td>
                  <td className="px-4 py-3 text-right text-yellow-600">{formatRupees(stats.pending_paise)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Daily Breakdown */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Daily COD Breakdown</h3>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Date</th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Orders</th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Total</th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Collected</th>
                <th className="px-4 py-2 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Pending</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {report.daily_breakdown?.map((day) => (
                <tr key={day.date} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm text-gray-900">{formatDate(day.date)}</td>
                  <td className="px-4 py-3 text-right text-sm text-gray-900">{day.orders}</td>
                  <td className="px-4 py-3 text-right text-sm text-gray-900">{formatRupees(day.total_paise)}</td>
                  <td className="px-4 py-3 text-right text-green-700">{formatRupees(day.collected_paise)}</td>
                  <td className="px-4 py-3 text-right text-yellow-600">{formatRupees(day.total_paise - day.collected_paise)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function StatCard({ title, value, icon, color }: { title: string; value: string; icon: React.ReactNode; color: string }) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-gray-500">{title}</p>
          <p className="text-2xl font-bold text-gray-900 mt-1">{value}</p>
        </div>
        <div className={cn("p-3 rounded-full", color)}>
          {icon}
        </div>
      </div>
    </div>
  );
}