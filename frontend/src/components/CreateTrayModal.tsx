import { useState, useEffect, useCallback, type FormEvent } from "react";
import { FolderPlus, X, Shield, Users } from "lucide-react";
import type { FolderVisibility, CreateTrayRequestDTO } from "../services/trayService";

interface CreateTrayModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: CreateTrayRequestDTO) => Promise<void> | void;
}

export function CreateTrayModal({ isOpen, onClose, onSubmit }: CreateTrayModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [visibility, setVisibility] = useState<FolderVisibility>("ALL_MEMBERS");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleClose = useCallback(() => {
    setName("");
    setDescription("");
    setVisibility("ALL_MEMBERS");
    setError(null);
    setIsSubmitting(false);
    onClose();
  }, [onClose]);

  // Handle escape key
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
      setError("Tray name is required.");
      return;
    }

    try {
      setIsSubmitting(true);
      setError(null);
      /**
       * 💡 LEARNING NOTE:
       * When you connect the API, this form calls onSubmit which triggers
       * createTray(slug, { name, description, visibility }, token).
       */
      await onSubmit({
        name: trimmedName,
        description: description.trim() || undefined,
        visibility,
      });
      handleClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create tray");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-tray-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
    >
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/50 backdrop-blur-xs transition-opacity duration-200"
        onClick={handleClose}
      />

      {/* Modal Dialog Card */}
      <div className="relative w-full max-w-lg bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400">
              <FolderPlus className="w-5 h-5" />
            </div>
            <div>
              <h2
                id="create-tray-title"
                className="text-lg font-bold text-gray-900 dark:text-white"
              >
                Create New Tray
              </h2>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                Trays appear as top-level categories in your workspace sidebar
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
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-5">
          {error && (
            <div className="rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 p-3 text-xs text-red-600 dark:text-red-400">
              {error}
            </div>
          )}

          <div>
            <label
              htmlFor="tray-name-input"
              className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
            >
              Tray Name <span className="text-red-500">*</span>
            </label>
            <input
              id="tray-name-input"
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Legal Filings, Tax Records"
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 px-3.5 py-2.5 text-sm text-gray-900 dark:text-white placeholder-gray-400 focus:border-indigo-500 focus:bg-white dark:focus:bg-gray-900 focus:outline-none focus:ring-1 focus:ring-indigo-500 transition-all"
              autoFocus
            />
          </div>

          <div>
            <label
              htmlFor="tray-desc-input"
              className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
            >
              Description <span className="text-gray-400 font-normal lowercase">(optional)</span>
            </label>
            <textarea
              id="tray-desc-input"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Brief description of what documents go into this tray"
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 px-3.5 py-2.5 text-sm text-gray-900 dark:text-white placeholder-gray-400 focus:border-indigo-500 focus:bg-white dark:focus:bg-gray-900 focus:outline-none focus:ring-1 focus:ring-indigo-500 transition-all"
            />
          </div>

          <div>
            <span className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-2">
              Visibility & Access
            </span>
            <div className="grid gap-2.5 sm:grid-cols-2">
              <label
                className={`relative flex cursor-pointer flex-col rounded-xl border p-3.5 transition-all ${
                  visibility === "ALL_MEMBERS"
                    ? "border-indigo-600 bg-indigo-50/40 dark:border-indigo-500 dark:bg-indigo-950/30"
                    : "border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600"
                }`}
              >
                <div className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="visibility"
                    value="ALL_MEMBERS"
                    checked={visibility === "ALL_MEMBERS"}
                    onChange={() => setVisibility("ALL_MEMBERS")}
                    className="text-indigo-600 focus:ring-indigo-500"
                  />
                  <Users className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                  <span className="text-xs font-semibold text-gray-900 dark:text-white">
                    All Members
                  </span>
                </div>
                <p className="mt-1 text-[11px] text-gray-500 dark:text-gray-400 pl-6">
                  Everyone with workspace membership can see this tray and its contents.
                </p>
              </label>

              <label
                className={`relative flex cursor-pointer flex-col rounded-xl border p-3.5 transition-all ${
                  visibility === "ADMIN_ONLY"
                    ? "border-indigo-600 bg-indigo-50/40 dark:border-indigo-500 dark:bg-indigo-950/30"
                    : "border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600"
                }`}
              >
                <div className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="visibility"
                    value="ADMIN_ONLY"
                    checked={visibility === "ADMIN_ONLY"}
                    onChange={() => setVisibility("ADMIN_ONLY")}
                    className="text-indigo-600 focus:ring-indigo-500"
                  />
                  <Shield className="h-4 w-4 text-amber-600 dark:text-amber-400" />
                  <span className="text-xs font-semibold text-gray-900 dark:text-white">
                    Admin Only
                  </span>
                </div>
                <p className="mt-1 text-[11px] text-gray-500 dark:text-gray-400 pl-6">
                  Restricted to Owners and Admins only. Hidden from Members & Managers.
                </p>
              </label>
            </div>
            <p className="mt-2 text-[11px] text-gray-500 dark:text-gray-400">
              Note: Sub-folders and files created inside this tray will strictly inherit this visibility setting.
            </p>
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
              {isSubmitting ? "Creating..." : "Create Tray"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
