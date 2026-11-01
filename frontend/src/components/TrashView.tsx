import { useCallback, useEffect, useState } from "react";
import { Gavel, Lock, RotateCcw, Trash2 } from "lucide-react";
import { DeleteConfirmationModal } from "./DeleteConfirmationModal";
import { notify } from "../lib/notifications";
import { formatDate, isLocked, lockDescription } from "../lib/retention";
import { formatFileSize } from "../services/documentService";
import { emptyTrash, listTrash, purgeDocument, restoreDocument, type TrashDocument } from "../services/retentionService";

type TrashViewProps = {
  slug: string;
  token: string;
};

// The Trash tray is a view of deleted documents. Nothing can be filed here directly.
export function TrashView({ slug, token }: TrashViewProps) {
  const [items, setItems] = useState<TrashDocument[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [purging, setPurging] = useState<TrashDocument | null>(null);
  const [isEmptying, setIsEmptying] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const loaded = await listTrash(slug, page, token);
      setItems(loaded.content);
      setTotalPages(Math.max(1, loaded.totalPages));
      setTotal(loaded.totalElements);
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load the Trash");
    } finally {
      setLoading(false);
    }
  }, [slug, token, page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function handleRestore(item: TrashDocument) {
    setBusyId(item.id);
    try {
      await restoreDocument(slug, item.id, token);
      notify.success("Restored", `"${item.originalFileName}" is back in ${item.originalFolderName ?? "the Inbox"}`);
      await load();
    } catch (err) {
      notify.error("Could not restore this", err instanceof Error ? err.message : undefined);
    } finally {
      setBusyId(null);
    }
  }

  async function handlePurge() {
    if (!purging) return;
    await purgeDocument(slug, purging.id, token);
    notify.success("Permanently deleted", `"${purging.originalFileName}" is gone`);
    await load();
  }

  async function handleEmpty() {
    const result = await emptyTrash(slug, token);
    notify.success(
      `${result.purged} permanently deleted`,
      result.skipped > 0 ? `${result.skipped} locked ${result.skipped === 1 ? "document stays" : "documents stay"} until released` : undefined,
    );
    setPage(0);
    await load();
  }

  return (
    <div className="space-y-4">
      <section className="flex flex-col gap-3 rounded-2xl border border-gray-200 bg-white p-6 shadow-xs dark:border-gray-800 dark:bg-gray-900 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-start gap-4">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-red-50 text-red-600 dark:bg-red-950/60 dark:text-red-400">
            <Trash2 className="h-6 w-6" />
          </div>
          <div>
            <h2 className="text-xl font-bold tracking-tight text-gray-900 dark:text-white">Trash</h2>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              Deleted documents stay here until the date shown, then they're removed for good.
            </p>
          </div>
        </div>
        {total > 0 && (
          <button
            type="button"
            onClick={() => setIsEmptying(true)}
            className="inline-flex items-center gap-2 self-start rounded-xl border border-red-200 px-3.5 py-2 text-xs font-semibold text-red-600 transition-colors hover:bg-red-50 dark:border-red-900/60 dark:text-red-400 dark:hover:bg-red-950/40 sm:self-auto"
          >
            <Trash2 className="h-4 w-4" />
            Empty Trash
          </button>
        )}
      </section>

      {error && (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-600 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
          {error}
        </div>
      )}

      {loading ? (
        <p role="status" className="p-8 text-center text-sm text-gray-500 dark:text-gray-400">Loading...</p>
      ) : items.length === 0 && !error ? (
        <section className="flex min-h-[240px] flex-col items-center justify-center rounded-2xl border border-dashed border-gray-300 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-900">
          <Trash2 className="mb-3 h-8 w-8 text-gray-300 dark:text-gray-600" strokeWidth={1.5} />
          <h3 className="text-base font-semibold text-gray-900 dark:text-white">The Trash is empty</h3>
        </section>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-gray-200 bg-white shadow-2xs dark:border-gray-800 dark:bg-gray-900">
          <table className="w-full text-left text-xs">
            <thead className="border-b border-gray-100 bg-gray-50/70 text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:border-gray-800 dark:bg-gray-800/40 dark:text-gray-400">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="hidden px-4 py-3 sm:table-cell">Was in</th>
                <th className="hidden px-4 py-3 md:table-cell">Deleted</th>
                <th className="hidden px-4 py-3 md:table-cell">Removed on</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {items.map((item) => {
                const locked = isLocked(item);
                const description = lockDescription(item);
                return (
                  <tr key={item.id}>
                    <td className="px-4 py-3">
                      <p className="flex max-w-xs items-center gap-1.5 truncate font-semibold text-gray-900 dark:text-white" title={item.originalFileName}>
                        {item.legalHold ? (
                          <Gavel className="h-3.5 w-3.5 shrink-0 text-red-600" aria-label={description ?? undefined} />
                        ) : locked ? (
                          <Lock className="h-3.5 w-3.5 shrink-0 text-amber-600" aria-label={description ?? undefined} />
                        ) : null}
                        <span className="truncate">{item.originalFileName}</span>
                      </p>
                      <p className="mt-0.5 text-[11px] text-gray-400 dark:text-gray-500">{formatFileSize(item.fileSize)}</p>
                    </td>
                    <td className="hidden px-4 py-3 text-gray-500 dark:text-gray-400 sm:table-cell">
                      {item.originalFolderName ?? "A folder that's gone (restores to the Inbox)"}
                    </td>
                    <td className="hidden px-4 py-3 text-gray-500 dark:text-gray-400 md:table-cell">
                      {formatDate(item.deletedAt)}
                      {item.deletedByName && <span className="block text-[11px] text-gray-400 dark:text-gray-500">by {item.deletedByName}</span>}
                    </td>
                    <td className="hidden px-4 py-3 text-gray-500 dark:text-gray-400 md:table-cell">
                      {locked ? description : item.purgeAfter ? formatDate(item.purgeAfter) : "—"}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <button
                          type="button"
                          onClick={() => handleRestore(item)}
                          disabled={busyId === item.id}
                          className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1 font-medium text-indigo-600 transition-colors hover:bg-indigo-50 disabled:opacity-50 dark:text-indigo-400 dark:hover:bg-indigo-950/40"
                          aria-label={`Restore ${item.originalFileName}`}
                        >
                          <RotateCcw className="h-3.5 w-3.5" />
                          <span>Restore</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => setPurging(item)}
                          disabled={locked}
                          title={locked ? description ?? undefined : "Delete permanently"}
                          aria-label={`Delete ${item.originalFileName} permanently`}
                          className="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-gray-400 dark:hover:bg-red-950/40"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-gray-100 px-4 py-2.5 text-xs text-gray-500 dark:border-gray-800 dark:text-gray-400">
              <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)} className="rounded-lg px-2 py-1 font-medium hover:bg-gray-100 disabled:opacity-40 dark:hover:bg-gray-800">Previous</button>
              <span>Page {page + 1} of {totalPages}</span>
              <button type="button" disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)} className="rounded-lg px-2 py-1 font-medium hover:bg-gray-100 disabled:opacity-40 dark:hover:bg-gray-800">Next</button>
            </div>
          )}
        </div>
      )}

      <DeleteConfirmationModal
        isOpen={purging !== null}
        title="Delete permanently"
        itemName={purging?.originalFileName ?? ""}
        description={`"${purging?.originalFileName}" will be gone for good, including its file. This can't be undone.`}
        confirmLabel="Delete permanently"
        onClose={() => setPurging(null)}
        onConfirm={handlePurge}
      />
      <DeleteConfirmationModal
        isOpen={isEmptying}
        title="Empty the Trash"
        itemName="the Trash"
        description="Everything here that isn't locked will be gone for good, including the files. This can't be undone."
        confirmLabel="Empty Trash"
        onClose={() => setIsEmptying(false)}
        onConfirm={handleEmpty}
      />
    </div>
  );
}
