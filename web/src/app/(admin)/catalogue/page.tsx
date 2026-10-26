import { createServerSupabaseClient } from "@/lib/supabase-server";
import CatalogueClient from "./CatalogueClient";

/**
 * Resolve the role on the server so staff never see write controls. RLS is the
 * real enforcement point; this only removes misleading buttons from the UI.
 */
export default async function CataloguePage() {
  const supabase = await createServerSupabaseClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  let canManage = false;
  if (user) {
    const { data: profile } = await supabase
      .from("profiles")
      .select("role, is_active")
      .eq("id", user.id)
      .single();
    canManage = profile?.is_active === true && profile.role === "manager";
  }

  return <CatalogueClient canManage={canManage} />;
}
