import Layout from "@/components/Layout";
import Dashboard from "./Dashboard";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <Layout>{children}</Layout>;
}