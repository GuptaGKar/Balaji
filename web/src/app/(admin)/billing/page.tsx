"use client";

import { Loader2 } from "lucide-react";

export default function BillingPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Billing</h1>
        <p className="text-gray-500">Bill management and POS</p>
      </div>
      <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-600 mx-auto mb-4" />
        <p className="text-gray-500">Billing & POS coming in M9</p>
      </div>
    </div>
  );
}