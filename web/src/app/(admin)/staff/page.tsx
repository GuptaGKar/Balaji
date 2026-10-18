"use client";

import { useEffect, useState } from "react";
import { createClient } from "@/lib/supabase";
import { createStaffSchema, type CreateStaffInput } from "@/lib/schemas";
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
  Shield,
  User,
  Key,
  RotateCcw,
  Eye,
  EyeOff,
} from "lucide-react";
import { Toaster, toast } from "sonner";

type Staff = {
  id: string;
  role: "staff" | "delivery" | "manager";
  display_name: string;
  phone: string;
  login_id: string | null;
  is_active: boolean;
  must_change_password: boolean;
};

export default function StaffPage() {
  const [staff, setStaff] = useState<Staff[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [showOTP, setShowOTP] = useState<string | null>(null);

  const form = useForm<CreateStaffInput>({
    resolver: zodResolver(createStaffSchema),
    defaultValues: { login_id: "", display_name: "", phone: "", role: "staff" },
  });

  const fetchStaff = async () => {
    const supabase = createClient();
    const { data, error } = await supabase
      .from("profiles")
      .select("*")
      .neq("role", "customer")
      .order("role")
      .order("display_name");
    if (error) {
      toast.error("Failed to load");
      return;
    }
    setStaff(data || []);
    setLoading(false);
  };

  useEffect(() => {
    fetchStaff();
  }, []);

  const onSubmit = async (data: CreateStaffInput) => {
    try {
      const res = await fetch("/api/staff", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: "create", ...data }),
      });
      const json = await res.json();
      if (!res.ok) throw new Error(json.error || "Failed to create");

      setShowOTP(json.tempPassword);
      setCreating(false);
      form.reset();
      fetchStaff();
      toast.success(`Account created! Share the one-time password securely.`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to create");
    }
  };

  const handleReset = async (id: string, loginId: string) => {
    if (!confirm(`Reset password for ${loginId}? A new one-time password will be generated.`))
      return;
    try {
      const res = await fetch("/api/staff", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: "reset_password", user_id: id }),
      });
      const json = await res.json();
      if (!res.ok) throw new Error(json.error || "Reset failed");
      setShowOTP(json.tempPassword);
      fetchStaff();
      toast.success("Password reset! Share the new one-time password securely.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Reset failed");
    }
  };

  const handleToggleActive = async (id: string, current: boolean) => {
    const supabase = createClient();
    const { error } = await supabase.from("profiles").update({ is_active: !current }).eq("id", id);
    if (error) {
      toast.error("Failed");
      return;
    }
    fetchStaff();
    toast.success(current ? "Deactivated" : "Activated");
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
          <h1 className="text-2xl font-bold text-gray-900">Staff & Riders</h1>
          <p className="text-gray-500">Manage staff and delivery rider accounts</p>
        </div>
        <button
          onClick={() => {
            setCreating(true);
            form.reset();
          }}
          className="btn-primary"
        >
          <Plus className="h-4 w-4 mr-2" /> Add Staff / Rider
        </button>
      </div>

      {creating && (
        <StaffForm
          form={form}
          onSubmit={onSubmit}
          onCancel={() => {
            setCreating(false);
            form.reset();
          }}
        />
      )}

      {showOTP && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-6" role="alert">
          <div className="flex items-center justify-between">
            <div>
              <h4 className="font-semibold text-green-800">One-Time Password Generated</h4>
              <p className="text-sm text-green-700 mt-1">
                Share this securely with the rider/staff. They must change it on first login.
              </p>
              <code className="block mt-2 p-3 bg-white border border-green-300 rounded font-mono text-lg tracking-wider select-all">
                {showOTP}
              </code>
            </div>
            <button
              onClick={() => setShowOTP(null)}
              className="p-2 text-green-600 hover:bg-green-100 rounded"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>
      )}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Name
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Role
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Login ID
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Phone
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Status
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Password
              </th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {staff.map((s) => (
              <tr key={s.id} className="hover:bg-gray-50">
                <td className="px-6 py-4 font-medium text-gray-900">{s.display_name}</td>
                <td className="px-6 py-4">
                  <span
                    className={cn(
                      "inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-full",
                      s.role === "manager"
                        ? "bg-purple-100 text-purple-800"
                        : s.role === "staff"
                          ? "bg-blue-100 text-blue-800"
                          : "bg-orange-100 text-orange-800",
                    )}
                  >
                    {s.role === "delivery" ? (
                      <Shield className="h-3.5 w-3.5" />
                    ) : (
                      <User className="h-3.5 w-3.5" />
                    )}
                    {s.role}
                  </span>
                </td>
                <td className="px-6 py-4 text-gray-500 font-mono">{s.login_id || "—"}</td>
                <td className="px-6 py-4 text-gray-500">{s.phone}</td>
                <td className="px-6 py-4">
                  <span
                    className={cn(
                      "inline-flex items-center px-2 py-1 text-xs font-medium rounded-full",
                      s.is_active ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-800",
                    )}
                  >
                    {s.is_active ? "Active" : "Inactive"}
                  </span>
                </td>
                <td className="px-6 py-4">
                  {s.must_change_password ? (
                    <span className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-full bg-yellow-100 text-yellow-800">
                      <Key className="h-3.5 w-3.5" /> Must change
                    </span>
                  ) : (
                    <span className="text-xs text-gray-500">Set</span>
                  )}
                </td>
                <td className="px-6 py-4 text-right">
                  <div className="flex items-center justify-end gap-1">
                    {s.role !== "manager" && (
                      <>
                        <button
                          onClick={() => handleReset(s.id, s.login_id || "")}
                          className="p-2 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded"
                          aria-label="Reset password"
                        >
                          <RotateCcw className="h-4 w-4" />
                        </button>
                        <button
                          onClick={() => handleToggleActive(s.id, s.is_active)}
                          className="p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded"
                          aria-label={s.is_active ? "Deactivate" : "Activate"}
                        >
                          {s.is_active ? (
                            <Eye className="h-4 w-4" />
                          ) : (
                            <EyeOff className="h-4 w-4" />
                          )}
                        </button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {staff.length === 0 && (
          <div className="p-12 text-center text-gray-500">No staff or riders yet</div>
        )}
      </div>

      <Toaster position="top-right" />
    </div>
  );
}

function StaffForm({
  form,
  onSubmit,
  onCancel,
}: {
  form: ReturnType<typeof useForm<CreateStaffInput>>;
  onSubmit: (data: CreateStaffInput) => void;
  onCancel: () => void;
}) {
  return (
    <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-6">
      <h3 className="text-lg font-semibold text-gray-900 mb-4">Create Staff / Rider Account</h3>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="grid grid-cols-1 md:grid-cols-2 gap-4"
      >
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Role *</label>
          <select
            {...form.register("role")}
            className={cn(
              "w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500",
              form.formState.errors.role && "border-red-500",
            )}
          >
            <option value="staff">Staff (order management)</option>
            <option value="delivery">Delivery Rider</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Login ID *</label>
          <input
            {...form.register("login_id")}
            className={cn(
              "w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500 uppercase",
              form.formState.errors.login_id && "border-red-500",
            )}
            placeholder="e.g., STAFF-001 or RIDER-A-001"
          />
          <p className="mt-1 text-xs text-gray-500">Uppercase letters, numbers, hyphens only</p>
          {form.formState.errors.login_id && (
            <p className="mt-1 text-sm text-red-600">{form.formState.errors.login_id.message}</p>
          )}
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Display Name *</label>
          <input
            {...form.register("display_name")}
            className={cn(
              "w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500",
              form.formState.errors.display_name && "border-red-500",
            )}
            placeholder="Full name"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Phone *</label>
          <input
            {...form.register("phone")}
            className={cn(
              "w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500",
              form.formState.errors.phone && "border-red-500",
            )}
            placeholder="9837803293"
          />
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
            <Save className="h-4 w-4 mr-2" /> Create Account
          </button>
        </div>
      </form>
    </div>
  );
}
