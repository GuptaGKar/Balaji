"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { productSchema, type Product } from "@/lib/schemas";
import { useForm, type FieldErrors } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cn } from "@/lib/utils";
import { SafeImage } from "@/components/SafeImage";
import { createUuid } from "@/lib/uuid";
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
import { useDropzone, type DropzoneInputProps, type DropzoneRootProps } from "react-dropzone";
import { Toaster, toast } from "sonner";

type ProductWithCategory = Product & { categories?: { name: string }; id: string };

const PRODUCT_IMAGE_BUCKET = "product-images";
const PRODUCT_IMAGE_MAX_BYTES = 2 * 1024 * 1024;
const PRODUCT_IMAGE_URL_PREFIX = `${(process.env.NEXT_PUBLIC_SUPABASE_URL || "").replace(/\/$/, "")}/storage/v1/object/public/${PRODUCT_IMAGE_BUCKET}/`;

function managedImagePath(url: string | null | undefined): string | null {
  if (!url || !url.startsWith(PRODUCT_IMAGE_URL_PREFIX)) return null;
  return decodeURIComponent(url.slice(PRODUCT_IMAGE_URL_PREFIX.length).split("?")[0]);
}

function describeProductSaveError(err: unknown): string {
  const message = err instanceof Error ? err.message : String(err ?? "");
  const lower = message.toLowerCase();
  if (message.includes("products_sku_key") || lower.includes("duplicate key")) {
    return "That SKU already belongs to another pack. Use a different SKU; to show packs on one customer page, choose Group with another pack.";
  }
  if (lower.includes("permission denied") || lower.includes("42501")) {
    return "The database blocked this save because a column permission is missing. Ask a manager to apply migration 20261026000001_product_variant_column_grants.sql.";
  }
  if (lower.includes("row-level security")) {
    return "Only a manager can save products. Ask a manager to make this change.";
  }
  return message || "Failed to save product.";
}

function FieldError({ id, message }: { id?: string; message?: string }) {
  if (!message) return null;
  return (
    <p id={id} role="alert" className="mt-1 text-xs font-medium text-red-600">
      {message}
    </p>
  );
}

