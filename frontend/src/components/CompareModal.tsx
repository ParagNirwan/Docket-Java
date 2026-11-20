import { useEffect, useMemo, useState } from "react";
import { GitCompare, Loader2, Sparkles, X } from "lucide-react";
import { sendTenantRequest, tenantUrl } from "../services/enterpriseService";
import { toRows, type Comparison, type Row } from "../lib/diffRows";

const LEFT_TONE: Record<Row["kind"], string> = {
  same: "", removed: "bg-red-50 dark:bg-red-950/30", changed: "bg-red-50 dark:bg-red-950/30", added: "",
};
const RIGHT_TONE: Record<Row["kind"], string> = {
  same: "", added: "bg-emerald-50 dark:bg-emerald-950/30", changed: "bg-emerald-50 dark:bg-emerald-950/30", removed: "",
};

// A redline of two versions (#84). `path` is the diff endpoint under the tenant.
export function CompareModal({ slug, token, path, title, onClose }: {
  slug: string;
  token: string;
  path: string;
  title: string;
  onClose: () => void;
}) {
  const [result, setResult] = useState<Comparison | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [summarizing, setSummarizing] = useState(false);

  useEffect(() => {
    let active = true;
    sendTenantRequest<Comparison>(tenantUrl(slug, path), token, {}, "Couldn't compare")
      .then((loaded) => active && setResult(loaded))
      .catch((err: unknown) => active && setError(err instanceof Error ? err.message : "Couldn't compare"));
    return () => {
      active = false;
    };
  }, [slug, token, path]);

  const rows = useMemo(() => (result ? toRows(result.ops) : []), [result]);

  async function summarize() {
    setSummarizing(true);
    try {
      const separator = path.includes("?") ? "&" : "?";
      setResult(await sendTenantRequest<Comparison>(tenantUrl(slug, `${path}${separator}summary=true`), token, {}, "Couldn't summarize"));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't summarize");
    } finally {
      setSummarizing(false);
    }
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4" role="dialog" aria-modal="true" aria-label={title}>
      <div className="flex max-h-[90vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl bg-white shadow-xl dark:bg-gray-900">
        <div className="flex items-center gap-2 border-b border-gray-100 px-5 py-3 dark:border-gray-800">
          <GitCompare className="h-4 w-4 text-indigo-600" />
          <h2 className="text-sm font-semibold text-gray-900 dark:text-white">{title}</h2>
          {result && (
            <span className="text-xs text-gray-500">
              <span className="text-emerald-600">+{result.added}</span> <span className="text-red-600">−{result.removed}</span> lines
            </span>
          )}
          <div className="ml-auto flex items-center gap-2">
            {result && !result.summary && (result.added > 0 || result.removed > 0) && (
              <button type="button" onClick={() => void summarize()} disabled={summarizing}
                className="inline-flex items-center gap-1 rounded-full border border-indigo-200 px-3 py-1 text-xs font-medium text-indigo-700 hover:bg-indigo-50 disabled:opacity-50 dark:border-indigo-900 dark:text-indigo-300">
                <Sparkles className="h-3.5 w-3.5" />
                {summarizing ? "Summarizing…" : "Summarize changes"}
              </button>
            )}
            <button type="button" onClick={onClose} aria-label="Close" className="rounded-lg p-1.5 text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {result?.summary && (
          <section aria-label="Change summary" className="border-b border-gray-100 bg-indigo-50/40 px-5 py-3 text-xs text-gray-700 dark:border-gray-800 dark:bg-indigo-950/20 dark:text-gray-300">
            <p>{result.summary}</p>
            {result.changes.length > 0 && (
              <ul className="mt-2 space-y-0.5">
                {result.changes.map((change, i) => (
                  <li key={i}>
                    <span className={`mr-1.5 rounded px-1 text-[10px] font-semibold uppercase ${
                      change.kind === "added" ? "bg-emerald-100 text-emerald-700" : change.kind === "removed" ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-800"
                    }`}>{change.kind}</span>
                    {change.description}
                  </li>
                ))}
              </ul>
            )}
            <p className="mt-2 text-[10px] text-gray-400">Written by AI. Check the redline before relying on it.</p>
          </section>
        )}

        <div className="flex-1 overflow-auto">
          {!result && !error && (
            <div className="flex items-center gap-2 p-6 text-sm text-gray-500"><Loader2 className="h-4 w-4 animate-spin" />Comparing…</div>
          )}
          {error && <p role="alert" className="p-6 text-sm text-red-600">{error}</p>}
          {result && (
            <table className="w-full table-fixed font-mono text-[12px]">
              <thead className="sticky top-0 bg-gray-50 text-left text-[11px] font-sans text-gray-500 dark:bg-gray-800">
                <tr>
                  <th className="px-3 py-2">{result.left.fileName}{result.left.version ? ` · v${result.left.version}` : ""}</th>
                  <th className="px-3 py-2">{result.right.fileName}{result.right.version ? ` · v${result.right.version}` : ""}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, i) => (
                  <tr key={i} className="align-top">
                    <td className={`whitespace-pre-wrap break-words border-r border-gray-100 px-3 py-0.5 dark:border-gray-800 ${LEFT_TONE[row.kind]}`}>
                      {row.left}
                    </td>
                    <td className={`whitespace-pre-wrap break-words px-3 py-0.5 ${RIGHT_TONE[row.kind]}`}>{row.right}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {result?.truncated && (
            <p className="p-3 text-xs text-amber-700">These documents are long; only the first part was compared.</p>
          )}
        </div>
      </div>
    </div>
  );
}
