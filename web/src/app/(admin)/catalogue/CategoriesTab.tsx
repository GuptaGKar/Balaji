"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { categorySchema, type Category, type CategoryWithId } from "@/lib/schemas";
import { useForm, type FieldErrors } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cn } from "@/lib/utils";
import { slugify } from "@/lib/slug";
import { createUuid } from "@/lib/uuid";
import { SafeImage } from "@/components/SafeImage";
import { Plus, Edit, Trash2, Loader2, Save, X, ImageIcon, AlertCircle } from "lucide-react";
import { Toaster, toast } from "sonner";

const CATEGORY_IMAGE_BUCKET = "category-images";
const CATEGORY_IMAGE_MAX_BYTES = 2 * 1024 * 1024;
const CATEGORY_IMAGE_URL_PREFIX = `${(process.env.NEXT_PUBLIC_SUPABASE_URL || "").replace(/\/$/, "")}/storage/v1/object/public/${CATEGORY_IMAGE_BUCKET}/`;

function managedImagePath(url: string | null | undefined): string | null {
  if (!url || !url.startsWith(CATEGORY_IMAGE_URL_PREFIX)) return null;
  return decodeURIComponent(url.slice(CATEGORY_IMAGE_URL_PREFIX.length).split("?")[0]);
}

export default function CategoriesTab({ canManage }: { canManage: boolean }) {
  const [categories, setCategories] = useState<CategoryWithId[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [isUploadingImage, setIsUploadingImage] = useState(false);

  const form = useForm<Category>({
    resolver: zodResolver(categorySchema),
    defaultValues: { name: "", slug: "", sort_order: 0, active: true, image_path: "" },
  });

  const fetchCategories = useCallback(async () => {
    setLoadError(null);
    const supabase = createClient();
    const { data, error } = await supabase
      .from("categories")
      .select("*")
      .order("sort_order", { ascending: true });
    if (error) {
      setLoadError(error.message);
      setLoading(false);
      toast.error("Failed to load categories");
      return;
    }
    setCategories(data || []);
    setLoading(false);
  }, []);

  useEffect(() => {
    void fetchCategories();
  }, [fetchCategories]);

  const onSubmit = async (data: Category) => {
    if (!canManage) {
      toast.error("Only a manager can change categories.");
      return;
    }
    const supabase = createClient();
    try {
      const generatedSlug = data.slug.trim() || slugify(data.name);
      if (!generatedSlug) {
        toast.error("Add a category name or a slug using lowercase letters, digits and hyphens.");
        return;
      }
      const { image_path: imagePath, ...categoryFields } = data;
      const supportsImages = categories.some((category) =>
        Object.prototype.hasOwnProperty.call(category, "image_path"),
      );
      const payload = {
        ...categoryFields,
        slug: generatedSlug,
        ...(supportsImages || imagePath ? { image_path: imagePath?.trim() || null } : {}),
      };

      if (editingId) {
        const previousImage = categories.find((category) => category.id === editingId)?.image_path;
        const { error } = await supabase.from("categories").update(payload).eq("id", editingId);
        if (error) throw error;

        const previousPath = managedImagePath(previousImage);
        const nextPath = managedImagePath(imagePath);
        if (previousPath && previousPath !== nextPath) {
          await supabase.storage.from(CATEGORY_IMAGE_BUCKET).remove([previousPath]);
        }
        toast.success("Category updated successfully!");
      } else {
        const { error } = await supabase.from("categories").insert(payload);
        if (error) throw error;
        toast.success("Category created successfully!");
      }
      setEditingId(null);
      setCreating(false);
      form.reset();
      await fetchCategories();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Operation failed";
      if (message.toLowerCase().includes("duplicate key")) {
        toast.error("That slug already belongs to another category. Choose a different slug.");
      } else if (message.toLowerCase().includes("row-level security")) {
        toast.error("Only a manager can change categories.");
      } else {
        toast.error(message);
      }
    }
  };

  const handleDelete = async (id: string) => {
    if (!canManage) {
      toast.error("Only a manager can delete categories.");
      return;
    }
    if (!confirm("Delete this category? Products in it will need to be reassigned.")) return;
    const supabase = createClient();
    const imagePath = managedImagePath(
      categories.find((category) => category.id === id)?.image_path,
    );
    const { error } = await supabase.from("categories").delete().eq("id", id);
    if (error) {
      if (error.message.toLowerCase().includes("foreign key")) {
        toast.error("This category still has products. Move them to another category first.");
      } else if (error.message.toLowerCase().includes("row-level security")) {
        toast.error("Only a manager can delete categories.");
      } else {
        toast.error(error.message || "Failed to delete category");
      }
      return;
    }
    if (imagePath) {
      await supabase.storage.from(CATEGORY_IMAGE_BUCKET).remove([imagePath]);
    }
    toast.success("Category deleted");
    await fetchCategories();
  };

  const uploadImage = async (file: File) => {
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      toast.error("Use a JPEG, PNG or WebP image");
      return;
    }
    if (file.size > CATEGORY_IMAGE_MAX_BYTES) {
      toast.error("Max image size is 2 MB");
      return;
    }

    const extension =
      file.type === "image/png" ? "png" : file.type === "image/webp" ? "webp" : "jpg";
    const objectPath = `categories/${createUuid()}.${extension}`;
    const supabase = createClient();
    setIsUploadingImage(true);
    try {
      const { error } = await supabase.storage
        .from(CATEGORY_IMAGE_BUCKET)
        .upload(objectPath, file, {
          contentType: file.type,
          cacheControl: "31536000",
          upsert: false,
        });
      if (error) throw error;
      const { data } = supabase.storage.from(CATEGORY_IMAGE_BUCKET).getPublicUrl(objectPath);
      form.setValue("image_path", data.publicUrl, { shouldValidate: true, shouldDirty: true });
      toast.success("Category image uploaded");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Image upload failed");
    } finally {
      setIsUploadingImage(false);
    }
  };

  const startEdit = (cat: CategoryWithId) => {
    if (!canManage) {
      toast.error("Only a manager can edit categories.");
      return;
    }
    form.reset(cat);
    setEditingId(cat.id);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setCreating(false);
    form.reset();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-600" />
        <span className="sr-only">Loading categories</span>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="rounded-2xl border border-red-200 bg-red-50 p-6 text-center">
        <AlertCircle className="mx-auto h-8 w-8 text-red-600" />
        <h2 className="mt-2 text-base font-bold text-red-900">Could not load categories</h2>
        <p className="mt-1 text-sm text-red-700">{loadError}</p>
        <button
          type="button"
          onClick={() => {
            setLoading(true);
            void fetchCategories();
          }}
          className="mt-4 rounded-xl bg-red-600 px-4 py-2 text-sm font-bold text-white hover:bg-red-700"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Categories</h1>
          <p className="text-gray-500">
            {canManage ? "Manage product categories" : "View product categories"}
          </p>
        </div>
        {canManage && (
          <button
            onClick={() => {
              setCreating(true);
              form.reset();
            }}
            className="btn-primary"
          >
            <Plus className="h-4 w-4 mr-2" /> Add Category
          </button>
        )}
      </div>

      {creating && (
        <CategoryForm
          form={form}
          onSubmit={onSubmit}
          onCancel={cancelEdit}
          onImageUpload={uploadImage}
          isUploadingImage={isUploadingImage}
          title="Create Category"
        />
      )}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Image
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Name
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Slug
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Sort Order
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Status
              </th>
              {canManage && (
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Actions
                </th>
              )}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {categories.map((cat) => (
              <tr
                key={cat.id}
                className={editingId === cat.id ? "bg-indigo-50" : "hover:bg-gray-50"}
              >
                {editingId === cat.id ? (
                  <td colSpan={canManage ? 6 : 5} className="px-6 py-4">
                    <CategoryForm
                      form={form}
                      onSubmit={onSubmit}
                      onCancel={cancelEdit}
                      onImageUpload={uploadImage}
                      isUploadingImage={isUploadingImage}
                      title="Edit Category"
                    />
                  </td>
                ) : (
                  <>
                    <td className="px-6 py-4">
                      <SafeImage
                        src={cat.image_path}
                        alt={`${cat.name} category image`}
                        className="h-11 w-11 rounded-lg border border-gray-200 object-cover"
                        fallback={
                          <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-gray-100 text-gray-400">
                            <ImageIcon className="h-5 w-5" aria-hidden="true" />
                          </div>
                        }
                      />
                    </td>
                    <td className="px-6 py-4 font-medium text-gray-900">{cat.name}</td>
                    <td className="px-6 py-4 text-gray-500">{cat.slug}</td>
                    <td className="px-6 py-4 text-gray-500">{cat.sort_order}</td>
                    <td className="px-6 py-4">
                      <span
                        className={cn(
                          "inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium",
                          cat.active ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-800",
                        )}
                      >
                        {cat.active ? "Active" : "Inactive"}
                      </span>
                    </td>
                    {canManage && (
                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => startEdit(cat)}
                            className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg"
                            aria-label={`Edit ${cat.name}`}
                          >
                            <Edit className="h-4 w-4" />
                          </button>
                          <button
                            onClick={() => handleDelete(cat.id)}
                            className="p-2 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded-lg"
                            aria-label={`Delete ${cat.name}`}
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                    )}
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
        {categories.length === 0 && (
          <div className="p-12 text-center text-gray-500">
            No categories yet. Click the button above to create one.
          </div>
        )}
      </div>

      <Toaster position="top-right" />
    </div>
  );
}

function CategoryForm({
  form,
  onSubmit,
  onCancel,
  onImageUpload,
  isUploadingImage,
  title,
}: {
  form: ReturnType<typeof useForm<Category>>;
  onSubmit: (data: Category) => void;
  onCancel: () => void;
  onImageUpload: (file: File) => Promise<void>;
  isUploadingImage: boolean;
  title: string;
}) {
  const imagePath = form.watch("image_path");
  const name = form.watch("name");
  const slug = form.watch("slug");
  const errors = form.formState.errors;
  const previewSlug = slug?.trim() || slugify(name || "");

  const onInvalid = (formErrors: FieldErrors<Category>) => {
    const first = Object.values(formErrors).find((error) => Boolean(error?.message));
    toast.error(first?.message ? String(first.message) : "Please fix the highlighted fields.");
  };

  return (
    <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-6">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">{title}</h3>
      <form
        onSubmit={form.handleSubmit(onSubmit, onInvalid)}
        className="grid grid-cols-1 md:grid-cols-2 gap-4"
        noValidate
      >
        <div className="md:col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">Category Image</label>
          <div className="flex flex-wrap items-center gap-4 rounded-lg border border-indigo-200 bg-white p-4">
            <SafeImage
              src={imagePath}
              alt="Category image preview"
              className="h-20 w-20 rounded-lg border border-gray-200 object-cover"
              fallback={
                <div className="flex h-20 w-20 items-center justify-center rounded-lg bg-gray-100 text-gray-400">
                  <ImageIcon className="h-8 w-8" aria-hidden="true" />
                </div>
              }
            />
            <div className="flex-1 space-y-2">
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                disabled={isUploadingImage}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void onImageUpload(file);
                  event.target.value = "";
                }}
                aria-label="Upload category image"
                className="block w-full text-sm text-gray-600 file:mr-3 file:rounded-md file:border-0 file:bg-indigo-600 file:px-3 file:py-2 file:text-sm file:font-semibold file:text-white hover:file:bg-indigo-700 disabled:opacity-60"
              />
              <p className="text-xs text-gray-500">
                JPEG, PNG or WebP · max 2 MB. Shown on the Android storefront.
              </p>
              <div>
                <label
                  htmlFor="category-image-url"
                  className="mb-1 block text-xs font-medium text-gray-700"
                >
                  Or paste a public image URL
                </label>
                <input
                  id="category-image-url"
                  {...form.register("image_path")}
                  type="text"
                  inputMode="url"
                  placeholder="https://example.com/category.jpg"
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500"
                />
              </div>
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
          </div>
        </div>
        <div>
          <label htmlFor="category-name" className="block text-sm font-medium text-gray-700 mb-1">
            Name *
          </label>
          <input
            id="category-name"
            {...form.register("name")}
            aria-required="true"
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name ? "category-name-error" : undefined}
            className={cn(
              "w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500",
              errors.name && "border-red-500",
            )}
            placeholder="Category name"
          />
          {errors.name && (
            <p id="category-name-error" role="alert" className="mt-1 text-sm text-red-600">
              {errors.name.message}
            </p>
          )}
        </div>
        <div>
          <label htmlFor="category-slug" className="block text-sm font-medium text-gray-700 mb-1">
            Slug (optional)
          </label>
          <input
            id="category-slug"
            {...form.register("slug")}
            aria-invalid={Boolean(errors.slug)}
            aria-describedby="category-slug-help"
            className={cn(
              "w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500",
              errors.slug && "border-red-500",
            )}
            placeholder="Generated from the name if blank"
          />
          <p id="category-slug-help" className="mt-1 text-xs text-gray-500">
            {previewSlug ? (
              <>
                Customer URL slug: <span className="font-mono font-semibold">{previewSlug}</span>
              </>
            ) : (
              "Use lowercase letters, digits and hyphens. Generated from the name if blank."
            )}
          </p>
          {errors.slug && (
            <p role="alert" className="mt-1 text-sm text-red-600">
              {errors.slug.message}
            </p>
          )}
        </div>
        <div>
          <label
            htmlFor="category-sort-order"
            className="block text-sm font-medium text-gray-700 mb-1"
          >
            Sort Order
          </label>
          <input
            id="category-sort-order"
            {...form.register("sort_order", { valueAsNumber: true })}
            type="number"
            min="0"
            className={cn(
              "w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500",
              errors.sort_order && "border-red-500",
            )}
          />
          {errors.sort_order && (
            <p role="alert" className="mt-1 text-sm text-red-600">
              {errors.sort_order.message}
            </p>
          )}
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
          <select
            {...form.register("active", {
              setValueAs: (value) => value === "true" || value === true,
            })}
            className={cn(
              "w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500",
              form.formState.errors.active && "border-red-500",
            )}
          >
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
        </div>
        <div className="md:col-span-2 flex justify-end gap-3 pt-4 border-t border-indigo-200">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
          >
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
