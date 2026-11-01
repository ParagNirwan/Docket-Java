import { useEffect, useState } from "react";
import { Download } from "lucide-react";
import { notify } from "../lib/notifications";
import {
  AUDIT_ACTIONS,
  type AuditFilters,
  type AuditPage,
  downloadAuditCsv,
  getAuditEvents,
} from "../services/auditService";

const NO_FILTERS: AuditFilters = { actor: "", action: "", from: "", to: "" };

function actionLabel(action: string): string {
  const words = action.toLowerCase().replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

// What the event was about, for the Details column.
function eventDetail(metadata: Record<string, unknown> | null): string {
  if (!metadata) return "";
  return Object.values(metadata).map(String).join(" → ");
}

type ActivityLogProps = {
  slug: string;
  token: string | null;
};

export function ActivityLog({ slug, token }: ActivityLogProps) {
  const [filters, setFilters] = useState<AuditFilters>(NO_FILTERS);
  const [applied, setApplied] = useState<AuditFilters>(NO_FILTERS);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AuditPage | null>(null);
  const [error, setError] = useState("");
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    getAuditEvents(slug, applied, page, token)
      .then((loaded) => {
        if (cancelled) return;
        setData(loaded);
        setError("");
      })
      .catch(() => {
        if (!cancelled) setError("Could not load the activity log.");
      });
    return () => {
      cancelled = true;
    };
  }, [slug, token, applied, page]);

  function handleFilter(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setPage(0);
    setApplied(filters);
  }

  async function handleExport() {
    if (!token) return;
    setExporting(true);
    try {
      await downloadAuditCsv(slug, applied, token);
    } catch {
      notify.error("Could not export the activity log.");
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="space-y-4 p-4 sm:p-6">
      <form onSubmit={handleFilter} className="flex flex-wrap items-end gap-3">
        <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
          <span className="mb-1 block">Person (email)</span>
          <input
            type="email"
            value={filters.actor}
            onChange={(e) => setFilters({ ...filters, actor: e.target.value })}
            className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-700 dark:bg-gray-900 dark:text-white"
          />
        </label>
        <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
          <span className="mb-1 block">Action</span>
          <select
            value={filters.action}
            onChange={(e) => setFilters({ ...filters, action: e.target.value })}
            className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-700 dark:bg-gray-900 dark:text-white"
          >
            <option value="">All actions</option>
            {AUDIT_ACTIONS.map((action) => (
              <option key={action} value={action}>{actionLabel(action)}</option>
            ))}
          </select>
        </label>
        <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
          <span className="mb-1 block">From</span>
          <input
            type="date"
            value={filters.from}
            onChange={(e) => setFilters({ ...filters, from: e.target.value })}
            className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-700 dark:bg-gray-900 dark:text-white"
          />
        </label>
        <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
          <span className="mb-1 block">To</span>
          <input
            type="date"
            value={filters.to}
            onChange={(e) => setFilters({ ...filters, to: e.target.value })}
            className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-700 dark:bg-gray-900 dark:text-white"
          />
        </label>
        <button type="submit" className="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white transition-colors hover:bg-indigo-700">
          Apply
        </button>
        <button type="button" onClick={handleExport} disabled={exporting} className="ml-auto inline-flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800">
          <Download className="h-4 w-4" />
          {exporting ? "Exporting..." : "Export CSV"}
        </button>
      </form>

      {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      {data && (
        <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-800">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="bg-gray-50 text-xs uppercase tracking-wider text-gray-500 dark:bg-gray-900 dark:text-gray-400">
              <tr>
                <th scope="col" className="px-4 py-3 font-semibold">When</th>
                <th scope="col" className="px-4 py-3 font-semibold">Who</th>
                <th scope="col" className="px-4 py-3 font-semibold">Action</th>
                <th scope="col" className="px-4 py-3 font-semibold">Details</th>
                <th scope="col" className="px-4 py-3 font-semibold">IP address</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {data.content.map((event) => (
                <tr key={event.id} className="text-gray-700 dark:text-gray-300">
                  <td className="whitespace-nowrap px-4 py-3">
                    {new Date(event.createdAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}
                  </td>
                  <td className="px-4 py-3">{event.actorName ?? event.actorEmail ?? "Unknown"}</td>
                  <td className="px-4 py-3">{actionLabel(event.action)}</td>
                  <td className="max-w-xs truncate px-4 py-3">{eventDetail(event.metadata)}</td>
                  <td className="whitespace-nowrap px-4 py-3">{event.ip ?? ""}</td>
                </tr>
              ))}
              {data.content.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-gray-500 dark:text-gray-400">No activity found.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-end gap-3 text-sm text-gray-600 dark:text-gray-400">
          <span>Page {data.number + 1} of {data.totalPages}</span>
          <button type="button" onClick={() => setPage(page - 1)} disabled={page === 0} className="rounded-lg border border-gray-200 px-3 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800">Previous</button>
          <button type="button" onClick={() => setPage(page + 1)} disabled={page + 1 >= data.totalPages} className="rounded-lg border border-gray-200 px-3 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800">Next</button>
        </div>
      )}
    </div>
  );
}
