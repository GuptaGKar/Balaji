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
  FileText,
  Search,
  AlertCircle,
  Package,
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
      pack_size_label: "1 kg",
      step_qty: 1,
      min_qty: 1,
      max_qty: null,
      stock_qty: 50,
      low_stock_threshold: 5,
      active: true,
      available: true,
    },
  });

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
      const generatedSku = data.sku.trim() || `SKU-${Math.floor(100000 + Math.random() * 900000)}`;
      const calculatedPricePaise = Math.round((priceRupees || 0) * 100);
      const selectedCategoryId = data.category_id || (categories.length > 0 ? categories[0].id : null);

      if (!selectedCategoryId) {
        toast.error("Please create or select a Category first.");
        return;
      }

      const payload = {
        ...data,
        sku: generatedSku,
        category_id: selectedCategoryId,
        quantity_type: data.quantity_type.toLowerCase(),
        price_paise: calculatedPricePaise > 0 ? calculatedPricePaise : data.price_paise || 10000,
        max_qty: data.max_qty || null,
      };

      if (editingId) {
        const { error } = await supabase.from("products").update(payload).eq("id", editingId);
        if (error) throw error;
        toast.success("Product updated successfully!");
      } else {
        const { error } = await supabase.from("products").insert(payload);
        if (error) throw error;
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
    form.reset({
      ...prod,
      max_qty: prod.max_qty || undefined,
      pack_size_label: prod.pack_size_label || undefined,
    });
    setPriceRupees(prod.price_paise / 100);
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
      p.name.toLowerCase().includes(search.toLowerCase()) ||
      p.sku.toLowerCase().includes(search.toLowerCase()),
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
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
              pack_size_label: "1 kg",
              step_qty: 1,
              min_qty: 1,
              max_qty: null,
              stock_qty: 50,
              low_stock_threshold: 5,
              active: true,
              available: true,
            });
            setPriceRupees(100);
          }}
          className="flex items-center justify-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-bold rounded-xl shadow-md shadow-emerald-600/20 transition-all"
        >
          <Plus className="h-4 w-4" /> Add New Product
        </button>
      </div>

      {categories.length === 0 && (
        <div className="flex items-center gap-3 p-4 bg-amber-50 border border-amber-200 rounded-2xl text-amber-800 text-sm font-semibold">
          <AlertCircle className="h-5 w-5 text-amber-600 flex-shrink-0" />
          <span>No categories found in store. Please switch to the <strong>Categories</strong> tab to create at least one category before adding products.</span>
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
        />
      )}

      {/* Products Table */}
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-slate-50 border-b border-slate-200 text-xs font-bold text-slate-500 uppercase tracking-wider">
              <tr>
                <th className="px-4 py-3.5">Image</th>
                <th className="px-4 py-3.5">Product Name & SKU</th>
                <th className="px-4 py-3.5">Type</th>
                <th className="px-4 py-3.5">Price (₹)</th>
                <th className="px-4 py-3.5">Stock Qty</th>
                <th className="px-4 py-3.5">Status</th>
                <th className="px-4 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 text-sm text-slate-700">
              {filteredProducts.map((prod) => (
                <tr
                  key={prod.id}
                  className={editingId === prod.id ? "bg-emerald-50/50" : "hover:bg-slate-50/80 transition-colors"}
                >
                  {editingId === prod.id ? (
                    <td colSpan={7} className="p-4">
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
                        <span
                          className={cn(
                            "inline-flex items-center px-2.5 py-0.5 text-xs font-bold rounded-full",
                            prod.quantity_type?.toLowerCase() === "weighted"
                              ? "bg-blue-100 text-blue-800"
                              : "bg-emerald-100 text-emerald-800",
                          )}
                        >
                          {prod.quantity_type}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-semibold text-slate-900">
                        ₹{(prod.price_paise / 100).toFixed(2)}
                        <span className="text-xs text-slate-400 ml-1">
                          / {prod.unit_label || "pack"}
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
                          {prod.quantity_type?.toLowerCase() === "weighted"
                            ? `${(prod.stock_qty / 1000).toFixed(1)} kg`
                            : `${prod.stock_qty} packs`}
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
              ))}
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
}) {
  return (
    <div className="bg-white border border-emerald-300 rounded-3xl p-6 shadow-xl space-y-5">
      <div className="flex items-center justify-between border-b border-slate-100 pb-3">
        <h3 className="text-lg font-extrabold text-slate-900">{title}</h3>
        <button
          type="button"
          onClick={onCancel}
          className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Product Name *</label>
            <input
              {...form.register("name")}
              required
              className="w-full px-3.5 py-2 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
              placeholder="e.g. Aashirvaad Whole Wheat Atta 5kg"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Category *</label>
            <select
              {...form.register("category_id")}
              required
              className="w-full px-3.5 py-2 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
            >
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Selling Price (₹) *</label>
            <input
              type="number"
              step="0.01"
              value={priceRupees}
              onChange={(e) => setPriceRupees(parseFloat(e.target.value) || 0)}
              required
              className="w-full px-3.5 py-2 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm font-bold focus:ring-2 focus:ring-emerald-500"
              placeholder="100.00"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Stock Quantity *</label>
            <input
              type="number"
              {...form.register("stock_qty", { valueAsNumber: true })}
              required
              className="w-full px-3.5 py-2 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
              placeholder="50"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Quantity Type *</label>
            <select
              {...form.register("quantity_type")}
              className="w-full px-3.5 py-2 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
            >
              <option value="PACKED">Packed (by unit count / pack)</option>
              <option value="WEIGHTED">Weighted (by Grams / Kilograms)</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Pack Size Label</label>
            <input
              {...form.register("pack_size_label")}
              className="w-full px-3.5 py-2 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
              placeholder="e.g. 5 kg or 1 L"
            />
          </div>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1">Description (Optional)</label>
          <textarea
            {...form.register("description")}
            rows={2}
            className="w-full px-3.5 py-2 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
            placeholder="Short product description or features..."
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
            className="flex items-center gap-2 px-5 py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-bold rounded-xl shadow-md shadow-emerald-600/20"
          >
            <Save className="h-4 w-4" /> Save Product
          </button>
        </div>
      </form>
    </div>
  );
}