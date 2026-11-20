import { useEffect, useState } from "react";
import { CreditCard, Loader2 } from "lucide-react";
import { sendTenantRequest, tenantUrl } from "../services/enterpriseService";
import { ErrorText, SettingsSection } from "./SettingsSection";
import { PRIMARY_BUTTON, SECONDARY_BUTTON, errorMessage, formatDate } from "../lib/settingsUi";

type BillingStatus = {
  plan: "FREE" | "PRO" | "BUSINESS";
  subscriptionStatus: string | null;
  renewsAt: string | null;
  configured: boolean;
  hasCustomer: boolean;
};

const PLANS = [
  { id: "FREE", name: "Free", detail: "1 GB storage · 3 seats · 50 AI questions/month" },
  { id: "PRO", name: "Pro", detail: "100 GB storage · 25 seats · 2,000 AI questions/month" },
  { id: "BUSINESS", name: "Business", detail: "1 TB storage · unlimited seats · 20,000 AI questions/month · compliance locks" },
] as const;

// Owner only (billing.manage). Checkout and the customer portal are hosted by Stripe.
export function BillingPanel({ slug, token }: { slug: string; token: string }) {
  const [status, setStatus] = useState<BillingStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [redirecting, setRedirecting] = useState(false);

  useEffect(() => {
    let active = true;
    sendTenantRequest<BillingStatus>(tenantUrl(slug, "/billing"), token, {}, "Couldn't load billing")
      .then((loaded) => active && setStatus(loaded))
      .catch((err: unknown) => active && setError(errorMessage(err, "Couldn't load billing")));
    return () => {
      active = false;
    };
  }, [slug, token]);

  async function go(path: string, body?: object) {
    setRedirecting(true);
    setError(null);
    try {
      const { url } = await sendTenantRequest<{ url: string }>(tenantUrl(slug, path), token,
        { method: "POST", body: body ? JSON.stringify(body) : undefined }, "Couldn't open billing");
      window.location.assign(url);
    } catch (err) {
      setError(errorMessage(err, "Couldn't open billing"));
      setRedirecting(false);
    }
  }

  if (!status) {
    return error ? <ErrorText message={error} /> : (
      <div className="flex items-center gap-2 text-sm text-gray-500"><Loader2 className="h-4 w-4 animate-spin" />Loading…</div>
    );
  }

  return (
    <SettingsSection icon={CreditCard} title="Plan & billing"
      description={status.configured
        ? "Upgrades are handled by Stripe. Use Manage billing to change cards, download invoices or cancel."
        : "Online billing isn't set up on this server."}
      action={status.configured && status.hasCustomer && (
        <button type="button" className={SECONDARY_BUTTON} disabled={redirecting} onClick={() => void go("/billing/portal")}>
          Manage billing
        </button>
      )}>
      <ul className="grid gap-3 sm:grid-cols-3">
        {PLANS.map((plan) => {
          const current = status.plan === plan.id;
          return (
            <li key={plan.id}
              className={`rounded-xl border p-4 ${current ? "border-indigo-500 ring-1 ring-indigo-500" : "border-gray-200 dark:border-gray-800"}`}>
              <div className="text-sm font-semibold text-gray-900 dark:text-white">{plan.name}</div>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{plan.detail}</p>
              {current ? (
                <p className="mt-3 text-xs font-medium text-indigo-600 dark:text-indigo-400">
                  Current plan{status.renewsAt ? ` · renews ${formatDate(status.renewsAt)}` : ""}
                  {status.subscriptionStatus && status.subscriptionStatus !== "active" ? ` · ${status.subscriptionStatus}` : ""}
                </p>
              ) : plan.id !== "FREE" && status.configured && !status.hasCustomer ? (
                <button type="button" className={`${PRIMARY_BUTTON} mt-3`} disabled={redirecting}
                  onClick={() => void go("/billing/checkout", { plan: plan.id })}>
                  Upgrade
                </button>
              ) : null}
            </li>
          );
        })}
      </ul>
      <ErrorText message={error} />
    </SettingsSection>
  );
}
