"use client";

import { useState } from "react";
import CategoriesTab from "./CategoriesTab";
import ProductsTab from "./ProductsTab";
import { Package, FolderTree } from "lucide-react";
import { cn } from "@/lib/utils";

export default function CataloguePage() {
  const [activeTab, setActiveTab] = useState<"products" | "categories">("products");

  return (
    <div className="space-y-6 max-w-7xl mx-auto px-2 sm:px-4 pb-12">
      {/* Header & Tab Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Catalogue Management</h1>
          <p className="text-xs font-medium text-slate-500 mt-0.5">Manage products, pricing, stock levels, and store categories</p>
        </div>

        {/* Tab Switcher */}
        <div className="inline-flex p-1 bg-slate-100 rounded-2xl border border-slate-200">
          <button
            onClick={() => setActiveTab("products")}
            className={cn(
              "flex items-center gap-2 px-5 py-2.5 text-sm font-bold rounded-xl transition-all duration-150",
              activeTab === "products"
                ? "bg-emerald-600 text-white shadow-md shadow-emerald-600/30"
                : "text-slate-600 hover:text-slate-900 hover:bg-slate-200/60"
            )}
          >
            <Package className="h-4 w-4" />
            Products
          </button>
          <button
            onClick={() => setActiveTab("categories")}
            className={cn(
              "flex items-center gap-2 px-5 py-2.5 text-sm font-bold rounded-xl transition-all duration-150",
              activeTab === "categories"
                ? "bg-emerald-600 text-white shadow-md shadow-emerald-600/30"
                : "text-slate-600 hover:text-slate-900 hover:bg-slate-200/60"
            )}
          >
            <FolderTree className="h-4 w-4" />
            Categories
          </button>
        </div>
      </div>

      {/* Active View */}
      {activeTab === "products" ? <ProductsTab /> : <CategoriesTab />}
    </div>
  );
}