"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { productSchema, type Product } from "@/lib/schemas";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cn } from "@/lib/utils";
import {
  Plus,
  Edit,
  Trash2,
  Loader2,
  Save,
  X,
  Image as ImageIcon,
  Search,
  AlertCircle,
  Package,
  Weight,
  Layers,
} from "lucide-react";
import { useDropzone } from "react-dropzone";
import { Toaster, toast } from "sonner";

type ProductWithCategory = Product & { categories?: { name: string }; id: string };

export default function ProductsTab() {
  const [products, setProducts] = useState<ProductWithCategory[]>([]);
  const [categories, setCategories] = useState<{ id: string; name: string }[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [search, setSearch] = useState("");
  const [priceRupees, setPriceRupees] = useState<number>(100);
  const [stockInput, setStockInput] = useState<number>(50);

  const form = useForm<Product>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      sku: "",
      name: "",
      description: "",
      category_id: "",
      image_path: "",
      quantity_type: "PACKED",
      price_paise: 10000,
      price_basis_qty: 1,
      unit_label: "pack",
      pack_size_label: "1 pc",
      step_qty: 1,
      min_qty: 1,
      max_qty: null,
      stock_qty: 50,
      low_stock_threshold: 5,
      active: true,
      available: true,
    },
  });

  const watchedQuantityType = form.watch("quantity_type");

  // Keep form fields synced when Quantity Type changes
  useEffect(() => {
    const isWeighted = watchedQuantityType?.toLowerCase() === "weighted";
    if (isWeighted) {
      form.setValue("price_basis_qty", 1000);
      form.setValue("unit_label", "kg");
      form.setValue("step_qty", 10);
      form.setValue("min_qty", 100);
      form.setValue("pack_size_label", null);
      form.setValue("low_stock_threshold", 5000);
    } else {
      form.setValue("price_basis_qty", 1);
      form.setValue("unit_label", "pack");
      form.setValue("step_qty", 1);
      form.setValue("min_qty", 1);
      form.setValue("low_stock_threshold", 5);
    }
  }, [watchedQuantityType, form]);

  const fetchData = async () => {
    const supabase = createClient();
    const [productsRes, categoriesRes] = await Promise.all([
      supabase.from("products").select("*, categories(name)").order("name"),
      supabase.from("categories").select("id, name").eq("active", true).order("sort_order"),
    ]);
    if (productsRes.error || categoriesRes.error) {
      toast.error("Failed to load catalogue data");
      return;
    }
    setProducts(productsRes.data || []);
    const catList = categoriesRes.data || [];
    setCategories(catList);
    if (catList.length > 0 && !form.getValues("category_id")) {
      form.setValue("category_id", catList[0].id);
    }
    setLoading(false);
  };

  useEffect(() => {
    fetchData();
  }, []);

  const onSubmit = async (data: Product) => {
    const supabase = createClient();
    try {
      const isWeighted = data.quantity_type?.toLowerCase() === "weighted";
      const generatedSku = (data.sku && data.sku.trim())
        ? data.sku.trim()
        : `SKU-${crypto.randomUUID().slice(0, 8).toUpperCase()}`;
      const calculatedPricePaise = Math.round((priceRupees || 0) * 100);
      const selectedCategoryId = data.category_id || (categories.length > 0 ? categories[0].id : null);

      if (!selectedCategoryId) {
        toast.error("Please create or select a Category first.");
        return;
      }

      const { stock_qty: _ignoredStock, ...productFields } = data;
      const payload = {
        ...productFields,
        sku: generatedSku,
        category_id: selectedCategoryId,
        quantity_type: isWeighted ? "WEIGHTED" : "PACKED",
        price_paise: calculatedPricePaise,
        price_basis_qty: isWeighted ? 1000 : 1,
        unit_label: isWeighted ? "kg" : "pack",
        pack_size_label: isWeighted ? null : (data.pack_size_label || "1 pc"),
        step_qty: isWeighted ? 10 : 1,
        min_qty: isWeighted ? 100 : 1,
        low_stock_threshold: isWeighted ? 5000 : 5,
        max_qty: data.max_qty || null,
      };

      if (editingId) {
        const { error } = await supabase.from("products").update(payload).eq("id", editingId);
        if (error) throw error;
        toast.success("Product updated successfully!");
      } else {
        const { data: inserted, error } = await supabase
          .from("products")
          .insert(payload)
          .select("id")
          .single();
        if (error) throw error;

        const initialStockQty = isWeighted
          ? Math.round((stockInput || 0) * 1000)
          : Math.round(stockInput || 0);
        if (initialStockQty > 0) {
          const { data: stockResult, error: stockError } = await supabase.rpc("adjust_stock", {
            p_product_id: inserted.id,
            p_delta_qty: initialStockQty,
            p_reason: "manual_adjustment",
            p_note: "Initial stock on product creation",
          });
          if (stockError || stockResult?.error) {
            toast.error(
              stockError?.message || stockResult?.message || "Product created but initial stock failed",
            );
          }
        }
        toast.success("Product created successfully!");
      }
      setEditingId(null);
      setCreating(false);
      form.reset();
      fetchData();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to save product.");
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Archive this product? It will no longer be available for sale.")) return;
    const supabase = createClient();
    const { error } = await supabase.from("products").update({ active: false }).eq("id", id);
    if (error) {
      toast.error("Failed to archive product");
      return;
    }
    toast.success("Product archived");
    fetchData();
  };

  const startEdit = (prod: ProductWithCategory) => {
    const isWeighted = prod.quantity_type?.toLowerCase() === "weighted";
    form.reset({
      ...prod,
      quantity_type: isWeighted ? "WEIGHTED" : "PACKED",
      max_qty: prod.max_qty || undefined,
      pack_size_label: prod.pack_size_label || undefined,
    });
    setPriceRupees(prod.price_paise / 100);
    setStockInput(isWeighted ? prod.stock_qty / 1000 : prod.stock_qty);
    setEditingId(prod.id);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setCreating(false);
    form.reset();
  };

  const onDrop = async (acceptedFiles: File[]) => {
    const file = acceptedFiles[0];
    if (!file.type.startsWith("image/")) {
      toast.error("Only image files allowed");
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      toast.error("Max image size is 2MB");
      return;
    }
    const reader = new FileReader();
    reader.onload = () => form.setValue("image_path", reader.result as string);
    reader.readAsDataURL(file);
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { "image/*": [] },
  });

  const filteredProducts = products.filter(
    (p) =>
      (p.name || "").toLowerCase().includes(search.toLowerCase()) ||
      (p.sku || "").toLowerCase().includes(search.toLowerCase()),
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  return (
    <div className="w-full space-y-6">
      {/* Search & Action Bar */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4 bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search products by name or SKU..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:bg-white transition-all"
          />
        </div>

        <button
          onClick={() => {
            setCreating(true);
            form.reset({
              sku: "",
              name: "",
              description: "",
              category_id: categories.length > 0 ? categories[0].id : "",
              image_path: "",
              quantity_type: "PACKED",
              price_paise: 10000,
              price_basis_qty: 1,
              unit_label: "pack",
              pack_size_label: "1 pc",
              step_qty: 1,
              min_qty: 1,
              max_qty: null,
              stock_qty: 50,
              low_stock_threshold: 5,
              active: true,
              available: true,
            });
            setPriceRupees(100);
            setStockInput(50);
          }}
          className="flex items-center justify-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-bold rounded-xl shadow-md shadow-emerald-600/20 transition-all"
        >
          <Plus className="h-4 w-4" /> Add New Product
        </button>
      </div>

      {categories.length === 0 && (
        <div className="flex items-center gap-3 p-4 bg-amber-50 border border-amber-200 rounded-2xl text-amber-800 text-sm font-semibold">
          <AlertCircle className="h-5 w-5 text-amber-600 flex-shrink-0" />
          <span>No categories found in store. Please switch to the <strong>Categories</strong> tab to create a category first.</span>
        </div>
      )}

      {creating && (
        <ProductFormCard
          form={form}
          categories={categories}
          onSubmit={onSubmit}
          onCancel={cancelEdit}
          title="Create New Product"
          getRootProps={getRootProps}
          getInputProps={getInputProps}
          isDragActive={isDragActive}
          priceRupees={priceRupees}
          setPriceRupees={setPriceRupees}
          stockInput={stockInput}
          setStockInput={setStockInput}
          watchedQuantityType={watchedQuantityType}
          isEditing={false}
        />
      )}

      {/* Products Table */}
      <div className="w-full bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="overflow-x-auto w-full">
          <table className="w-full text-left border-collapse min-w-[700px]">
            <thead className="bg-slate-50 border-b border-slate-200 text-xs font-bold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="px-4 py-3.5">Image</th>
                <th className="px-4 py-3.5">Product Name & SKU</th>
                <th className="px-4 py-3.5">Category</th>
                <th className="px-4 py-3.5">Quantity Type</th>
                <th className="px-4 py-3.5">Selling Price (₹)</th>
                <th className="px-4 py-3.5">Stock</th>
                <th className="px-4 py-3.5">Status</th>
                <th className="px-4 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 text-sm text-slate-700">
              {filteredProducts.map((prod) => {
                const isWeighted = prod.quantity_type?.toLowerCase() === "weighted";
                return (
                  <tr
                    key={prod.id}
                    className={editingId === prod.id ? "bg-emerald-50/50" : "hover:bg-slate-50/80 transition-colors"}
                  >
                    {editingId === prod.id ? (
                      <td colSpan={8} className="p-4">
                        <ProductFormCard
                          form={form}
                          categories={categories}
                          onSubmit={onSubmit}
                          onCancel={cancelEdit}
                          title="Edit Product"
                          getRootProps={getRootProps}
                          getInputProps={getInputProps}
                          isDragActive={isDragActive}
                          priceRupees={priceRupees}
                          setPriceRupees={setPriceRupees}
                          stockInput={stockInput}
                          setStockInput={setStockInput}
                          watchedQuantityType={watchedQuantityType}
                          isEditing
                        />
                      </td>
                    ) : (
                      <>
                        <td className="px-4 py-3">
                          {prod.image_path ? (
                            <img
                              src={prod.image_path}
                              alt={prod.name}
                              className="h-12 w-12 object-cover rounded-xl border border-slate-200"
                            />
                          ) : (
                            <div className="h-12 w-12 bg-slate-100 rounded-xl border border-slate-200 flex items-center justify-center">
                              <Package className="h-6 w-6 text-slate-400" />
                            </div>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          <p className="font-bold text-slate-900">{prod.name}</p>
                          <p className="text-xs text-slate-500 font-mono">{prod.sku}</p>
                        </td>
                        <td className="px-4 py-3">
                          <span className="text-xs font-semibold text-slate-600 bg-slate-100 px-2.5 py-1 rounded-lg">
                            {prod.categories?.name || "General"}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={cn(
                              "inline-flex items-center gap-1 px-2.5 py-0.5 text-xs font-bold rounded-full",
                              isWeighted
                                ? "bg-blue-100 text-blue-800"
                                : "bg-emerald-100 text-emerald-800",
                            )}
                          >
                            {isWeighted ? <Weight className="h-3 w-3" /> : <Layers className="h-3 w-3" />}
                            {isWeighted ? "WEIGHTED (g/kg)" : "PACKED (1 pc)"}
                          </span>
                        </td>
                        <td className="px-4 py-3 font-semibold text-slate-900">
                          ₹{(prod.price_paise / 100).toFixed(2)}
                          <span className="text-xs text-slate-400 ml-1">
                            / {isWeighted ? "1 kg" : (prod.unit_label || "1 pc")}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={cn(
                              "font-bold text-sm",
                              prod.stock_qty <= prod.low_stock_threshold && prod.stock_qty > 0
                                ? "text-amber-600"
                                : prod.stock_qty === 0
                                  ? "text-red-600"
                                  : "text-slate-900",
                            )}
                          >
                            {isWeighted
                              ? `${(prod.stock_qty / 1000).toFixed(1)} kg`
                              : `${prod.stock_qty} pcs`}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={cn(
                              "inline-flex items-center px-2.5 py-0.5 text-xs font-bold rounded-full",
                              prod.active
                                ? prod.available
                                  ? "bg-emerald-100 text-emerald-800"
                                  : "bg-amber-100 text-amber-800"
                                : "bg-slate-100 text-slate-600",
                            )}
                          >
                            {prod.active ? (prod.available ? "Available" : "Unavailable") : "Archived"}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <div className="flex items-center justify-end gap-1">
                            <button
                              onClick={() => startEdit(prod)}
                              className="p-2 text-slate-500 hover:text-slate-900 hover:bg-slate-100 rounded-lg transition-colors"
                              aria-label="Edit"
                            >
                              <Edit className="h-4 w-4" />
                            </button>
                            <button
                              onClick={() => handleDelete(prod.id)}
                              className="p-2 text-slate-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                              aria-label="Archive"
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          </div>
                        </td>
                      </>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {filteredProducts.length === 0 && (
          <div className="p-12 text-center text-slate-400 font-medium">
            No products found in catalogue. Click <strong>+ Add New Product</strong> above to add one.
          </div>
        )}
      </div>

      <Toaster position="top-right" />
    </div>
  );
}

function ProductFormCard({
  form,
  categories,
  onSubmit,
  onCancel,
  title,
  getRootProps,
  getInputProps,
  isDragActive,
  priceRupees,
  setPriceRupees,
  stockInput,
  setStockInput,
  watchedQuantityType,
  isEditing,
}: {
  form: ReturnType<typeof useForm<Product>>;
  categories: { id: string; name: string }[];
  onSubmit: (data: Product) => void;
  onCancel: () => void;
  title: string;
  getRootProps: () => Record<string, unknown>;
  getInputProps: () => Record<string, unknown>;
  isDragActive: boolean;
  priceRupees: number;
  setPriceRupees: (val: number) => void;
  stockInput: number;
  setStockInput: (val: number) => void;
  watchedQuantityType: string;
  isEditing: boolean;
}) {
  const isWeighted = watchedQuantityType?.toLowerCase() === "weighted";

  return (
    <div className="bg-white border border-emerald-300 rounded-3xl p-6 shadow-xl space-y-6 w-full">
      <div className="flex items-center justify-between border-b border-slate-100 pb-3">
        <div className="flex items-center gap-2">
          <Package className="h-5 w-5 text-emerald-600" />
          <h3 className="text-lg font-extrabold text-slate-900">{title}</h3>
        </div>
        <button
          type="button"
          onClick={onCancel}
          className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5">
        {/* Step 1: Select Quantity Type First */}
        <div className="p-4 bg-emerald-50/60 border border-emerald-200 rounded-2xl space-y-2">
          <label className="block text-xs font-bold text-emerald-900 uppercase tracking-wider">
            Step 1: Select Quantity Type First *
          </label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <label
              className={cn(
                "flex items-center gap-3 p-3.5 border-2 rounded-xl cursor-pointer transition-all",
                !isWeighted
                  ? "border-emerald-600 bg-white shadow-sm"
                  : "border-slate-200 bg-slate-50 hover:bg-white"
              )}
            >
              <input
                type="radio"
                value="PACKED"
                {...form.register("quantity_type")}
                className="h-4 w-4 text-emerald-600 focus:ring-emerald-500"
              />
              <div className="flex flex-col">
                <span className="text-sm font-bold text-slate-900">📦 Packed Unit (1 Pc / Pack)</span>
                <span className="text-xs text-slate-500">Priced per single piece/pack (e.g., Atta 5kg, Biscuits, Oil 1L)</span>
              </div>
            </label>

            <label
              className={cn(
                "flex items-center gap-3 p-3.5 border-2 rounded-xl cursor-pointer transition-all",
                isWeighted
                  ? "border-emerald-600 bg-white shadow-sm"
                  : "border-slate-200 bg-slate-50 hover:bg-white"
              )}
            >
              <input
                type="radio"
                value="WEIGHTED"
                {...form.register("quantity_type")}
                className="h-4 w-4 text-emerald-600 focus:ring-emerald-500"
              />
              <div className="flex flex-col">
                <span className="text-sm font-bold text-slate-900">⚖️ Weighted (By 1 kg / Grams)</span>
                <span className="text-xs text-slate-500">Priced per 1 kg. Customers can select custom weight in grams/kg.</span>
              </div>
            </label>
          </div>
        </div>

        {/* Step 2: Product Name, Category & Dynamic Pricing */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Product Name *</label>
            <input
              {...form.register("name")}
              required
              className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
              placeholder={isWeighted ? "e.g. Fresh Red Onions" : "e.g. Aashirvaad Atta 5kg"}
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Category *</label>
            <select
              {...form.register("category_id")}
              required
              className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
            >
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              {isWeighted ? "Selling Price per 1 kg (₹) *" : "Selling Price per 1 Pc / Pack (₹) *"}
            </label>
            <input
              type="number"
              step="0.01"
              value={priceRupees}
              onChange={(e) => setPriceRupees(parseFloat(e.target.value) || 0)}
              required
              className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm font-bold focus:ring-2 focus:ring-emerald-500"
              placeholder={isWeighted ? "e.g. 60.00 per kg" : "e.g. 120.00 per pack"}
            />
            <span className="text-[11px] text-slate-500 mt-1 block">
              {isWeighted ? "Price entered is for 1 kilogram (1000g)" : "Price entered is for 1 unit / pack"}
            </span>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              {isWeighted ? "Available Stock (in Kilograms - kg) *" : "Available Stock (in Packs / Pcs) *"}
            </label>
            <input
              type="number"
              value={stockInput}
              onChange={(e) => setStockInput(parseFloat(e.target.value) || 0)}
              required
              disabled={isEditing}
              className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm font-semibold focus:ring-2 focus:ring-emerald-500 disabled:cursor-not-allowed disabled:opacity-60"
              placeholder={isWeighted ? "e.g. 50 (50 kg)" : "e.g. 50 (50 pcs)"}
            />
            <span className="text-[11px] text-slate-500 mt-1 block">
              {isEditing
                ? "Stock can only be changed through Inventory → Adjust Stock"
                : isWeighted
                  ? "Initial stock in kg (e.g. 50 kg), recorded as an audited movement"
                  : "Initial quantity in packs, recorded as an audited movement"}
            </span>
          </div>

          {/* Only shown for PACKED items */}
          {!isWeighted && (
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Weight / Volume Per Pack</label>
              <input
                {...form.register("pack_size_label")}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
                placeholder="e.g. 5 kg, 1 L, 200 g"
              />
              <span className="text-[11px] text-slate-500 mt-1 block">Specify weight or volume per single unit pack</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">SKU (Auto-Generated if Blank)</label>
            <input
              {...form.register("sku")}
              className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm font-mono focus:ring-2 focus:ring-emerald-500"
              placeholder="Auto-generated if left blank"
            />
          </div>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1">Description (Optional)</label>
          <textarea
            {...form.register("description")}
            rows={2}
            className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
            placeholder="Product details or highlights..."
          />
        </div>

        <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 border border-slate-300 rounded-xl text-sm font-semibold text-slate-700 hover:bg-slate-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-bold rounded-xl shadow-md shadow-emerald-600/20"
          >
            <Save className="h-4 w-4" /> Save Product
          </button>
        </div>
      </form>
    </div>
  );
}