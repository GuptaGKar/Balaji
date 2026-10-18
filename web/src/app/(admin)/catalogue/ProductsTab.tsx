"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { productSchema, type Product, quantityTypeEnum } from "@/lib/schemas";
import { useForm, useFieldArray } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cn } from "@/lib/utils";
import { Plus, Edit, Trash2, Loader2, Save, X, Image, FileText, Search } from "lucide-react";
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

  const form = useForm<Product>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      sku: "",
      name: "",
      description: "",
      category_id: "",
      image_path: "",
      quantity_type: "PACKED",
      price_paise: 0,
      price_basis_qty: 1,
      unit_label: "pack",
      pack_size_label: null,
      step_qty: 1,
      min_qty: 1,
      max_qty: null,
      stock_qty: 0,
      low_stock_threshold: 0,
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
      toast.error("Failed to load data");
      return;
    }
    setProducts(productsRes.data || []);
    setCategories(categoriesRes.data || []);
    setLoading(false);
  };

  useEffect(() => { fetchData(); }, []);

  const onSubmit = async (data: Product) => {
    const supabase = createClient();
    try {
      const payload = { ...data, max_qty: data.max_qty || null };
      if (editingId) {
        const { error } = await supabase.from("products").update(payload).eq("id", editingId);
        if (error) throw error;
        toast.success("Product updated");
      } else {
        const { error } = await supabase.from("products").insert(payload);
        if (error) throw error;
        toast.success("Product created");
      }
      setEditingId(null);
      setCreating(false);
      form.reset();
      fetchData();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Operation failed");
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Archive this product? It will no longer be available for sale.")) return;
    const supabase = createClient();
    const { error } = await supabase.from("products").update({ active: false }).eq("id", id);
    if (error) { toast.error("Failed to archive"); return; }
    toast.success("Product archived");
    fetchData();
  };

  const startEdit = (prod: ProductWithCategory) => {
    form.reset({
      ...prod,
      max_qty: prod.max_qty || undefined,
      pack_size_label: prod.pack_size_label || undefined,
    });
    setEditingId(prod.id);
  };

  const cancelEdit = () => { setEditingId(null); setCreating(false); form.reset(); };

  const onDrop = async (acceptedFiles: File[]) => {
    const file = acceptedFiles[0];
    if (!file.type.startsWith("image/")) { toast.error("Only images allowed"); return; }
    if (file.size > 2 * 1024 * 1024) { toast.error("Max 2MB"); return; }
    // In production, upload to Supabase Storage. For now, store as data URL.
    const reader = new FileReader();
    reader.onload = () => form.setValue("image_path", reader.result as string);
    reader.readAsDataURL(file);
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({ onDrop, accept: { "image/*": [] } });

  const filteredProducts = products.filter(p =>
    p.name.toLowerCase().includes(search.toLowerCase()) ||
    p.sku.toLowerCase().includes(search.toLowerCase())
  );

  if (loading) {
    return <div className="flex items-center justify-center h-64"><Loader2 className="h-8 w-8 animate-spin text-indigo-600" /></div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Products</h1>
          <p className="text-gray-500">Manage catalogue items</p>
        </div>
        <button onClick={() => { setCreating(true); form.reset(); }} className="btn-primary">
          <Plus className="h-4 w-4 mr-2" /> Add Product
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
        <div className="flex gap-4">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search products..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
          </div>
        </div>
      </div>

      {creating && (
        <ProductForm
          form={form}
          categories={categories}
          onSubmit={onSubmit}
          onCancel={cancelEdit}
          title="Create Product"
          onDrop={onDrop}
          getRootProps={getRootProps}
          getInputProps={getInputProps}
          isDragActive={isDragActive}
        />
      )}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Image</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">SKU / Name</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Price</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Stock</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {filteredProducts.map((prod) => (
                <tr key={prod.id} className={editingId === prod.id ? "bg-indigo-50" : "hover:bg-gray-50"}>
                  {editingId === prod.id ? (
                    <td colSpan={7} className="px-4 py-4">
                      <ProductForm
                        form={form}
                        categories={categories}
                        onSubmit={onSubmit}
                        onCancel={cancelEdit}
                        title="Edit Product"
                        onDrop={onDrop}
                        getRootProps={getRootProps}
                        getInputProps={getInputProps}
                        isDragActive={isDragActive}
                      />
                    </td>
                  ) : (
                    <>
                      <td className="px-4 py-3">
                        {prod.image_path ? (
                          <img src={prod.image_path} alt={prod.name} className="h-12 w-12 object-cover rounded" />
                        ) : (
                          <div className="h-12 w-12 bg-gray-100 rounded flex items-center justify-center">
                            <FileText className="h-6 w-6 text-gray-400" />
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <p className="font-medium text-gray-900">{prod.name}</p>
                        <p className="text-sm text-gray-500">{prod.sku}</p>
                      </td>
                      <td className="px-4 py-3">
                        <span className={cn("inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                          prod.quantity_type === "WEIGHTED" ? "bg-blue-100 text-blue-800" : "bg-green-100 text-green-800")}>
                          {prod.quantity_type}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-900">
                        ₹{(prod.price_paise / 100).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                        <span className="text-xs text-gray-500 ml-1">
                          / {prod.price_basis_qty === 1000 ? "kg" : prod.price_basis_qty === 100 ? "100g" : prod.quantity_type === "PACKED" ? "pack" : prod.price_basis_qty}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span className={cn("font-medium", prod.stock_qty <= prod.low_stock_threshold && prod.stock_qty > 0 ? "text-yellow-600" : prod.stock_qty === 0 ? "text-red-600" : "text-gray-900")}>
                          {prod.quantity_type === "WEIGHTED"
                            ? `${(prod.stock_qty / 1000).toFixed(1)} kg`
                            : `${prod.stock_qty} packs`}
                        </span>
                        {prod.stock_qty <= prod.low_stock_threshold && prod.stock_qty > 0 && (
                          <span className="ml-1 text-xs text-yellow-600">(low)</span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <span className={cn("inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                          prod.active ? (prod.available ? "bg-green-100 text-green-800" : "bg-yellow-100 text-yellow-800") : "bg-gray-100 text-gray-800")}>
                          {prod.active ? (prod.available ? "Available" : "Unavailable") : "Archived"}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button onClick={() => startEdit(prod)} className="p-1.5 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded" aria-label="Edit"><Edit className="h-4 w-4" /></button>
                          <button onClick={() => handleDelete(prod.id)} className="p-1.5 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded" aria-label="Archive"><Trash2 className="h-4 w-4" /></button>
                        </div>
                      </td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {filteredProducts.length === 0 && <div className="p-12 text-center text-gray-500">No products found</div>}
      </div>

      <Toaster position="top-right" />
    </div>
  );
}

function ProductForm({
  form,
  categories,
  onSubmit,
  onCancel,
  title,
  onDrop,
  getRootProps,
  getInputProps,
  isDragActive,
}: {
  form: ReturnType<typeof useForm<Product>>;
  categories: { id: string; name: string }[];
  onSubmit: (data: Product) => void;
  onCancel: () => void;
  title: string;
  onDrop: (files: File[]) => void;
  getRootProps: () => Record<string, unknown>;
  getInputProps: () => Record<string, unknown>;
  isDragActive: boolean;
}) {
  const watchedQuantityType = form.watch("quantity_type");
  const watchedPriceBasisQty = form.watch("price_basis_qty");
  const watchedStepQty = form.watch("step_qty");

  return (
    <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-6">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">{title}</h3>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
        {/* Image Upload */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Product Image</label>
          <div {...getRootProps()} className={cn("border-2 border-dashed rounded-lg p-6 text-center cursor-pointer transition-colors", isDragActive ? "border-indigo-500 bg-indigo-50" : "border-gray-300 hover:border-indigo-400")}>
            <input {...getInputProps()} />
            <div className="flex flex-col items-center gap-2">
              <Image className="h-8 w-8 text-gray-400" />
              <p className="text-sm text-gray-600">Drag & drop image here, or click to select</p>
              <p className="text-xs text-gray-500">Max 2MB · JPG, PNG, WebP</p>
            </div>
          </div>
          {form.watch("image_path") && (
            <div className="mt-2 relative w-24 h-24">
              <img src={form.watch("image_path")} alt="Preview" className="w-full h-full object-cover rounded" />
            </div>
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">SKU *</label>
            <input {...form.register("sku")} className={cn("w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500", form.formState.errors.sku && "border-red-500")} placeholder="UNIQUE-SKU" />
            {form.formState.errors.sku && <p className="mt-1 text-sm text-red-600">{form.formState.errors.sku.message}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Name *</label>
            <input {...form.register("name")} className={cn("w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500", form.formState.errors.name && "border-red-500")} placeholder="Product name" />
            {form.formState.errors.name && <p className="mt-1 text-sm text-red-600">{form.formState.errors.name.message}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Category *</label>
            <select {...form.register("category_id")} className={cn("w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500", form.formState.errors.category_id && "border-red-500")}>
              <option value="">Select category</option>
              {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Quantity Type *</label>
            <select {...form.register("quantity_type")} className={cn("w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500", form.formState.errors.quantity_type && "border-red-500")}>
              <option value="PACKED">Packed (integer packs)</option>
              <option value="WEIGHTED">Weighted (grams/kg)</option>
            </select>
          </div>

          {/* Price fields - shown based on quantity type */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Price (₹) *</label>
            <input
              {...form.register("price_paise", { valueAsNumber: true })}
              type="number" min="0" step="0.01"
              className={cn("w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500", form.formState.errors.price_paise && "border-red-500")}
              placeholder="0.00"
            />
          </div>

          {/* Packed-specific fields */}
          {watchedQuantityType === "PACKED" && (
            <>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Unit Label *</label>
                <input {...form.register("unit_label")} className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500" placeholder="pack" defaultValue="pack" readOnly />
                {/* eslint-disable-next-line react/no-unescaped-entities */}
<p className="mt-1 text-xs text-gray-500">Fixed to "pack" for PACKED items</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Pack Size Label</label>
                <input {...form.register("pack_size_label")} className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500" placeholder="e.g., 1 kg, 500 g" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Price Basis Qty *</label>
                <input {...form.register("price_basis_qty", { valueAsNumber: true })} type="number" min="1" className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500" defaultValue={1} readOnly />
                {/* eslint-disable-next-line react/no-unescaped-entities */}
<p className="mt-1 text-xs text-gray-500">Fixed to 1 for PACKED items</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Step Qty *</label>
                <input {...form.register("step_qty", { valueAsNumber: true })} type="number" min="1" className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500" defaultValue={1} readOnly />
                <p className="mt-1 text-xs text-gray-500">Fixed to 1 for PACKED items</p>
              </div>
            </>
          )}

          {/* Weighted-specific fields */}
          {watchedQuantityType === "WEIGHTED" && (
            <>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Unit Label *</label>
                <select {...form.register("unit_label")} className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500">
                  <option value="g">g</option>
                  <option value="kg">kg</option>
                  <option value="100 g">100 g</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Price Basis *</label>
                <select {...form.register("price_basis_qty", { valueAsNumber: true })} className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500">
                  <option value={1000}>Per kg (1000 g)</option>
                  <option value={100}>Per 100 g</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Step Qty (g) *</label>
                <input {...form.register("step_qty", { valueAsNumber: true })} type="number" min="1" className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500" defaultValue={10} />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Pack Size Label</label>
                <input {...form.register("pack_size_label")} className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500" placeholder="e.g., 1 kg bag" />
              </div>
            </>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Min Qty *</label>
            <input {...form.register("min_qty", { valueAsNumber: true })} type="number" min="1" className={cn("w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500", form.formState.errors.min_qty && "border-red-500")} defaultValue={1} />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Max Qty</label>
            <input {...form.register("max_qty", { valueAsNumber: true })} type="number" min="1" className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500" placeholder="Optional" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Stock Qty *</label>
            <input {...form.register("stock_qty", { valueAsNumber: true })} type="number" min="0" className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500" defaultValue={0} />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Low Stock Threshold</label>
            <input {...form.register("low_stock_threshold", { valueAsNumber: true })} type="number" min="0" className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500" defaultValue={0} />
          </div>

          <div className="md:col-span-2 flex items-center gap-2">
            <label className="flex items-center gap-2 cursor-pointer">
              <input {...form.register("active")} type="checkbox" className="h-4 w-4 text-indigo-600 rounded border-gray-300 focus:ring-indigo-500" defaultChecked />
              <span className="text-sm text-gray-700">Active</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input {...form.register("available")} type="checkbox" className="h-4 w-4 text-indigo-600 rounded border-gray-300 focus:ring-indigo-500" defaultChecked />
              <span className="text-sm text-gray-700">Available for sale</span>
            </label>
          </div>

          <div className="md:col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea {...form.register("description")} rows={3} className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500" placeholder="Product description" />
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-4 border-t border-indigo-200">
          <button type="button" onClick={onCancel} className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50">
            <X className="h-4 w-4 mr-2 inline" /> Cancel
          </button>
          <button type="submit" className="btn-primary">
            <Save className="h-4 w-4 mr-2" /> Save
          </button>
        </div>
      </form>
    </div>
  );
}