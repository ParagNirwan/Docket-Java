import { useEffect, useState } from "react";
import { Activity, BarChart3, Download, Loader2 } from "lucide-react";
import { getTenantInsights, type TenantInsights } from "../services/enterpriseService";
import { getTenantUsage, type TenantUsageDTO } from "../services/trayService";
import { downloadAuditCsv } from "../services/auditService";
import { ErrorText, SettingsSection } from "./SettingsSection";
import { SECONDARY_BUTTON } from "../lib/settingsUi";

type Props = { slug: string; token: string };

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unit]}`;
}

function humanize(action: string): string {
  const text = action.toLowerCase().replaceAll("_", " ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function Meter({ label, used, limit, format = String }: {
  label: string;
  used: number;
  limit: number | null;
  format?: (n: number) => string;
}) {
  const percent = limit ? Math.min(100, Math.round((used / limit) * 100)) : null;
  const tone = percent === null ? "bg-indigo-500" : percent >= 90 ? "bg-red-500" : percent >= 75 ? "bg-amber-500" : "bg-indigo-500";
  return (
    <div className="rounded-xl border border-gray-200 p-4 dark:border-gray-800">
      <div className="text-xs font-medium text-gray-500 dark:text-gray-400">{label}</div>
      <div className="mt-1 text-xl font-semibold text-gray-900 dark:text-white">
        {format(used)}
        <span className="ml-1 text-sm font-normal text-gray-500">{limit ? `of ${format(limit)}` : "· no limit"}</span>
      </div>
      {percent !== null && (
        <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800"
          role="meter" aria-label={label} aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
          <div className={`h-full ${tone}`} style={{ width: `${percent}%` }} />
        </div>
      )}
    </div>
  );
}

export function InsightsPanel({ slug, token }: Props) {
  const [insights, setInsights] = useState<TenantInsights | null>(null);
  const [usage, setUsage] = useState<TenantUsageDTO | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    let active = true;
    Promise.all([getTenantInsights(slug, token), getTenantUsage(slug, token)])
      .then(([loadedInsights, loadedUsage]) => {
        if (!active) return;
        setInsights(loadedInsights);
        setUsage(loadedUsage);
      })
      .catch((err: unknown) => {
        if (active) setError(err instanceof Error ? err.message : "Couldn't load insights");
      });
    return () => {
      active = false;
    };
  }, [slug, token]);

  async function handleExport(format: "csv" | "json") {
    setExporting(true);
    try {
      await downloadAuditCsv(slug, { actor: "", action: "", from: "", to: "" }, token, format);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't export the activity log");
    } finally {
      setExporting(false);
    }
  }

  if (!insights || !usage) {
    return error ? <ErrorText message={error} /> : (
      <div className="flex items-center gap-2 text-sm text-gray-500"><Loader2 className="h-4 w-4 animate-spin" />Loading…</div>
    );
  }

  const topActions = Object.entries(insights.topActionsLast30Days);
  const maxCount = Math.max(1, ...topActions.map(([, n]) => n));

  return (
    <div className="space-y-6">
      <SettingsSection icon={BarChart3} title="Usage"
        description={insights.billingEnforced ? `Current plan: ${insights.plan}. AI questions reset on the 1st of each month.` : "This installation has no plan limits."}>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Meter label="Storage" used={usage.storageBytesUsed} limit={usage.storageQuotaBytes ?? null} format={formatBytes} />
          <Meter label="Documents" used={usage.documentCount} limit={null} />
          <Meter label="AI questions this month" used={insights.aiQueriesThisMonth} limit={insights.aiQueryLimit} />
          <Meter label="Seats" used={insights.seatsUsed} limit={insights.seatLimit} />
        </div>
      </SettingsSection>

      <SettingsSection icon={Activity} title="Activity (last 30 days)"
        description={`${insights.auditEventsLast30Days.toLocaleString()} audited actions.`}
        action={
          <div className="flex gap-2">
            {(["csv", "json"] as const).map((format) => (
              <button key={format} type="button" className={SECONDARY_BUTTON}
                onClick={() => void handleExport(format)} disabled={exporting}>
                <Download className="h-3.5 w-3.5" />
                {format.toUpperCase()}
              </button>
            ))}
          </div>
        }>
        {topActions.length === 0 ? (
          <p className="text-sm text-gray-500 dark:text-gray-400">No activity yet.</p>
        ) : (
          <ul className="space-y-2">
            {topActions.map(([action, count]) => (
              <li key={action} className="grid grid-cols-[10rem_1fr_3rem] items-center gap-3 text-sm">
                <span className="truncate text-gray-700 dark:text-gray-300">{humanize(action)}</span>
                <span className="h-2 rounded-full bg-indigo-500/80" style={{ width: `${(count / maxCount) * 100}%` }} />
                <span className="text-right tabular-nums text-gray-500">{count.toLocaleString()}</span>
              </li>
            ))}
          </ul>
        )}
      </SettingsSection>
      <ErrorText message={error} />
    </div>
  );
}
