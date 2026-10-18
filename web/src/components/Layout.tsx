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
  UserCog,
  Bell,
  BarChart2,
  ClipboardList,
} from "lucide-react";

const navigation = [
  { name: "Dashboard", href: "/", icon: LayoutDashboard },
  { name: "Orders", href: "/orders", icon: FileText },
  { name: "Catalogue", href: "/catalogue", icon: Package },
  { name: "Inventory", href: "/inventory", icon: ShoppingCart },
  { name: "Reports", href: "/reports", icon: BarChart2 },
  { name: "Audit Log", href: "/audit", icon: ClipboardList },
  { name: "Settings", href: "/settings", icon: Settings },
  { name: "Service Areas", href: "/service-areas", icon: MapPin },
  { name: "Staff & Riders", href: "/staff", icon: Users },
  { name: "Billing", href: "/billing", icon: FileText },
];

export default function Layout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [user, setUser] = useState<{ email: string; role: string } | null>(null);

  const handleSignOut = async () => {
    const supabase = createClient();
    await supabase.auth.signOut();
    window.location.href = "/login";
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Mobile sidebar overlay */}
      <div
        className={cn(
          "fixed inset-0 z-40 bg-black/50 lg:hidden transition-opacity",
          sidebarOpen ? "opacity-100" : "opacity-0 pointer-events-none",
        )}
        onClick={() => setSidebarOpen(false)}
        aria-hidden="true"
      />

      {/* Sidebar */}
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 w-64 bg-white border-r border-gray-200 transform transition-transform duration-200 ease-in-out lg:translate-x-0",
          sidebarOpen ? "translate-x-0" : "-translate-x-full",
        )}
        aria-label="Sidebar"
      >
        <div className="flex flex-col h-full">
          <div className="flex items-center justify-between h-16 px-4 border-b border-gray-200">
            <Link href="/" className="flex items-center gap-2">
              <Store className="h-8 w-8 text-indigo-600" />
              <span className="text-xl font-bold text-gray-900">Balaji Store</span>
            </Link>
            <button
              className="lg:hidden p-2 text-gray-500 hover:text-gray-700"
              onClick={() => setSidebarOpen(false)}
              aria-label="Close sidebar"
            >
              <X className="h-6 w-6" />
            </button>
          </div>

          <nav
            className="flex-1 px-2 py-4 space-y-1 overflow-y-auto"
            role="navigation"
            aria-label="Main navigation"
          >
            {navigation.map((item) => {
              const isActive = pathname === item.href || pathname.startsWith(item.href + "/");
              return (
                <Link
                  key={item.name}
                  href={item.href}
                  className={cn(
                    "flex items-center gap-3 px-3 py-2.5 text-sm font-medium rounded-lg transition-colors",
                    isActive
                      ? "bg-indigo-50 text-indigo-700"
                      : "text-gray-700 hover:bg-gray-100 hover:text-gray-900",
                  )}
                  aria-current={isActive ? "page" : undefined}
                >
                  <item.icon className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
                  {item.name}
                </Link>
              );
            })}
          </nav>

          <div className="p-4 border-t border-gray-200">
            <button
              onClick={handleSignOut}
              className={cn(
                "flex items-center gap-3 w-full px-3 py-2.5 text-sm font-medium text-gray-700 rounded-lg hover:bg-gray-100 transition-colors",
              )}
            >
              <LogOut className="h-5 w-5" aria-hidden="true" />
              Sign out
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <div className="lg:pl-64">
        {/* Top bar */}
        <header className="sticky top-0 z-30 bg-white border-b border-gray-200">
          <div className="flex items-center justify-between h-16 px-4 sm:px-6 lg:px-8">
            <button
              className="lg:hidden p-2 text-gray-500 hover:text-gray-700"
              onClick={() => setSidebarOpen(true)}
              aria-label="Open sidebar"
            >
              <Menu className="h-6 w-6" />
            </button>

            <div className="flex-1 lg:flex-none" />

            <div className="flex items-center gap-4">
              <div className="hidden sm:block text-sm text-gray-500">
                {user?.email || "Loading..."}
              </div>
              <div className="hidden sm:block px-2 py-1 text-xs font-medium text-indigo-700 bg-indigo-50 rounded-full">
                {user?.role || "staff"}
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