export default function ProductsTab({ canManage }: { canManage: boolean }) {
  const [products, setProducts] = useState<ProductWithCategory[]>([]);
  const [categories, setCategories] = useState<{ id: string; name: string }[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [search, setSearch] = useState("");
  const [priceRupees, setPriceRupees] = useState<number>(100);
  const [mrpRupees, setMrpRupees] = useState<string>("");
  const [stockInput, setStockInput] = useState<number>(50);
  const [isUploadingImage, setIsUploadingImage] = useState(false);

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
      variant_group_id: null,
      variant_label: "",
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

  const fetchData = useCallback(async () => {
    setLoadError(null);
    const supabase = createClient();
    const [productsRes, categoriesRes] = await Promise.all([
      supabase.from("products").select("*, categories(name)").order("name"),
      supabase.from("categories").select("id, name").eq("active", true).order("sort_order"),
    ]);
    if (productsRes.error || categoriesRes.error) {
      const message =
        productsRes.error?.message ||
        categoriesRes.error?.message ||
        "Failed to load catalogue data";
      setLoadError(message);
      setLoading(false);
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
  }, [form]);

  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  const onSubmit = async (data: Product) => {
    if (!canManage) {
      toast.error("Only a manager can save products.");
      return;
    }

    const supabase = createClient();
    try {
      const isWeighted = data.quantity_type?.toLowerCase() === "weighted";
      const sellingPriceRupees = Number(priceRupees);
      if (!Number.isFinite(sellingPriceRupees) || sellingPriceRupees < 0) {
        toast.error("Enter a valid selling price.");
        return;
      }
      const calculatedPricePaise = Math.round(sellingPriceRupees * 100);
      if (data.active !== false && calculatedPricePaise <= 0) {
        toast.error("Published products need a selling price greater than ₹0.");
        return;
      }

      const parsedMrp = mrpRupees.trim() ? Number(mrpRupees) : null;
      const calculatedMrpPaise =
        parsedMrp !== null && Number.isFinite(parsedMrp) && parsedMrp >= 0
          ? Math.round(parsedMrp * 100)
          : null;

      const generatedSku = data.sku?.trim() || `SKU-${createUuid().slice(0, 8).toUpperCase()}`;
      const selectedCategoryId =
        data.category_id || (categories.length > 0 ? categories[0].id : null);

      if (!selectedCategoryId) {
        toast.error("Please create or select a Category first.");
        return;
      }

      const {
        stock_qty: _ignoredStock,
        variant_group_id: _variantGroupId,
        variant_label: _variantLabel,
        image_path: rawImagePath,
        ...productFields
      } = data;
      const selectedGroupId = data.variant_group_id?.trim() || null;
      const variantLabel =
        data.variant_label?.trim() || (isWeighted ? null : data.pack_size_label?.trim() || null);
      const supportsGrouping = products.some((product) =>
        Object.prototype.hasOwnProperty.call(product, "variant_group_id"),
      );
      // Only send columns that exist in this database. Older deployments that
      // have not run 20261023000001 must keep saving ordinary products.
      const groupingFields = supportsGrouping
        ? { variant_group_id: selectedGroupId, variant_label: variantLabel }
        : {};
      const payload: Record<string, unknown> = {
        ...productFields,
        sku: generatedSku,
        description: data.description?.trim() || null,
        image_path: rawImagePath?.trim() || null,
        category_id: selectedCategoryId,
        quantity_type: isWeighted ? "WEIGHTED" : "PACKED",
        price_paise: calculatedPricePaise,
        mrp_paise: calculatedMrpPaise,
        price_basis_qty: isWeighted ? 1000 : 1,
        unit_label: isWeighted ? "kg" : "pack",
        pack_size_label: isWeighted ? null : data.pack_size_label || "1 pc",
        step_qty: isWeighted ? 10 : 1,
        min_qty: isWeighted ? 100 : 1,
        low_stock_threshold: isWeighted ? 5000 : 5,
        max_qty: data.max_qty || null,
        ...groupingFields,
      };

      if (supportsGrouping && selectedGroupId) {
        const groupTarget = products.find(
          (product) => (product.variant_group_id ?? product.id) === selectedGroupId,
        );
        if (groupTarget && !groupTarget.variant_group_id) {
          const { error: groupError } = await supabase
            .from("products")
            .update({
              variant_group_id: groupTarget.id,
              variant_label:
                groupTarget.variant_label || groupTarget.pack_size_label || groupTarget.name,
            })
            .eq("id", groupTarget.id);
          if (groupError) throw groupError;
        }
      }

      if (editingId) {
        const previousImage = products.find((p) => p.id === editingId)?.image_path;
        const { error } = await supabase.from("products").update(payload).eq("id", editingId);
        if (error) throw error;

        const previousPath = managedImagePath(previousImage);
        const nextPath = managedImagePath(rawImagePath);
        if (previousPath && previousPath !== nextPath) {
          await supabase.storage.from(PRODUCT_IMAGE_BUCKET).remove([previousPath]);
        }
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
        let stockWarning: string | null = null;
        if (initialStockQty > 0) {
          const { data: stockResult, error: stockError } = await supabase.rpc("adjust_stock", {
            p_product_id: inserted.id,
            p_delta_qty: initialStockQty,
            p_reason: "manual_adjustment",
            p_note: "Initial stock on product creation",
          });
          if (stockError || stockResult?.error) {
            stockWarning =
              stockError?.message || stockResult?.message || "initial stock was not recorded";
          }
        }

        if (stockWarning) {
          toast.error(
            `Product created, but ${stockWarning}. Adjust the stock from Inventory → Adjust Stock.`,
          );
        } else {
          toast.success("Product created successfully!");
        }
      }
      setEditingId(null);
      setCreating(false);
      form.reset();
      await fetchData();
    } catch (err) {
      toast.error(describeProductSaveError(err));
    }
  };

  const handleDelete = async (id: string) => {
    if (!canManage) {
      toast.error("Only a manager can archive products.");
      return;
    }
    if (!confirm("Archive this product? It will no longer be available for sale.")) return;
    const supabase = createClient();
    const { error } = await supabase.from("products").update({ active: false }).eq("id", id);
    if (error) {
      toast.error(error.message || "Failed to archive product");
      return;
    }
    toast.success("Product archived");
    await fetchData();
  };

  const startEdit = (prod: ProductWithCategory) => {
    if (!canManage) {
      toast.error("Only a manager can edit products.");
      return;
    }
    const isWeighted = prod.quantity_type?.toLowerCase() === "weighted";
    form.reset({
      ...prod,
      quantity_type: isWeighted ? "WEIGHTED" : "PACKED",
      max_qty: prod.max_qty || undefined,
      pack_size_label: prod.pack_size_label || undefined,
    });
    setPriceRupees(prod.price_paise / 100);
    setMrpRupees(prod.mrp_paise ? (prod.mrp_paise / 100).toString() : "");
    setStockInput(isWeighted ? prod.stock_qty / 1000 : prod.stock_qty);
    setEditingId(prod.id);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setCreating(false);
    setMrpRupees("");
    form.reset();
  };

  const onDrop = async (acceptedFiles: File[]) => {
    const file = acceptedFiles[0];
    if (!file) return;
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      toast.error("Use a JPEG, PNG or WebP image");
      return;
    }
    if (file.size > PRODUCT_IMAGE_MAX_BYTES) {
      toast.error("Max image size is 2 MB");
      return;
    }

    const extension =
      file.type === "image/png" ? "png" : file.type === "image/webp" ? "webp" : "jpg";
    const objectPath = `products/${createUuid()}.${extension}`;
    const supabase = createClient();
    setIsUploadingImage(true);
    try {
      const { error } = await supabase.storage.from(PRODUCT_IMAGE_BUCKET).upload(objectPath, file, {
        contentType: file.type,
        cacheControl: "31536000",
        upsert: false,
      });
      if (error) throw error;
      const { data } = supabase.storage.from(PRODUCT_IMAGE_BUCKET).getPublicUrl(objectPath);
      form.setValue("image_path", data.publicUrl, { shouldValidate: true, shouldDirty: true });
      toast.success("Image uploaded");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Image upload failed");
    } finally {
      setIsUploadingImage(false);
    }
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { "image/jpeg": [], "image/png": [], "image/webp": [] },
    maxFiles: 1,
    maxSize: PRODUCT_IMAGE_MAX_BYTES,
    multiple: false,
    disabled: isUploadingImage,
  });

  const filteredProducts = products.filter(
    (p) =>
      (p.name || "").toLowerCase().includes(search.toLowerCase()) ||
      (p.sku || "").toLowerCase().includes(search.toLowerCase()),
  );

  const groupOptions = (() => {
    const groups = new Map<string, { id: string; name: string; count: number }>();
    for (const product of products) {
      const key = product.variant_group_id ?? product.id;
      if (product.id === editingId && !product.variant_group_id) continue;
      const existing = groups.get(key);
      if (existing) {
        existing.count += 1;
        if (product.name.length < existing.name.length) existing.name = product.name;
      } else {
        groups.set(key, { id: key, name: product.name, count: 1 });
      }
    }
    return Array.from(groups.values()).sort((a, b) => a.name.localeCompare(b.name));
  })();

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
        <span className="sr-only">Loading products</span>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="rounded-2xl border border-red-200 bg-red-50 p-6 text-center">
        <AlertCircle className="mx-auto h-8 w-8 text-red-600" />
        <h2 className="mt-2 text-base font-bold text-red-900">Could not load the catalogue</h2>
        <p className="mt-1 text-sm text-red-700">{loadError}</p>
        <button
          type="button"
          onClick={() => {
            setLoading(true);
            void fetchData();
          }}
          className="mt-4 rounded-xl bg-red-600 px-4 py-2 text-sm font-bold text-white hover:bg-red-700"
        >
          Retry
        </button>
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

        {canManage && (
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
            disabled={categories.length === 0}
            title={categories.length === 0 ? "Create a category first" : "Add a new product"}
            className="flex items-center justify-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50 text-white text-sm font-bold rounded-xl shadow-md shadow-emerald-600/20 transition-all"
          >
            <Plus className="h-4 w-4" /> Add New Product
          </button>
        )}
      </div>

      {canManage && categories.length === 0 && (
        <div className="flex items-center gap-3 p-4 bg-amber-50 border border-amber-200 rounded-2xl text-amber-800 text-sm font-semibold">
          <AlertCircle className="h-5 w-5 text-amber-600 flex-shrink-0" />
          <span>
            No categories found in store. Please switch to the <strong>Categories</strong> tab to
            create a category first.
          </span>
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
          isUploadingImage={isUploadingImage}
          priceRupees={priceRupees}
          setPriceRupees={setPriceRupees}
          mrpRupees={mrpRupees}
          setMrpRupees={setMrpRupees}
          stockInput={stockInput}
          setStockInput={setStockInput}
          watchedQuantityType={watchedQuantityType}
          groupOptions={groupOptions}
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
                {canManage && <th className="px-4 py-3.5 text-right">Actions</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 text-sm text-slate-700">
              {filteredProducts.map((prod) => {
                const isWeighted = prod.quantity_type?.toLowerCase() === "weighted";
                return (
                  <tr
                    key={prod.id}
                    className={
                      editingId === prod.id
                        ? "bg-emerald-50/50"
                        : "hover:bg-slate-50/80 transition-colors"
                    }
                  >
                    {editingId === prod.id ? (
                      <td colSpan={canManage ? 8 : 7} className="p-4">
                        <ProductFormCard
                          form={form}
                          categories={categories}
                          onSubmit={onSubmit}
                          onCancel={cancelEdit}
                          title="Edit Product"
                          getRootProps={getRootProps}
                          getInputProps={getInputProps}
                          isDragActive={isDragActive}
                          isUploadingImage={isUploadingImage}
                          priceRupees={priceRupees}
                          setPriceRupees={setPriceRupees}
                          mrpRupees={mrpRupees}
                          setMrpRupees={setMrpRupees}
                          stockInput={stockInput}
                          setStockInput={setStockInput}
                          watchedQuantityType={watchedQuantityType}
                          groupOptions={groupOptions}
                          isEditing
                        />
                      </td>
                    ) : (
                      <>
                        <td className="px-4 py-3">
                          <SafeImage
                            src={prod.image_path}
                            alt={prod.name}
                            className="h-12 w-12 object-cover rounded-xl border border-slate-200"
                            fallback={
                              <div className="flex h-12 w-12 items-center justify-center rounded-xl border border-slate-200 bg-slate-100">
                                <Package className="h-6 w-6 text-slate-400" aria-hidden="true" />
                              </div>
                            }
                          />
                        </td>
                        <td className="px-4 py-3">
                          <p className="font-bold text-slate-900">{prod.name}</p>
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="text-xs text-slate-500 font-mono">{prod.sku}</p>
                            {prod.variant_group_id && (
                              <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-[10px] font-bold text-indigo-700">
                                GROUPED{prod.variant_label ? ` · ${prod.variant_label}` : ""}
                              </span>
                            )}
                          </div>
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
                            {isWeighted ? (
                              <Weight className="h-3 w-3" />
                            ) : (
                              <Layers className="h-3 w-3" />
                            )}
                            {isWeighted ? "WEIGHTED (g/kg)" : "PACKED (1 pc)"}
                          </span>
                        </td>
                        <td className="px-4 py-3 font-semibold text-slate-900">
                          <div>
                            ₹{(prod.price_paise / 100).toFixed(2)}
                            <span className="text-xs text-slate-400 ml-1">
                              / {isWeighted ? "1 kg" : prod.unit_label || "1 pc"}
                            </span>
                          </div>
                          {prod.mrp_paise && prod.mrp_paise > prod.price_paise && (
                            <div className="text-xs text-slate-400 line-through">
                              MRP ₹{(prod.mrp_paise / 100).toFixed(2)}
                            </div>
                          )}
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
                            {prod.active
                              ? prod.available
                                ? "Available"
                                : "Unavailable"
                              : "Archived"}
                          </span>
                        </td>
                        {canManage && (
                          <td className="px-4 py-3 text-right">
                            <div className="flex items-center justify-end gap-1">
                              <button
                                onClick={() => startEdit(prod)}
                                className="p-2 text-slate-500 hover:text-slate-900 hover:bg-slate-100 rounded-lg transition-colors"
                                aria-label={`Edit ${prod.name}`}
                              >
                                <Edit className="h-4 w-4" />
                              </button>
                              <button
                                onClick={() => handleDelete(prod.id)}
                                className="p-2 text-slate-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                                aria-label={`Archive ${prod.name}`}
                              >
                                <Trash2 className="h-4 w-4" />
                              </button>
                            </div>
                          </td>
                        )}
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
            {search
              ? "No products match this search."
              : canManage
                ? "No products found in catalogue. Click + Add New Product above to add one."
                : "No products found in catalogue."}
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
  isUploadingImage,
  priceRupees,
  setPriceRupees,
  mrpRupees,
  setMrpRupees,
  stockInput,
  setStockInput,
  watchedQuantityType,
  groupOptions,
  isEditing,
}: {
  form: ReturnType<typeof useForm<Product>>;
  categories: { id: string; name: string }[];
  onSubmit: (data: Product) => void;
  onCancel: () => void;
  title: string;
  getRootProps: () => DropzoneRootProps;
  getInputProps: () => DropzoneInputProps;
  isDragActive: boolean;
  isUploadingImage: boolean;
  priceRupees: number;
  setPriceRupees: (val: number) => void;
  mrpRupees: string;
  setMrpRupees: (val: string) => void;
  stockInput: number;
  setStockInput: (val: number) => void;
  watchedQuantityType: string;
  groupOptions: { id: string; name: string; count: number }[];
  isEditing: boolean;
}) {
  const isWeighted = watchedQuantityType?.toLowerCase() === "weighted";
  const imagePath = form.watch("image_path");
  const errors = form.formState.errors;

  const onInvalid = (formErrors: FieldErrors<Product>) => {
    const first = Object.values(formErrors).find((error) => Boolean(error?.message));
    toast.error(first?.message ? String(first.message) : "Please fix the highlighted fields.");
  };

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
          aria-label="Close product form"
        >
          <X className="h-5 w-5" />
        </button>
      </div>

      <form onSubmit={form.handleSubmit(onSubmit, onInvalid)} className="space-y-5" noValidate>
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
                  : "border-slate-200 bg-slate-50 hover:bg-white",
              )}
            >
              <input
                type="radio"
                value="PACKED"
                {...form.register("quantity_type")}
                className="h-4 w-4 text-emerald-600 focus:ring-emerald-500"
              />
              <div className="flex flex-col">
                <span className="text-sm font-bold text-slate-900">
                  📦 Packed Unit (1 Pc / Pack)
                </span>
                <span className="text-xs text-slate-500">
                  Priced per single piece/pack (e.g., Atta 5kg, Biscuits, Oil 1L)
                </span>
              </div>
            </label>

            <label
              className={cn(
                "flex items-center gap-3 p-3.5 border-2 rounded-xl cursor-pointer transition-all",
                isWeighted
                  ? "border-emerald-600 bg-white shadow-sm"
                  : "border-slate-200 bg-slate-50 hover:bg-white",
              )}
            >
              <input
                type="radio"
                value="WEIGHTED"
                {...form.register("quantity_type")}
                className="h-4 w-4 text-emerald-600 focus:ring-emerald-500"
              />
              <div className="flex flex-col">
                <span className="text-sm font-bold text-slate-900">
                  ⚖️ Weighted (By 1 kg / Grams)
                </span>
                <span className="text-xs text-slate-500">
                  Priced per 1 kg. Customers can select custom weight in grams/kg.
                </span>
              </div>
            </label>
          </div>
          <FieldError message={errors.quantity_type?.message} />
        </div>

        {/* Product image */}
        <div className="space-y-2">
          <label className="block text-xs font-semibold text-slate-700">Product Image</label>
          <div
            {...getRootProps()}
            className={cn(
              "flex cursor-pointer flex-col items-center justify-center gap-2 rounded-2xl border-2 border-dashed p-6 text-center transition-colors",
              isDragActive
                ? "border-emerald-500 bg-emerald-50"
                : "border-slate-300 bg-slate-50 hover:bg-white",
              isUploadingImage && "cursor-wait opacity-70",
            )}
          >
            <input {...getInputProps()} />
            <SafeImage
              src={imagePath}
              alt="Product image preview"
              className="h-28 w-28 rounded-xl border border-slate-200 object-cover"
              fallback={
                <div className="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100">
                  <ImageIcon className="h-7 w-7 text-emerald-600" aria-hidden="true" />
                </div>
              }
            />
            <p className="text-sm font-semibold text-slate-700" aria-live="polite">
              {isUploadingImage
                ? "Uploading image..."
                : imagePath
                  ? "Drop or click to replace"
                  : "Drop or click to upload"}
            </p>
            <p className="text-xs text-slate-500">JPEG, PNG or WebP · max 2 MB</p>
          </div>
          <div className="flex items-center justify-between gap-3">
            <p className="text-[11px] text-slate-500">Shown to customers in the Android app.</p>
            {imagePath && (
              <button
                type="button"
                onClick={() => form.setValue("image_path", "", { shouldDirty: true })}
                className="text-xs font-semibold text-red-600 hover:underline"
              >
                Remove image
              </button>
            )}
          </div>
          <div>
            <label
              htmlFor="product-image-url"
              className="mb-1 block text-xs font-semibold text-slate-700"
            >
              Or paste a public image URL
            </label>
            <input
              id="product-image-url"
              {...form.register("image_path")}
              type="text"
              inputMode="url"
              placeholder="https://example.com/product.jpg"
              className="w-full rounded-xl border border-slate-300 bg-slate-50 px-3.5 py-2.5 text-sm text-slate-900 focus:ring-2 focus:ring-emerald-500"
            />
            <p className="mt-1 text-[11px] text-slate-500">
              Must be a public https URL. Uploading a file above replaces this value.
            </p>
          </div>
          {imagePath?.startsWith("data:") && (
            <p
              role="status"
              className="rounded-lg bg-amber-50 px-3 py-2 text-xs font-medium text-amber-800"
            >
              This image was saved inline by an older upload and is not shown in the Android app.
              Upload it again to publish it.
            </p>
          )}
        </div>

        {/* Step 2: Product Name, Category & Dynamic Pricing */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <div>
            <label
              htmlFor="product-name"
              className="block text-xs font-semibold text-slate-700 mb-1"
            >
              Product Name *
            </label>
            <input
              id="product-name"
              {...form.register("name")}
              aria-required="true"
              aria-invalid={Boolean(errors.name)}
              aria-describedby={errors.name ? "product-name-error" : undefined}
              className={cn(
                "w-full px-3.5 py-2.5 bg-slate-50 border rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500",
                errors.name ? "border-red-400" : "border-slate-300",
              )}
              placeholder={isWeighted ? "e.g. Fresh Red Onions" : "e.g. Aashirvaad Atta 5kg"}
            />
            <FieldError id="product-name-error" message={errors.name?.message} />
          </div>

          <div>
            <label
              htmlFor="product-category"
              className="block text-xs font-semibold text-slate-700 mb-1"
            >
              Category *
            </label>
            <select
              id="product-category"
              {...form.register("category_id")}
              aria-required="true"
              aria-invalid={Boolean(errors.category_id)}
              aria-describedby={errors.category_id ? "product-category-error" : undefined}
              className={cn(
                "w-full px-3.5 py-2.5 bg-slate-50 border rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500",
                errors.category_id ? "border-red-400" : "border-slate-300",
              )}
            >
              {categories.length === 0 && <option value="">Create a category first</option>}
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            <FieldError id="product-category-error" message={errors.category_id?.message} />
          </div>

          {groupOptions.length > 0 && (
            <div>
              <label
                htmlFor="product-group"
                className="block text-xs font-semibold text-slate-700 mb-1"
              >
                Group with another pack (optional)
              </label>
              <select
                id="product-group"
                {...form.register("variant_group_id", {
                  setValueAs: (value) => (value ? value : null),
                })}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
              >
                <option value="">Not grouped</option>
                {groupOptions.map((group) => (
                  <option key={group.id} value={group.id}>
                    {group.name}
                    {group.count > 1 ? ` (${group.count} packs)` : ""}
                  </option>
                ))}
              </select>
              <span className="text-[11px] text-slate-500 mt-1 block">
                Grouped packs appear on one customer product page; each pack keeps its own SKU,
                price and stock.
              </span>
            </div>
          )}

          <div>
            <label
              htmlFor="product-variant-label"
              className="block text-xs font-semibold text-slate-700 mb-1"
            >
              Pack selector label
            </label>
            <input
              id="product-variant-label"
              {...form.register("variant_label")}
              className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
              placeholder="e.g. 500 g, 1 L, 5 kg"
            />
            <span className="text-[11px] text-slate-500 mt-1 block">
              Shown in the pack selector. Falls back to the pack size or unit.
            </span>
            <FieldError message={errors.variant_label?.message} />
          </div>

          <div>
            <label
              htmlFor="product-price"
              className="block text-xs font-semibold text-slate-700 mb-1"
            >
              {isWeighted ? "Selling Price per 1 kg (₹) *" : "Selling Price per 1 Pc / Pack (₹) *"}
            </label>
            <input
              id="product-price"
              type="number"
              min="0"
              step="0.01"
              value={Number.isFinite(priceRupees) ? priceRupees : ""}
              onChange={(e) => setPriceRupees(parseFloat(e.target.value) || 0)}
              aria-required="true"
              className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm font-bold focus:ring-2 focus:ring-emerald-500"
              placeholder={isWeighted ? "e.g. 60.00 per kg" : "e.g. 120.00 per pack"}
            />
            <span className="text-[11px] text-slate-500 mt-1 block">
              {isWeighted
                ? "Price entered is for 1 kilogram (1000g)"
                : "Price entered is for 1 unit / pack"}
            </span>
          </div>

          <div>
            <label
              htmlFor="product-mrp"
              className="block text-xs font-semibold text-slate-700 mb-1"
            >
              MRP (Printed Maximum Retail Price ₹) (Optional)
            </label>
            <input
              id="product-mrp"
              type="number"
              min="0"
              step="0.01"
              value={mrpRupees}
              onChange={(e) => setMrpRupees(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm font-semibold focus:ring-2 focus:ring-emerald-500"
              placeholder="e.g. 150.00"
            />
            <span className="text-[11px] text-slate-500 mt-1 block">
              If higher than selling price, MRP is shown crossed-through in the customer app.
            </span>
          </div>

          <div>
            <label
              htmlFor="product-stock"
              className="block text-xs font-semibold text-slate-700 mb-1"
            >
              {isWeighted
                ? "Available Stock (in Kilograms - kg) *"
                : "Available Stock (in Packs / Pcs) *"}
            </label>
            <input
              id="product-stock"
              type="number"
              min="0"
              value={Number.isFinite(stockInput) ? stockInput : ""}
              onChange={(e) => setStockInput(parseFloat(e.target.value) || 0)}
              disabled={isEditing}
              aria-required="true"
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
              <label
                htmlFor="product-pack-size"
                className="block text-xs font-semibold text-slate-700 mb-1"
              >
                Pack size (weight / volume)
              </label>
              <input
                id="product-pack-size"
                {...form.register("pack_size_label")}
                className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
                placeholder="e.g. 5 kg, 1 L, 200 g"
              />
              <span className="text-[11px] text-slate-500 mt-1 block">
                Weight or volume per single unit pack
              </span>
              <FieldError message={errors.pack_size_label?.message} />
            </div>
          )}

          <div>
            <label
              htmlFor="product-sku"
              className="block text-xs font-semibold text-slate-700 mb-1"
            >
              SKU (Auto-Generated if Blank)
            </label>
            <input
              id="product-sku"
              {...form.register("sku")}
              className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm font-mono focus:ring-2 focus:ring-emerald-500"
              placeholder="Auto-generated if left blank"
            />
            <span className="text-[11px] text-slate-500 mt-1 block">
              Each pack needs its own SKU. Use the pack-group field above to show sizes together.
            </span>
          </div>
        </div>

        <div>
          <label
            htmlFor="product-description"
            className="block text-xs font-semibold text-slate-700 mb-1"
          >
            Description (Optional)
          </label>
          <textarea
            id="product-description"
            {...form.register("description")}
            rows={2}
            className="w-full px-3.5 py-2.5 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 text-sm focus:ring-2 focus:ring-emerald-500"
            placeholder="Product details or highlights..."
          />
        </div>

        <fieldset className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <legend className="px-1 text-xs font-bold uppercase tracking-wider text-slate-600">
            Store visibility
          </legend>
          <div className="mt-2 flex flex-col gap-3 sm:flex-row sm:gap-8">
            <label className="flex items-start gap-3 text-sm">
              <input
                type="checkbox"
                {...form.register("active")}
                className="mt-0.5 h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
              />
              <span>
                <span className="block font-semibold text-slate-900">Published</span>
                <span className="block text-xs text-slate-500">
                  Clear this to archive the product.
                </span>
              </span>
            </label>
            <label className="flex items-start gap-3 text-sm">
              <input
                type="checkbox"
                {...form.register("available")}
                className="mt-0.5 h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500"
              />
              <span>
                <span className="block font-semibold text-slate-900">Available to order</span>
                <span className="block text-xs text-slate-500">
                  Uncheck while the item is temporarily unavailable.
                </span>
              </span>
            </label>
          </div>
        </fieldset>

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
            disabled={isUploadingImage}
            className="flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-60 text-white text-sm font-bold rounded-xl shadow-md shadow-emerald-600/20"
          >
            {isUploadingImage ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Save className="h-4 w-4" />
            )}
            {isUploadingImage ? "Uploading..." : "Save Product"}
          </button>
        </div>
      </form>
    </div>
  );
}
