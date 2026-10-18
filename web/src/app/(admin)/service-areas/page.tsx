"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { serviceAreaSchema, type ServiceArea, serviceAreaKindEnum } from "@/lib/schemas";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { cn } from "@/lib/utils";
import { Plus, Edit, Trash2, Loader2, Save, X, MapPin, Building2 } from "lucide-react";
import { Toaster, toast } from "sonner";

type ServiceAreaWithId = ServiceArea & { id: string };

export default function ServiceAreasPage() {
  const [areas, setAreas] = useState<ServiceAreaWithId[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const form = useForm<ServiceArea>({
    resolver: zodResolver(serviceAreaSchema),
    defaultValues: { kind: "pincode", value: "", active: true },
  });

  const fetchAreas = async () => {
    const supabase = createClient();
    const { data, error } = await supabase.from("service_areas").select("*").order("kind").order("value");
    if (error) { toast.error("Failed to load"); return; }
    setAreas(data || []);
    setLoading(false);
  };

  useEffect(() => { fetchAreas(); }, []);

  const onSubmit = async (data: ServiceArea) => {
    const supabase = createClient();
    try {
      if (editingId) {
        const { error } = await supabase.from("service_areas").update(data).eq("id", editingId);
        if (error) throw error;
        toast.success("Updated");
      } else {
        const { error } = await supabase.from("service_areas").insert(data);
        if (error) {
          if (error.code === "23505") throw new Error("This PIN/neighbourhood already exists");
          throw error;
        }
        toast.success("Created");
      }
      setEditingId(null); setCreating(false); form.reset(); fetchAreas();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Operation failed");
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Deactivate this service area? Customers in this area won't be able to order.")) return;
    const supabase = createClient();
    const { error } = await supabase.from("service_areas").update({ active: false }).eq("id", id);
    if (error) { toast.error("Failed"); return; }
    toast.success("Deactivated"); fetchAreas();
  };

  const startEdit = (area: ServiceAreaWithId) => { form.reset(area); setEditingId(area.id); };
  const cancelEdit = () => { setEditingId(null); setCreating(false); form.reset(); };

  if (loading) return <div className="flex items-center justify-center h-64"><Loader2 className="h-8 w-8 animate-spin text-indigo-600" /></div>;

  const KindIcon = ({ kind }: { kind: "pincode" | "neighbourhood" }) =>
    kind === "pincode" ? <MapPin className="h-3.5 w-3.5" /> : <Building2 className="h-3.5 w-3.5" />;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Service Areas</h1>
          <p className="text-gray-500">Manage delivery PIN codes and neighbourhoods</p>
        </div>
        <button onClick={() => { setCreating(true); form.reset(); }} className="btn-primary">
          <Plus className="h-4 w-4 mr-2" /> Add Area
        </button>
      </div>

      {creating && (
        <ServiceAreaForm form={form} onSubmit={onSubmit} onCancel={cancelEdit} title="Add Service Area" />
      )}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Type</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Value</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {areas.map((area) => (
              <tr key={area.id} className={editingId === area.id ? "bg-indigo-50" : "hover:bg-gray-50"}>
                {editingId === area.id ? (
                  <td colSpan={4} className="px-6 py-4">
                    <ServiceAreaForm form={form} onSubmit={onSubmit} onCancel={cancelEdit} title="Edit Service Area" />
                  </td>
                ) : (
                  <>
                    <td className="px-6 py-4">
                      <span className={cn("inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-full",
                        area.kind === "pincode" ? "bg-blue-100 text-blue-800" : "bg-green-100 text-green-800")}>
                        <KindIcon kind={area.kind} />
                        {area.kind}
                      </span>
                    </td>
                    <td className="px-6 py-4 font-medium text-gray-900">{area.value}</td>
                    <td className="px-6 py-4">
                      <span className={cn("inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium",
                        area.active ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-800")}>
                        {area.active ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button onClick={() => startEdit(area)} className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg"><Edit className="h-4 w-4" /></button>
                        <button onClick={() => handleDelete(area.id)} className="p-2 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded-lg"><Trash2 className="h-4 w-4" /></button>
                      </div>
                    </td>
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
        {areas.length === 0 && <div className="p-12 text-center text-gray-500">No service areas configured</div>}
      </div>

      <Toaster position="top-right" />
    </div>
  );
}

function ServiceAreaForm({
  form, onSubmit, onCancel, title,
}: { form: ReturnType<typeof useForm<ServiceArea>>; onSubmit: (data: ServiceArea) => void; onCancel: () => void; title: string }) {
  return (
    <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-6">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">{title}</h3>
      <form onSubmit={form.handleSubmit(onSubmit)} className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Type *</label>
          <select {...form.register("kind")} className={cn("w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500", form.formState.errors.kind && "border-red-500")}>
            <option value="pincode">PIN Code</option>
            <option value="neighbourhood">Neighbourhood</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Value *</label>
          <input {...form.register("value")} className={cn("w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500", form.formState.errors.value && "border-red-500")} placeholder={form.watch("kind") === "pincode" ? "e.g., 122001" : "e.g., Sector 14"} />
          {form.formState.errors.value && <p className="mt-1 text-sm text-red-600">{form.formState.errors.value.message}</p>}
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
          <select {...form.register("active")} className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500">
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
        </div>
        <div className="md:col-span-3 flex justify-end gap-3 pt-4 border-t border-indigo-200">
          <button type="button" onClick={onCancel} className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"><X className="h-4 w-4 mr-2 inline" /> Cancel</button>
          <button type="submit" className="btn-primary"><Save className="h-4 w-4 mr-2" /> Save</button>
        </div>
      </form>
    </div>
  );
}