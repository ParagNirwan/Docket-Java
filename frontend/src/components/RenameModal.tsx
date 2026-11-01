import { useEffect, useState, type FormEvent } from "react";
import { Pencil, X } from "lucide-react";

interface RenameModalProps {
  itemType: "folder" | "document";
  currentName: string;
  onClose: () => void;
  onSubmit: (name: string) => Promise<void>;
}

// A document's extension has to stay the same, so only the part before it is editable.
function splitExtension(itemType: RenameModalProps["itemType"], name: string): [string, string] {
  const dot = name.lastIndexOf(".");
  return itemType === "document" && dot > 0 ? [name.slice(0, dot), name.slice(dot)] : [name, ""];
}

// Rendered only while renaming, so each rename starts from the item's current name.
export function RenameModal({ itemType, currentName, onClose, onSubmit }: RenameModalProps) {
  const [baseName, extension] = splitExtension(itemType, currentName);
  const [name, setName] = useState(baseName);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    try {
      setIsSubmitting(true);
      setError(null);
      await onSubmit(trimmed + extension);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not rename this");
      setIsSubmitting(false);
    }
  }

  return (
    <div role="dialog" aria-modal="true" aria-labelledby="rename-title" className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6">
      <div className="fixed inset-0 bg-black/50 backdrop-blur-xs" onClick={onClose} />
      <form onSubmit={handleSubmit} className="relative w-full max-w-md bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400">
              <Pencil className="w-4 h-4" />
            </div>
            <h2 id="rename-title" className="text-lg font-bold text-gray-900 dark:text-white">
              Rename {itemType}
            </h2>
          </div>
          <button type="button" onClick={onClose} aria-label="Close dialog" className="p-1.5 rounded-lg text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          {error && (
            <div role="alert" className="rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 p-3 text-xs text-red-600 dark:text-red-400">
              {error}
            </div>
          )}
          <div>
            <label htmlFor="rename-input" className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5">
              New name
            </label>
            <div className="flex items-center gap-2">
              <input
                id="rename-input"
                type="text"
                autoFocus
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 px-3.5 py-2.5 text-sm text-gray-900 dark:text-white focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
              {extension && <span className="text-sm text-gray-500 dark:text-gray-400">{extension}</span>}
            </div>
          </div>
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-gray-100 dark:border-gray-800">
            <button type="button" onClick={onClose} disabled={isSubmitting} className="px-4 py-2 text-xs font-medium rounded-xl border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors">
              Cancel
            </button>
            <button type="submit" disabled={isSubmitting || !name.trim()} className="px-4 py-2 text-xs font-medium rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
              {isSubmitting ? "Saving..." : "Rename"}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}
