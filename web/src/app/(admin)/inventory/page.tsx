"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { formatRupees } from "@/lib/money";
import { formatRelativeTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { Loader2, AlertTriangle, TrendingDown, Plus, Minus, Search, RotateCcw } from "lucide-react";
import { Toaster, toast } from "sonner";

type Product = {
  id: string;
  sku: string;
  name: string;
  quantity_type: "WEIGHTED" | "PACKED";
  price_paise: number;
  price_basis_qty: number;
  unit_label: string;
  pack_size_label: string | null;
  step_qty: number;
  min_qty: number;
  max_qty: number | null;
  stock_qty: number;
  low_stock_threshold: number;
  active: boolean;
  available: boolean;
};

type InventoryMovement = {
  id: string;
  product_id: string;
  delta_qty: number;
  reason: string;
  reference_type: string | null;
  reference_id: string | null;
  actor_id: string | null;
  note: string | null;
  created_at: string;
};

type LowStockProduct = Product & {
  deficit: number;
};

export default function InventoryPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [movements, setMovements] = useState<InventoryMovement[]>([]);
  const [lowStock, setLowStock] = useState<LowStockProduct[]>([]);
  const [reconciliation, setReconciliation] = useState<Array<{
    product_id: string;
    sku: string;
    name: string;
    current_stock: number;
    movement_sum: number;
    discrepancy: number;
  }>>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<"stock" | "movements" | "low-stock" | "reconciliation">("stock");
  const [search, setSearch] = useState("");

  const supabase = createClient();

  const fetchProducts = async () => {
    const { data, error } = await supabase
      .from("products")
      .select("*")
      .eq("active", true)
      .order("name");
    if (error) { toast.error("Failed to load products"); return; }
    setProducts(data || []);
  };

  const fetchMovements = async () => {
    const { data, error } = await supabase
      .from("inventory_movements")
      .select("*, products(sku, name)")
      .order("created_at", { ascending: false })
      .limit(200);
    if (error) { toast.error("Failed to load movements"); return; }
    setMovements(data || []);
  };

  const fetchLowStock = async () => {
    const supabase = createClient();
    const { data, error } = await supabase.rpc("get_low_stock_products");
    if (error) { toast.error("Failed to load low stock"); return; }
    const lowStockData = (data || []).map((p: any) => ({
      ...p,
      deficit: p.low_stock_threshold - p.stock_qty,
    }));
    setLowStock(lowStockData);
  };

  const fetchReconciliation = async () => {
    const supabase = createClient();
    const { data, error } = await supabase.rpc("check_stock_reconciliation");
    if (error) { toast.error("Failed to check reconciliation"); return; }
    setReconciliation(data || []);
  };

  const fetchAll = async () => {
    setLoading(true);
    await Promise.all([fetchProducts(), fetchMovements(), fetchLowStock(), fetchReconciliation()]);
    setLoading(false);
  };

  useEffect(() => { fetchAll(); }, []);

  const handleAdjustStock = async (productId: string, currentStock: number, delta: number, reason: string) => {
    const supabase = createClient();
    const { data, error } = await supabase.rpc("adjust_stock", {
      p_product_id: productId,
      p_delta_qty: delta,
      p_reason: reason as any,
      p_note: `Manual adjustment: ${reason}`,
    });
    if (error) { toast.error(error.message); return; }
    if (data.error) { toast.error(data.error.message); return; }
    toast.success(`Stock adjusted by ${delta > 0 ? "+" : ""}${delta}`);
    fetchAll();
  };

  const renderStockCell = (product: Product) => {
    const isLowStock = product.stock_qty > 0 && product.stock_qty <= product.low_stock_threshold;
    const isOutOfStock = product.stock_qty === 0;
    const displayStock = product.quantity_type === "WEIGHTED"
      ? `${(product.stock_qty / 1000).toFixed(3)} kg`
      : `${product.stock_qty} ${product.unit_label}`;

    if (isOutOfStock) {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-full bg-red-100 text-red-800">
          <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
          Out of stock
        </span>
      );
    }
    if (isLowStock) {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-full bg-yellow-100 text-yellow-800">
          <span className="w-1.5 h-1.5 rounded-full bg-yellow-500" />
          {displayStock} (Low)
        </span>
      );
    }
    return <span className="font-medium">{displayStock}</span>;
  };

  const filteredProducts = products.filter(p =>
    p.name.toLowerCase().includes(search.toLowerCase()) ||
    p.sku.toLowerCase().includes(search.toLowerCase())
  );

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
          <h1 className="text-2xl font-bold text-gray-900">Inventory</h1>
          <p className="text-gray-500">Stock levels, movements, and reconciliation</p>
        </div>
        <button onClick={() => { setActiveTab("stock"); }} className="btn-primary">
          <RotateCcw className="h-4 w-4 mr-2" /> Refresh
        </button>
      </div>

      {/* Tab Navigation */}
      <div className="bg-white rounded-lg border border-gray-200">
        <nav className="flex border-b border-gray-200" aria-label="Inventory tabs">
          {[
            { id: "stock", label: "Stock Levels", count: products.length },
            { id: "movements", label: "Movements", count: movements.length },
            { id: "low-stock", label: "Low Stock", count: lowStock.length },
            { id: "reconciliation", label: "Reconciliation", count: reconciliation.length },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={cn(
                "px-6 py-3 text-sm font-medium border-b-2 transition-colors",
                activeTab === tab.id
                  ? "border-indigo-600 text-indigo-600"
                  : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
              )}
              aria-current={activeTab === tab.id ? "page" : undefined}
            >
              {tab.label}
              <span className={cn("ml-2 px-2 py-0.5 text-xs font-medium rounded-full",
                tab.count > 0 ? "bg-indigo-100 text-indigo-700" : "bg-gray-100 text-gray-500"
              )}>
                {tab.count}
              </span>
            </button>
          ))}
        </nav>

        <div className="p-4">
          {activeTab === "stock" && (
            <div>
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-900">All Products</h2>
                <div className="relative max-w-xs">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <input
                    type="text" placeholder="Search products..."
                    value={search} onChange={(e) => setSearch(e.target.value)}
                    className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Product</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Price</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Stock</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Threshold</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {filteredProducts.map((product) => (
                      <tr key={product.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4">
                          <p className="font-medium text-gray-900">{product.name}</p>
                          <p className="text-sm text-gray-500">{product.sku}</p>
                        </td>
                        <td className="px-6 py-4">
                          <span className={cn("inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                            product.quantity_type === "WEIGHTED" ? "bg-blue-100 text-blue-800" : "bg-green-100 text-green-800"
                          )}>
                            {product.quantity_type}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-gray-900">
                          {product.quantity_type === "WEIGHTED"
                            ? `₹${(product.price_paise / 100).toLocaleString()} / ${product.price_basis_qty === 1000 ? "kg" : "100g"}`
                            : `₹${(product.price_paise / 100).toLocaleString()} / pack`}
                        </td>
                        <td className="px-6 py-4">{renderStockCell(product)}</td>
                        <td className="px-6 py-4 text-gray-500">
                          {product.low_stock_threshold > 0
                            ? (product.quantity_type === "WEIGHTED"
                              ? `${(product.low_stock_threshold / 1000).toFixed(1)} kg`
                              : `${product.low_stock_threshold} ${product.unit_label}`)
                            : "—"}
                        </td>
                        <td className="px-6 py-4 text-right">
                          <AdjustStockButton product={product} onRefresh={() => {}} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {filteredProducts.length === 0 && (
                <div className="p-12 text-center text-gray-500">No products found</div>
              )}
            </div>
          )}

          {activeTab === "movements" && (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Date</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Product</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Reason</th>
                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Delta</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Reference</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Note</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {movements.map((movement) => (
                    <tr key={movement.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 text-sm text-gray-500 whitespace-nowrap">
                        {formatRelativeTime(movement.created_at)}
                      </td>
                      <td className="px-6 py-4">
                        <p className="font-medium text-gray-900">{movement.products?.name || "Unknown"}</p>
                        <p className="text-sm text-gray-500">{movement.products?.sku}</p>
                      </td>
                      <td className="px-6 py-4">
                        <span className={cn(
                          "inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                          movement.reason === "order_placed" ? "bg-blue-100 text-blue-800" :
                          movement.reason === "order_cancelled" ? "bg-yellow-100 text-yellow-800" :
                          movement.reason === "order_rejected" ? "bg-red-100 text-red-800" :
                          movement.reason === "item_removed" ? "bg-orange-100 text-orange-800" :
                          movement.reason === "pos_sale" ? "bg-green-100 text-green-800" :
                          movement.reason === "pos_void" ? "bg-red-100 text-red-800" :
                          movement.reason === "manual_adjustment" ? "bg-purple-100 text-purple-800" :
                          movement.reason === "correction" ? "bg-teal-100 text-teal-800" :
                          "bg-gray-100 text-gray-800"
                        )}>
                          {movement.reason.replace("_", " ")}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-right font-medium">
                        {movement.delta_qty > 0 ? "+" : ""}{movement.delta_qty}
                      </td>
                      <td className="px-6 py-4 text-left text-xs font-mono text-gray-500">
                        {movement.reference_type || "—"}
                        {movement.reference_id && ` ${movement.reference_id.slice(0, 8)}`}
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-500 max-w-xs truncate">
                        {movement.note || "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {movements.length === 0 ? (
                <div className="p-12 text-center text-gray-500">No movements recorded</div>
              ) : null}
            </div>
          )}

          {activeTab === "low-stock" && (
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              {lowStock.length > 0 ? (
                <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-4">
                  <div className="flex items-center gap-2 text-yellow-800 mb-2">
                    <TrendingDown className="h-5 w-5" />
                    <span className="font-medium">{lowStock.length} product(s) below threshold</span>
                  </div>
                </div>
              ) : (
                <div className="bg-green-50 border border-green-200 rounded-lg p-4 mb-4">
                  <div className="flex items-center gap-2 text-green-800">
                    <CheckCircle className="h-5 w-5" />
                    <span>All products above low stock threshold</span>
                  </div>
                </div>
              )}
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Product</th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Current Stock</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Threshold</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Deficit</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {lowStock.map((product) => (
                      <tr key={product.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4">
                          <p className="font-medium text-gray-900">{product.name}</p>
                          <p className="text-sm text-gray-500">{product.sku}</p>
                        </td>
                        <td className="px-6 py-4">
                          <span className={cn("inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                            product.quantity_type === "WEIGHTED" ? "bg-blue-100 text-blue-800" : "bg-green-100 text-green-800"
                          )}>
                            {product.quantity_type}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-right font-medium text-red-600">
                          {product.quantity_type === "WEIGHTED"
                            ? `${(product.stock_qty / 1000).toFixed(3)} kg`
                            : `${product.stock_qty} ${product.unit_label}`}
                        </td>
                        <td className="px-6 py-4 text-right text-gray-500">
                          {product.quantity_type === "WEIGHTED"
                            ? `${(product.low_stock_threshold / 1000).toFixed(1)} kg`
                            : `${product.low_stock_threshold} ${product.unit_label}`}
                        </td>
                        <td className="px-6 py-4 text-right font-bold text-red-600">
                          {product.quantity_type === "WEIGHTED"
                            ? `${((product.low_stock_threshold - product.stock_qty) / 1000).toFixed(3)} kg`
                            : `${product.low_stock_threshold - product.stock_qty} ${product.unit_label}`}
                        </td>
                        <td className="px-6 py-4 text-right">
                          <AdjustStockButton product={product} onRefresh={() => {}} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Reconciliation Tab */}
          {activeTab === "reconciliation" && (
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              {reconciliation.length > 0 ? (
                <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-4">
                  <div className="flex items-center gap-2 text-red-800 mb-2">
                    <AlertTriangle className="h-5 w-5" />
                    <span className="font-medium">{reconciliation.length} product(s) with stock discrepancy</span>
                  </div>
                  <p className="text-sm text-red-700">Run reconciliation job or investigate discrepancies manually.</p>
                </div>
              ) : (
                <div className="bg-green-50 border border-green-200 rounded-lg p-4 mb-4">
                  <div className="flex items-center gap-2 text-green-800">
                    <CheckCircle className="h-5 w-5" />
                    <span>All products reconciled - stock matches movement sum</span>
                  </div>
                </div>
              )}
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Product</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Current Stock</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Movement Sum</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Discrepancy</th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {reconciliation.map((item) => (
                      <tr key={item.product_id} className="hover:bg-gray-50">
                        <td className="px-6 py-4">
                          <p className="font-medium text-gray-900">{item.name}</p>
                          <p className="text-sm text-gray-500">{item.sku}</p>
                        </td>
                        <td className="px-6 py-4 text-right font-medium">{item.current_stock}</td>
                        <td className="px-6 py-4 text-right text-gray-500">{item.movement_sum}</td>
                        <td className="px-6 py-4 text-right font-bold text-red-600">
                          {item.discrepancy > 0 ? "+" : ""}{item.discrepancy}
                        </td>
                        <td className="px-6 py-4 text-right">
                          <button className="px-3 py-1.5 text-sm font-medium text-indigo-600 hover:text-indigo-800">
                            Investigate
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {reconciliation.length === 0 && <div className="p-12 text-center text-gray-500">No discrepancies found</div>}
            </div>
          )}
        </div>
      </div>

      <Toaster position="top-right" />
    </div>
  );
}

function AdjustStockButton({ product, onRefresh }: { product: Product; onRefresh: () => void }) {
  const [open, setOpen] = useState(false);
  const [delta, setDelta] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const supabase = createClient();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!delta || !reason) return;
    setSubmitting(true);
    const deltaNum = parseInt(delta, 10);
    const supabase = createClient();
    const { data, error } = await supabase.rpc("adjust_stock", {
      p_product_id: product.id,
      p_delta_qty: deltaNum,
      p_reason: "manual_adjustment",
      p_note: reason,
    });
    if (error || data?.error) {
      alert(error?.message || data?.error?.message || "Failed to adjust stock");
    } else {
      setOpen(false);
      setDelta("");
      setReason("");
      window.location.reload();
    }
    setSubmitting(false);
  };

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="px-3 py-1.5 text-sm font-medium text-indigo-600 hover:text-indigo-800 hover:bg-indigo-50 rounded-lg"
      >
        <Plus className="h-4 w-4 mr-1" /> Adjust
      </button>
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
            <h3 className="text-lg font-semibold mb-4">Adjust Stock: {product.name}</h3>
            <p className="text-sm text-gray-500 mb-4">Current: {product.quantity_type === "WEIGHTED" ? `${(product.stock_qty / 1000).toFixed(3)} kg` : `${product.stock_qty} ${product.unit_label}`}</p>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Adjustment</label>
                <div className="flex gap-2">
                  <select value={delta.startsWith("-") ? "subtract" : "add"} onChange={(e) => setDelta(e.target.value + (delta.startsWith("-") ? "-" : "") + delta.replace(/[^0-9]/g, ""))} className="w-1/3 px-3 py-2 border border-gray-300 rounded-md">
                    <option value="add">Add</option>
                    <option value="subtract">Subtract</option>
                  </select>
                  <input
                    type="number"
                    min="1"
                    placeholder="Quantity"
                    value={delta.replace(/[^0-9]/g, "")}
                    onChange={(e) => setDelta(delta.startsWith("-") ? "-" + e.target.value : e.target.value)}
                    className="flex-1 px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Reason *</label>
                <select value={reason} onChange={(e) => setReason(e.target.value)} className="w-full px-3 py-2 border border-gray-300 rounded-md" required>
                  <option value="manual_adjustment">Manual Adjustment</option>
                  <option value="correction">Correction</option>
                  <option value="damage">Damage</option>
                  <option value="expired">Expired</option>
                  <option value="return">Return</option>
                </select>
              </div>
              <div className="flex justify-end gap-3 pt-4 border-t">
                <button type="button" onClick={() => setOpen(false)} className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50">
                  Cancel
                </button>
                <button type="submit" disabled={submitting} className="btn-primary">
                  Apply Adjustment
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
