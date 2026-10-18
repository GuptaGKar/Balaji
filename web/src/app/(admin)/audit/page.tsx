"use client";

import React from "react";
import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { formatRelativeTime, formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { Loader2, Search, Filter, Download, ChevronDown, ChevronUp, Eye, X } from "lucide-react";
import { Toaster, toast } from "sonner";

type AuditLog = {
  id: string;
  actor_id: string;
  action: string;
  entity_type: string;
  entity_id: string;
  before: any;
  after: any;
  request_id: string;
  created_at: string;
  actor_name: string | null;
  actor_role: string | null;
};

type AuditFilters = {
  start_date: string | null;
  end_date: string | null;
  entity_type: string | null;
  actor_id: string | null;
  action: string | null;
  page: number;
  page_size: number;
};

export default function AuditPage() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [filters, setFilters] = useState<AuditFilters>({
    start_date: null,
    end_date: null,
    entity_type: null,
    actor_id: null,
    action: null,
    page: 1,
    page_size: 50,
  });
  const [expandedLogs, setExpandedLogs] = useState<Set<string>>(new Set());

  const supabase = createClient();

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      let query = supabase
        .from("audit_logs")
        .select("*", { count: "exact" })
        .order("created_at", { ascending: false });

      if (filters.start_date) query = query.gte("created_at", filters.start_date);
      if (filters.end_date) query = query.lte("created_at", filters.end_date);
      if (filters.entity_type) query = query.eq("entity_type", filters.entity_type);
      if (filters.actor_id) query = query.eq("actor_id", filters.actor_id);
      if (filters.action) query = query.eq("action", filters.action);

      query = query.range(
        (filters.page - 1) * filters.page_size,
        filters.page * filters.page_size - 1,
      );

      const { data, error, count } = await query;
      if (error) {
        toast.error("Failed to load audit logs");
        return;
      }
      setLogs(data || []);
      setTotalCount(count || 0);
    } catch (err: any) {
      toast.error("Failed to load audit logs");
    } finally {
      setLoading(false);
    }
  }, [filters, supabase]);

  const fetchEntityTypes = useCallback(async () => {
    const { data } = await supabase.from("audit_logs").select("entity_type").order("entity_type");
    const types = Array.from(new Set(data?.map((d) => d.entity_type) || []));
    return types;
  }, [supabase]);

  const fetchActors = useCallback(async () => {
    const { data } = await supabase
      .from("audit_logs")
      .select("actor_id, actor_name, actor_role")
      .order("actor_name");
    const actorsMap = new Map<string, { id: string; name: string | null; role: string | null }>();
    (data || []).forEach((d) => {
      if (d.actor_id && !actorsMap.has(d.actor_id)) {
        actorsMap.set(d.actor_id, { id: d.actor_id, name: d.actor_name, role: d.actor_role });
      }
    });
    return Array.from(actorsMap.values());
  }, [supabase]);

  const [entityTypes, setEntityTypes] = useState<string[]>([]);
  const [actors, setActors] = useState<
    Array<{ id: string; name: string | null; role: string | null }>
  >([]);

  useEffect(() => {
    fetchEntityTypes().then(setEntityTypes);
    fetchActors().then(setActors);
  }, [fetchEntityTypes, fetchActors]);

  const handleFilterChange = (key: keyof AuditFilters, value: any) => {
    setFilters((prev) => ({ ...prev, [key]: value, page: 1 }));
  };

  const handlePageChange = (newPage: number) => {
    setFilters((prev) => ({ ...prev, page: newPage }));
  };

  const handleExport = async () => {
    try {
      const supabase = createClient();
      const { data, error } = await supabase.rpc("get_audit_logs", {
        p_start_date: filters.start_date,
        p_end_date: filters.end_date,
        p_entity_type: filters.entity_type,
        p_actor_id: filters.actor_id,
        p_limit: totalCount,
        p_offset: 0,
      });
      if (error) throw error;

      const csv = [
        [
          "ID",
          "Timestamp",
          "Actor",
          "Role",
          "Action",
          "Entity Type",
          "Entity ID",
          "Before",
          "After",
          "Request ID",
        ],
        ...(data || []).map((log: any) => [
          log.id,
          formatDateTime(log.created_at),
          log.actor_name || log.actor_id,
          log.actor_role || "N/A",
          log.action,
          log.entity_type,
          log.entity_id,
          JSON.stringify(log.before || {}),
          JSON.stringify(log.after || {}),
          log.request_id || "",
        ]),
      ]
        .map((row: any[]) =>
          row.map((cell: any) => `"${String(cell).replace(/"/g, '""')}"`).join(","),
        )
        .join("\n");

      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `audit-logs-${new Date().toISOString().split("T")[0]}.csv`;
      link.click();
      URL.revokeObjectURL(url);
      toast.success("Export complete");
    } catch (err: any) {
      toast.error("Export failed: " + err.message);
    }
  };

  const toggleExpand = (id: string) => {
    setExpandedLogs((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const formatDateTime = (iso: string) => {
    return new Date(iso).toLocaleString();
  };

  const formatRelativeTime = (iso: string) => {
    const date = new Date(iso);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return "just now";
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return new Date(iso).toLocaleDateString();
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
          <h1 className="text-2xl font-bold text-gray-900">Audit Log</h1>
          <p className="text-gray-500">Track all system changes and user actions</p>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={handleExport} disabled={loading} className="btn-secondary">
            <Download className="h-4 w-4 mr-2" />
            Export CSV
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">From Date</label>
            <input
              type="date"
              value={filters.start_date || ""}
              onChange={(e) => handleFilterChange("start_date", e.target.value || null)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">To Date</label>
            <input
              type="date"
              value={filters.end_date || ""}
              onChange={(e) => handleFilterChange("end_date", e.target.value || null)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Entity Type</label>
            <select
              value={filters.entity_type || ""}
              onChange={(e) => handleFilterChange("entity_type", e.target.value || null)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">All Types</option>
              {entityTypes.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Actor</label>
            <select
              value={filters.actor_id || ""}
              onChange={(e) => handleFilterChange("actor_id", e.target.value || null)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">All Actors</option>
              {actors.map((actor) => (
                <option key={actor.id} value={actor.id}>
                  {actor.name || actor.id} ({actor.role})
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-end">
            <button
              onClick={() =>
                setFilters((prev) => ({
                  ...prev,
                  start_date: null,
                  end_date: null,
                  entity_type: null,
                  actor_id: null,
                  action: null,
                  page: 1,
                }))
              }
              className="w-full px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50"
            >
              <X className="h-4 w-4 mr-2 inline" /> Clear Filters
            </button>
          </div>
        </div>
      </div>

      {/* Results Table */}
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Time
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actor
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Action
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Entity
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Entity ID
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Request ID
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Details
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {logs.map((log) => {
                const isExpanded = expandedLogs.has(log.id);
                return (
                  <React.Fragment key={log.id}>
                    <tr
                      className={cn("hover:bg-gray-50", expandedLogs.has(log.id) && "bg-blue-50")}
                    >
                      <td className="px-4 py-3 text-sm text-gray-500 whitespace-nowrap">
                        {formatRelativeTime(log.created_at)}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-6 h-6 rounded-full bg-indigo-100 flex items-center justify-center">
                            <span className="text-xs font-medium text-indigo-700">
                              {log.actor_name?.charAt(0) || log.actor_id.slice(0, 2).toUpperCase()}
                            </span>
                          </div>
                          <div>
                            <p className="text-sm font-medium text-gray-900">
                              {log.actor_name || "Unknown"}
                            </p>
                            <p className="text-xs text-gray-500">{log.actor_role || "N/A"}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={cn(
                            "inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                            log.action === "INSERT"
                              ? "bg-green-100 text-green-800"
                              : log.action === "UPDATE"
                                ? "bg-yellow-100 text-yellow-800"
                                : log.action === "DELETE"
                                  ? "bg-red-100 text-red-800"
                                  : log.action === "ASSIGN_ORDER"
                                    ? "bg-indigo-100 text-indigo-800"
                                    : log.action === "REMOVE_ORDER_ITEM"
                                      ? "bg-orange-100 text-orange-800"
                                      : log.action === "ADJUST_STOCK"
                                        ? "bg-purple-100 text-purple-800"
                                        : log.action === "CREATE_RIDER"
                                          ? "bg-blue-100 text-blue-800"
                                          : log.action === "RESET_RIDER_PASSWORD"
                                            ? "bg-pink-100 text-pink-800"
                                            : log.action === "SET_RIDER_ACTIVE"
                                              ? "bg-teal-100 text-teal-800"
                                              : log.action === "ASSIGN_ORDER"
                                                ? "bg-indigo-100 text-indigo-800"
                                                : "bg-gray-100 text-gray-800",
                          )}
                        >
                          {log.action.replace("_", " ")}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-900">{log.entity_type}</td>
                      <td className="px-4 py-3 text-sm text-gray-500 font-mono text-xs">
                        {log.entity_id.slice(0, 8)}…
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-500 font-mono text-xs">
                        {log.request_id?.slice(0, 12) || "—"}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => toggleExpand(log.id)}
                          className="p-1 text-gray-500 hover:text-gray-700"
                          aria-label={expandedLogs.has(log.id) ? "Collapse" : "Expand"}
                        >
                          {expandedLogs.has(log.id) ? (
                            <ChevronUp className="h-4 w-4" />
                          ) : (
                            <ChevronDown className="h-4 w-4" />
                          )}
                        </button>
                      </td>
                    </tr>
                    {expandedLogs.has(log.id) ? (
                      <tr className="bg-blue-50">
                        <td colSpan={7} className="px-4 py-3">
                          <div className="grid grid-cols-2 gap-4 p-3 bg-white rounded border">
                            <div>
                              <p className="text-xs font-medium text-gray-500 mb-1">Before</p>
                              <pre
                                className="text-xs bg-gray-50 p-2 rounded overflow-auto max-h-40"
                                style={{ fontSize: "10px" }}
                              >
                                {JSON.stringify(log.before, null, 2) || "—"}
                              </pre>
                            </div>
                            <div>
                              <p className="text-xs font-medium text-gray-500 mb-1">After</p>
                              <pre
                                className="text-xs bg-gray-50 p-2 rounded overflow-auto max-h-40"
                                style={{ fontSize: "10px" }}
                              >
                                {JSON.stringify(log.after, null, 2) || "—"}
                              </pre>
                            </div>
                          </div>
                        </td>
                      </tr>
                    ) : null}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
        {logs.length === 0 && (
          <div className="p-12 text-center text-gray-500">No audit logs found</div>
        )}
      </div>

      {/* Pagination */}
      <div className="px-4 py-3 border-t border-gray-200 flex items-center justify-between">
        <div className="text-sm text-gray-500">
          Showing {(filters.page - 1) * filters.page_size + 1} to{" "}
          {Math.min(filters.page * filters.page_size, totalCount)} of {totalCount} results
        </div>
        <div className="flex items-center gap-2">
          <select
            value={filters.page_size}
            onChange={(e) =>
              setFilters((prev) => ({ ...prev, page_size: parseInt(e.target.value), page: 1 }))
            }
            className="px-3 py-1 border border-gray-300 rounded-md text-sm"
          >
            <option value="25">25 per page</option>
            <option value="50">50 per page</option>
            <option value="100">100 per page</option>
          </select>
          <button
            onClick={() => handlePageChange(filters.page - 1)}
            disabled={filters.page === 1}
            className="px-3 py-1 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50"
          >
            Previous
          </button>
          <button
            onClick={() => handlePageChange(filters.page + 1)}
            disabled={filters.page * filters.page_size >= totalCount}
            className="px-3 py-1 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50"
          >
            Next
          </button>
        </div>
      </div>

      <Toaster position="top-right" />
    </div>
  );
}
