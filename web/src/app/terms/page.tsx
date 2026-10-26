import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Terms of Use - Balaji Provision Store",
  description: "Terms for using the Balaji Provision Store customer app and website.",
};

const updated = "23 October 2026";

export default function TermsPage() {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-slate-800">
      <p className="text-sm font-semibold uppercase tracking-wide text-emerald-700">
        Balaji Provision Store
      </p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">Terms of Use</h1>
      <p className="mt-2 text-sm text-slate-500">Last updated: {updated}</p>

      <div className="mt-8 space-y-8 text-sm leading-7">
        <section>
          <h2 className="text-xl font-semibold">Using the service</h2>
          <p className="mt-2">
            The Balaji Provision Store app lets you browse the live catalogue, place a
            cash-on-delivery order and track delivery within the store&apos;s service area. You must
            provide accurate contact and delivery details and keep your account secure.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Orders and prices</h2>
          <p className="mt-2">
            Prices, available stock, delivery charges and totals are calculated by the store when
            you request a quote and place an order. The app does not set prices. An order is
            confirmed only after the store accepts it. If an item is unavailable, the store may
            remove it and recalculate the total; you will see the updated order in the app.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Payment</h2>
          <p className="mt-2">
            This version supports cash on delivery only. Online payment is disabled. Pay the
            amount shown on the order or bill at the time of delivery.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Cancellations and rejection</h2>
          <p className="mt-2">
            You can cancel while an order is awaiting store confirmation. After that, call the
            store. The store may reject an order when delivery is unavailable, stock cannot be
            fulfilled, or the details cannot be verified.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Acceptable use</h2>
          <p className="mt-2">
            Do not misuse the service, attempt to access another customer&apos;s account, submit
            false orders, interfere with the apps, or use the service for unlawful activity.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Availability</h2>
          <p className="mt-2">
            The store may pause orders, change the catalogue, or stop delivery temporarily. We aim
            to keep the service available but do not guarantee uninterrupted access.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Liability</h2>
          <p className="mt-2">
            To the extent permitted by law, the store is responsible for fulfilling accepted orders
            and is not liable for indirect losses caused by network or device failures. Nothing in
            these terms limits rights that cannot be excluded by law.
          </p>
        </section>

        <section>
          <h2 className="text-xl font-semibold">Contact</h2>
          <p className="mt-2">
            Balaji Provision Store, India. Phone:{" "}
            <a className="font-semibold text-emerald-700" href="tel:+919837803293">9837803293</a>.
          </p>
        </section>
      </div>

      <div className="mt-10 flex gap-4 border-t border-slate-200 pt-6 text-sm">
        <Link className="font-semibold text-emerald-700" href="/privacy">Privacy policy</Link>
        <Link className="font-semibold text-emerald-700" href="/account-deletion">Account deletion</Link>
      </div>
    </main>
  );
}
