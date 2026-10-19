"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { createClient } from "@/lib/supabase";
import { cn } from "@/lib/utils";
import {
  LayoutDashboard,
  Package,
  Settings,
  MapPin,
  Users,
  FileText,
  ShoppingCart,
  LogOut,
  Menu,
  X,
  Store,
  BarChart2,
  ClipboardList,
  ShieldCheck,
} from "lucide-react";

const navigation = [
  { name: "Dashboard", href: "/", icon: LayoutDashboard },
  { name: "Orders Queue", href: "/orders", icon: FileText },
  { name: "Catalogue", href: "/catalogue", icon: Package },
  { name: "Inventory", href: "/inventory", icon: ShoppingCart },
  { name: "Reports & Sales", href: "/reports", icon: BarChart2 },
  { name: "Audit Log", href: "/audit", icon: ClipboardList },
  { name: "Store Settings", href: "/settings", icon: Settings },
  { name: "Service Areas", href: "/service-areas", icon: MapPin },
  { name: "Staff & Riders", href: "/staff", icon: Users },
  { name: "POS & Billing", href: "/billing", icon: FileText },
];

export default function Layout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [user] = useState<{ email: string; role: string } | null>({
    email: "admin@balajiprovision.com",
    role: "Manager",
  });

  const handleSignOut = async () => {
    const supabase = createClient();
    await supabase.auth.signOut();
    window.location.href = "/login";
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 font-sans">
      {/* Mobile sidebar overlay */}
      <div
        className={cn(
          "fixed inset-0 z-40 bg-slate-900/60 backdrop-blur-sm lg:hidden transition-opacity",
          sidebarOpen ? "opacity-100" : "opacity-0 pointer-events-none",
        )}
        onClick={() => setSidebarOpen(false)}
        aria-hidden="true"
      />

      {/* Sidebar */}
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 w-64 bg-slate-900 text-white border-r border-slate-800 transform transition-transform duration-200 ease-in-out lg:translate-x-0 shadow-xl",
          sidebarOpen ? "translate-x-0" : "-translate-x-full",
        )}
        aria-label="Sidebar"
      >
        <div className="flex flex-col h-full">
          {/* Brand Header */}
          <div className="flex items-center justify-between h-16 px-5 border-b border-slate-800 bg-slate-950/40">
            <Link href="/" className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-xl bg-emerald-600 flex items-center justify-center text-white shadow-md shadow-emerald-600/30">
                <Store className="h-6 w-6" />
              </div>
              <div className="flex flex-col">
                <span className="text-base font-bold tracking-tight text-white leading-tight">Balaji Provision</span>
                <span className="text-xs text-emerald-400 font-medium">Store Admin Portal</span>
              </div>
            </Link>
            <button
              className="lg:hidden p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800"
              onClick={() => setSidebarOpen(false)}
              aria-label="Close sidebar"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* Navigation Links */}
          <nav
            className="flex-1 px-3 py-4 space-y-1 overflow-y-auto"
            role="navigation"
            aria-label="Main navigation"
          >
            {navigation.map((item) => {
              const isActive = pathname === item.href || (item.href !== "/" && pathname.startsWith(item.href));
              return (
                <Link
                  key={item.name}
                  href={item.href}
                  className={cn(
                    "flex items-center gap-3 px-3.5 py-2.5 text-sm font-medium rounded-xl transition-all duration-150",
                    isActive
                      ? "bg-emerald-600 text-white shadow-md shadow-emerald-600/20"
                      : "text-slate-300 hover:bg-slate-800/80 hover:text-white",
                  )}
                  aria-current={isActive ? "page" : undefined}
                >
                  <item.icon className={cn("h-5 w-5 flex-shrink-0", isActive ? "text-white" : "text-slate-400")} aria-hidden="true" />
                  {item.name}
                </Link>
              );
            })}
          </nav>

          {/* User & Store Status Footer */}
          <div className="p-4 border-t border-slate-800 bg-slate-950/30 space-y-3">
            <div className="flex items-center justify-between text-xs px-1">
              <span className="flex items-center gap-1.5 text-emerald-400 font-medium">
                <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
                Store Online
              </span>
              <span className="text-slate-400">v1.0.0</span>
            </div>

            <button
              onClick={handleSignOut}
              className="flex items-center gap-3 w-full px-3 py-2 text-sm font-medium text-slate-300 rounded-xl hover:bg-red-500/10 hover:text-red-400 transition-colors"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
              Sign Out
            </button>
          </div>
        </div>
      </aside>

      {/* Main content area */}
      <div className="lg:pl-64">
        {/* Top Header */}
        <header className="sticky top-0 z-30 bg-white/80 backdrop-blur-md border-b border-slate-200">
          <div className="flex items-center justify-between h-16 px-4 sm:px-6 lg:px-8">
            <button
              className="lg:hidden p-2 text-slate-600 hover:text-slate-900 rounded-lg hover:bg-slate-100"
              onClick={() => setSidebarOpen(true)}
              aria-label="Open sidebar"
            >
              <Menu className="h-6 w-6" />
            </button>

            <div className="flex-1 lg:flex-none">
              <h2 className="hidden sm:block text-sm font-semibold text-slate-700">
                Balaji Provision Store Management System
              </h2>
            </div>

            <div className="flex items-center gap-3">
              <div className="hidden sm:flex flex-col text-right">
                <span className="text-xs font-semibold text-slate-900">{user?.email}</span>
                <span className="text-[11px] text-emerald-600 font-medium">{user?.role}</span>
              </div>
              <div className="h-8 w-8 rounded-full bg-emerald-100 text-emerald-800 flex items-center justify-center font-bold text-sm border border-emerald-300 shadow-sm">
                M
              </div>
            </div>
          </div>
        </header>

        <main className="p-4 sm:p-6 lg:p-8" role="main">
          {children}
        </main>
      </div>
    </div>
  );
}
