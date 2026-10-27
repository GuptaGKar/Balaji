import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Delete Your Account - Balaji Provision Store",
  description: "Request deletion of your Balaji Provision Store account and personal data.",
};

export default function AccountDeletionPage() {
  return (
    <main className="mx-auto max-w-3xl px-6 py-12 text-slate-800">
      <p className="text-sm font-semibold uppercase tracking-wide text-emerald-700">
        Balaji Provision Store
      </p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">Delete your account</h1>
      <p className="mt-3 text-sm leading-7 text-slate-600">
        You can erase your Balaji Provision Store customer account and its personal data. Choose
        either route below.
      </p>

      <div className="mt-8 space-y-6 text-sm leading-7">
        <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
          <h2 className="text-lg font-semibold">Delete in the app (fastest)</h2>
          <ol className="mt-2 list-decimal space-y-1 pl-6">
            <li>Open the Balaji Provision Store app and sign in.</li>
            <li>Open Profile.</li>
            <li>Under Privacy &amp; account, choose Delete my account.</li>
            <li>Type DELETE and confirm.</li>
          </ol>
          <p className="mt-2">
            The app signs you out and removes your name, phone number, saved addresses and sign-in
            credentials. Active orders must be completed or cancelled first.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold">Request by phone</h2>
          <p className="mt-2">
            Call{" "}
            <a className="font-semibold text-emerald-700" href="tel:+919837803293">
              9837803293
            </a>{" "}
            from the mobile number registered to your account and ask for account deletion. We will
            verify control of the number before processing the request.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold">What is deleted and what is retained</h2>
          <p className="mt-2">
            We delete your profile name, phone, login identifiers, saved addresses, location
            coordinates and active sign-in sessions. Completed order and bill amounts are retained
            in an anonymised form for accounting, tax and legal records; item names, quantities and
            totals remain without your personal details. We do not keep your phone number or address
            for marketing after deletion.
          </p>
        </section>

        <section>
          <h2 className="text-lg font-semibold">Questions</h2>
          <p className="mt-2">
            See the{" "}
            <Link className="font-semibold text-emerald-700" href="/privacy">
              privacy policy
            </Link>{" "}
            or call the store. If you cannot access the app, the phone route above is the external
            deletion resource for Google Play.
          </p>
        </section>
      </div>
    </main>
  );
}
