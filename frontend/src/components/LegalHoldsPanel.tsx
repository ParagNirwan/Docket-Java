import { useCallback, useEffect, useState } from "react";
import { Gavel, Plus } from "lucide-react";
import { DeleteConfirmationModal } from "./DeleteConfirmationModal";
import { LegalHoldModal } from "./DocumentLockModals";
import { notify } from "../lib/notifications";
import { hasPermission } from "../lib/permissions";
import { formatDate } from "../lib/retention";
import type { DocumentDTO } from "../services/documentService";
import {
  createLegalHold,
  listHoldDocuments,
  listLegalHolds,
  releaseLegalHold,
  type LegalHold,
  type LegalHoldTargets,
} from "../services/retentionService";
import type { Tray } from "../services/trayService";

const INPUT = "mt-1 w-full rounded-xl border border-gray-200 bg-gray-50/50 px-3 py-2 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-800/50 dark:text-white";

type LegalHoldsPanelProps = {
  slug: string;
  token: string;
  trays: Tray[];
  permissions: ReadonlySet<string>;
};

export function LegalHoldsPanel({ slug, token, trays, permissions }: LegalHoldsPanelProps) {
  const canApply = hasPermission(permissions, "retention.manage");
  const canRelease = hasPermission(permissions, "retention.override");
  const [holds, setHolds] = useState<LegalHold[]>([]);
  const [error, setError] = useState("");
  const [scope, setScope] = useState<"tray" | "search">("tray");
  const [trayId, setTrayId] = useState("");
  const [query, setQuery] = useState("");
  const [applying, setApplying] = useState<{ subject: string; targets: LegalHoldTargets } | null>(null);
  const [releasing, setReleasing] = useState<LegalHold | null>(null);
  const [expanded, setExpanded] = useState<{ id: string; documents: DocumentDTO[] } | null>(null);

  const load = useCallback(async () => {
    try {
      setHolds(await listLegalHolds(slug, token));
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load legal holds");
    }
  }, [slug, token]);

  useEffect(() => {
    void load();
  }, [load]);

  const holdableTrays = trays.filter((tray) => !(tray.isDefault && tray.name === "Trash"));
  const selectedTray = holdableTrays.find((tray) => tray.id === trayId);

  function startApplying() {
    if (scope === "tray" && selectedTray) {
      setApplying({ subject: `Everything in ${selectedTray.name}`, targets: { folderIds: [selectedTray.id] } });
    } else if (scope === "search" && query.trim()) {
      setApplying({ subject: `Documents matching "${query.trim()}"`, targets: { searchQuery: query.trim() } });
    }
  }

  async function toggleDocuments(hold: LegalHold) {
    if (expanded?.id === hold.id) {
      setExpanded(null);
      return;
    }
    try {
      const page = await listHoldDocuments(slug, hold.id, token);
      setExpanded({ id: hold.id, documents: page.content });
    } catch (err) {
      notify.error("Could not load the documents", err instanceof Error ? err.message : undefined);
    }
  }

  return (
    <div className="space-y-6">
      {error && (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-600 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
          {error}
        </div>
      )}

      {canApply && (
        <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-xs dark:border-gray-800 dark:bg-gray-900">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Apply a legal hold</h3>
          <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
            A hold stops documents being deleted until it's released, whatever their retention dates say. It also covers documents in the Trash. To hold one document or folder, use its ⋮ menu.
          </p>
          <div className="mt-3 flex flex-wrap items-end gap-2">
            <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
              Cover
              <select value={scope} onChange={(e) => setScope(e.target.value as "tray" | "search")} className={INPUT}>
                <option value="tray">Everything in a tray</option>
                <option value="search">Search results</option>
              </select>
            </label>
            {scope === "tray" ? (
              <label className="min-w-40 flex-1 text-xs font-medium text-gray-600 dark:text-gray-400">
                Tray
                <select value={trayId} onChange={(e) => setTrayId(e.target.value)} className={INPUT}>
                  <option value="">Choose a tray</option>
                  {holdableTrays.map((tray) => <option key={tray.id} value={tray.id}>{tray.name}</option>)}
                </select>
              </label>
            ) : (
              <label className="min-w-40 flex-1 text-xs font-medium text-gray-600 dark:text-gray-400">
                Search for
                <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Names or words in documents" className={INPUT} />
              </label>
            )}
            <button
              type="button"
              onClick={startApplying}
              disabled={scope === "tray" ? !selectedTray : !query.trim()}
              className="inline-flex items-center gap-2 rounded-xl bg-red-600 px-3.5 py-2.5 text-xs font-semibold text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Plus className="h-4 w-4" />
              Apply hold
            </button>
          </div>
        </section>
      )}

      <section className="rounded-2xl border border-gray-200 bg-white shadow-xs dark:border-gray-800 dark:bg-gray-900">
        <h3 className="border-b border-gray-100 px-5 py-4 text-sm font-semibold text-gray-900 dark:border-gray-800 dark:text-white">Legal holds</h3>
        {holds.length === 0 ? (
          <p className="px-5 py-8 text-center text-xs text-gray-500 dark:text-gray-400">No legal holds.</p>
        ) : (
          <ul className="divide-y divide-gray-100 dark:divide-gray-800">
            {holds.map((hold) => (
              <li key={hold.id} className="px-5 py-3.5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="flex items-center gap-1.5 text-sm font-semibold text-gray-900 dark:text-white">
                      <Gavel className={`h-3.5 w-3.5 shrink-0 ${hold.releasedAt ? "text-gray-400" : "text-red-600"}`} />
                      {hold.name}
                      {hold.releasedAt && <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-semibold text-gray-600 dark:bg-gray-800 dark:text-gray-300">Released {formatDate(hold.releasedAt)}</span>}
                    </p>
                    <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{hold.reason}</p>
                    <p className="mt-1 text-[11px] text-gray-400 dark:text-gray-500">
                      {formatDate(hold.createdAt)}{hold.createdByName ? ` by ${hold.createdByName}` : ""}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1.5">
                    <button type="button" onClick={() => toggleDocuments(hold)} aria-expanded={expanded?.id === hold.id} className="rounded-lg px-2.5 py-1 text-xs font-medium text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-950/40">
                      {hold.documentCount} {hold.documentCount === 1 ? "document" : "documents"}
                    </button>
                    {canRelease && !hold.releasedAt && (
                      <button type="button" onClick={() => setReleasing(hold)} className="rounded-lg border border-gray-200 px-2.5 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800">
                        Release
                      </button>
                    )}
                  </div>
                </div>
                {expanded?.id === hold.id && (
                  <ul className="mt-2 max-h-48 overflow-y-auto rounded-xl bg-gray-50 p-2 text-xs text-gray-600 dark:bg-gray-800/50 dark:text-gray-300">
                    {expanded.documents.map((document) => <li key={document.id} className="truncate px-1 py-0.5">{document.originalFileName}</li>)}
                    {hold.documentCount > expanded.documents.length && <li className="px-1 py-0.5 text-gray-400">and {hold.documentCount - expanded.documents.length} more</li>}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {applying && (
        <LegalHoldModal
          subject={applying.subject}
          onClose={() => setApplying(null)}
          onConfirm={async (name, reason) => {
            const hold = await createLegalHold(slug, name, reason, applying.targets, token);
            notify.success("Legal hold applied", `${hold.documentCount} ${hold.documentCount === 1 ? "document" : "documents"} covered`);
            await load();
          }}
        />
      )}

      <DeleteConfirmationModal
        isOpen={releasing !== null}
        title="Release legal hold"
        itemName={releasing?.name ?? ""}
        description={`Documents covered by "${releasing?.name}" can be deleted again, unless another hold or a retention period still protects them.`}
        confirmLabel="Release hold"
        onClose={() => setReleasing(null)}
        onConfirm={async () => {
          if (!releasing) return;
          await releaseLegalHold(slug, releasing.id, token);
          notify.success("Legal hold released", releasing.name);
          await load();
        }}
      />
    </div>
  );
}
