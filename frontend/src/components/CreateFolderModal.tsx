import { useState, useEffect, useCallback, type FormEvent } from "react";
import { FolderPlus, X } from "lucide-react";
import type { CreateFolderDTO } from "../services/trayService";

interface CreateFolderModalProps {
  isOpen: boolean;
  parentName: string;
  onClose: () => void;
  onSubmit: (data: CreateFolderDTO) => Promise<void> | void;
}

export function CreateFolderModal({
  isOpen,
  parentName,
  onClose,
  onSubmit,
}: CreateFolderModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleClose = useCallback(() => {
    setName("");
    setDescription("");
    setError(null);
    setIsSubmitting(false);
    onClose();
  }, [onClose]);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape" && isOpen) {
        handleClose();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, handleClose]);

  if (!isOpen) return null;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const trimmedName = name.trim();
    if (!trimmedName) {
      setError("Folder name is required.");
      return;
    }

    try {
      setIsSubmitting(true);
      setError(null);
      /**
       * 💡 LEARNING NOTE:
       * When connecting to your Spring Boot backend, this calls:
       * createSubFolder(slug, parentFolderId, { name, description }, token)
       * which hits POST /api/tenants/{slug}/folders/{folderId}/children
       */
      await onSubmit({
        name: trimmedName,
        description: description.trim() || undefined,
      });
      handleClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create folder");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-folder-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
    >
      <div
        className="fixed inset-0 bg-black/50 backdrop-blur-xs transition-opacity duration-200"
        onClick={handleClose}
      />

      <div className="relative w-full max-w-md bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400">
              <FolderPlus className="w-5 h-5" />
            </div>
            <div>
              <h2
                id="create-folder-title"
                className="text-lg font-bold text-gray-900 dark:text-white"
              >
                New Folder
              </h2>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                Inside <span className="font-semibold text-gray-700 dark:text-gray-300">{parentName}</span>
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleClose}
            className="p-1.5 rounded-lg text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            aria-label="Close dialog"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 p-3 text-xs text-red-600 dark:text-red-400">
              {error}
            </div>
          )}

          <div>
            <label
              htmlFor="folder-name-input"
              className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
            >
              Folder Name <span className="text-red-500">*</span>
            </label>
            <input
              id="folder-name-input"
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Q4 Invoices, Vendor Contracts"
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 px-3.5 py-2.5 text-sm text-gray-900 dark:text-white placeholder-gray-400 focus:border-indigo-500 focus:bg-white dark:focus:bg-gray-900 focus:outline-none focus:ring-1 focus:ring-indigo-500 transition-all"
              autoFocus
            />
          </div>

          <div>
            <label
              htmlFor="folder-desc-input"
              className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
            >
              Description <span className="text-gray-400 font-normal lowercase">(optional)</span>
            </label>
            <textarea
              id="folder-desc-input"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional notes or details about this folder"
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 px-3.5 py-2.5 text-sm text-gray-900 dark:text-white placeholder-gray-400 focus:border-indigo-500 focus:bg-white dark:focus:bg-gray-900 focus:outline-none focus:ring-1 focus:ring-indigo-500 transition-all"
            />
          </div>

          {/* Footer Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-gray-100 dark:border-gray-800">
            <button
              type="button"
              onClick={handleClose}
              disabled={isSubmitting}
              className="px-4 py-2 text-xs font-medium rounded-xl border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting || !name.trim()}
              className="px-4 py-2 text-xs font-medium rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? "Creating..." : "Create Folder"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
