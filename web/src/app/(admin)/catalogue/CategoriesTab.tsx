"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { categorySchema, categoryWithIdSchema, type Category, type CategoryWithId } from "@/lib/schemas";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cn } from "@/lib/utils";
import { Plus, Edit, Trash2, Loader2, Save, X } from "lucide-react";
import { Toaster, toast } from "sonner";

export default function CategoriesTab() {
  const [categories, setCategories] = useState<CategoryWithId[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const form = useForm<Category>({
    resolver: zodResolver(categorySchema),
    defaultValues: { name: "", slug: "", sort_order: 0, active: true },
  });

  const fetchCategories = async () => {
    const supabase = createClient();
    const { data, error } = await supabase
      .from("categories")
      .select("*")
      .order("sort_order", { ascending: true });
    if (error) {
      toast.error("Failed to load categories");
      return;
    }
    setCategories(data || []);
    setLoading(false);
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  const onSubmit = async (data: Category) => {
    const supabase = createClient();
    try {
      if (editingId) {
        const { error } = await supabase.from("categories").update(data).eq("id", editingId);
        if (error) throw error;
        toast.success("Category updated");
      } else {
        const { error } = await supabase.from("categories").insert(data);
        if (error) throw error;
        toast.success("Category created");
      }
      setEditingId(null);
      setCreating(false);
      form.reset();
      fetchCategories();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Operation failed");
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Delete this category? Products in it will need to be reassigned.")) return;
    const supabase = createClient();
    const { error } = await supabase.from("categories").delete().eq("id", id);
    if (error) {
      toast.error("Failed to delete");
      return;
    }
    toast.success("Category deleted");
    fetchCategories();
  };

  const startEdit = (cat: CategoryWithId) => {
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
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Categories</h1>
          <p className="text-gray-500">Manage product categories</p>
        </div>
        <button
          onClick={() => { setCreating(true); form.reset(); }}
          className="btn-primary"
        >
          <Plus className="h-4 w-4 mr-2" /> Add Category
        </button>
      </div>

      {creating && (
        <CategoryForm
          form={form}
          onSubmit={onSubmit}
          onCancel={cancelEdit}
          title="Create Category"
        />
      )}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Name</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Slug</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Sort Order</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {categories.map((cat) => (
              <tr key={cat.id} className={editingId === cat.id ? "bg-indigo-50" : "hover:bg-gray-50"}>
                {editingId === cat.id ? (
                  <td colSpan={5} className="px-6 py-4">
                    <CategoryForm form={form} onSubmit={onSubmit} onCancel={cancelEdit} title="Edit Category" />
                  </td>
                ) : (
                  <>
                    <td className="px-6 py-4 font-medium text-gray-900">{cat.name}</td>
                    <td className="px-6 py-4 text-gray-500">{cat.slug}</td>
                    <td className="px-6 py-4 text-gray-500">{cat.sort_order}</td>
                    <td className="px-6 py-4">
                      <span className={cn("inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium", cat.active ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-800")}>
                        {cat.active ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button onClick={() => startEdit(cat)} className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg" aria-label="Edit">
                          <Edit className="h-4 w-4" />
                        </button>
                        <button onClick={() => handleDelete(cat.id)} className="p-2 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded-lg" aria-label="Delete">
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
        {categories.length === 0 && (
          <div className="p-12 text-center text-gray-500">No categories yet. Click the button above to create one.</div>
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
  title,
}: {
  form: ReturnType<typeof useForm<Category>>;
  onSubmit: (data: Category) => void;
  onCancel: () => void;
  title: string;
}) {
  return (
    <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-6">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">{title}</h3>
      <form onSubmit={form.handleSubmit(onSubmit)} className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
          <input
            {...form.register("name")}
            className={cn("w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500", form.formState.errors.name && "border-red-500")}
            placeholder="Category name"
          />
          {form.formState.errors.name && <p className="mt-1 text-sm text-red-600">{form.formState.errors.name.message}</p>}
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Slug</label>
          <input
            {...form.register("slug")}
            className={cn("w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500", form.formState.errors.slug && "border-red-500")}
            placeholder="category-slug"
          />
          {form.formState.errors.slug && <p className="mt-1 text-sm text-red-600">{form.formState.errors.slug.message}</p>}
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Sort Order</label>
          <input
            {...form.register("sort_order", { valueAsNumber: true })}
            type="number"
            min="0"
            className={cn("w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500", form.formState.errors.sort_order && "border-red-500")}
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
          <select
            {...form.register("active")}
            className={cn("w-full px-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500", form.formState.errors.active && "border-red-500")}
          >
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
        </div>
        <div className="md:col-span-2 flex justify-end gap-3 pt-4 border-t border-indigo-200">
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