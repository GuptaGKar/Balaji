import { redirect } from "next/navigation";
import Layout from "@/components/Layout";
import { createServerSupabaseClient } from "@/lib/supabase-server";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const supabase = await createServerSupabaseClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) redirect("/login");

  const { data: profile } = await supabase
    .from("profiles")
    .select("role, is_active, display_name")
    .eq("id", user.id)
    .single();

  if (!profile || !profile.is_active || !["staff", "manager"].includes(profile.role)) {
    await supabase.auth.signOut();
    redirect("/login?error=not_staff");
  }

  return (
    <Layout user={{ email: user.email ?? profile.display_name ?? "Signed in", role: profile.role }}>
      {children}
    </Layout>
  );
}
