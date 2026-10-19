import { useEffect, useState, useCallback } from "react";
import { AlertTriangle, Trash2, X } from "lucide-react";

interface DeleteConfirmationModalProps {
  isOpen: boolean;
  title?: string;
  itemName: string;
  itemType: "tray" | "folder" | "document";
  description?: string;
  onClose: () => void;
  onConfirm: () => Promise<void> | void;
}

export function DeleteConfirmationModal({
  isOpen,
  title,
  itemName,
  itemType,
  description,
  onClose,
  onConfirm,
}: DeleteConfirmationModalProps) {
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleClose = useCallback(() => {
    if (isDeleting) return;
    setError(null);
    onClose();
  }, [isDeleting, onClose]);

  // Handle Escape key
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape" && isOpen && !isDeleting) {
        handleClose();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, isDeleting, handleClose]);

  if (!isOpen) return null;

  async function handleConfirm() {
    try {
      setIsDeleting(true);
      setError(null);
      await onConfirm();
      setIsDeleting(false);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete item");
      setIsDeleting(false);
    }
  }

  const typeLabel = itemType === "tray" ? "Tray" : itemType === "document" ? "Document" : "Folder";
  const defaultTitle = title || `Delete ${typeLabel}`;
  const defaultDesc =
    description ||
    (itemType === "document"
      ? `Are you sure you want to delete "${itemName}"? This file will be removed.`
      : `Are you sure you want to permanently delete "${itemName}"? All files, documents, and sub-folders inside it will be permanently deleted. This action cannot be undone.`);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-confirm-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
    >
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/50 backdrop-blur-xs transition-opacity duration-200"
        onClick={handleClose}
      />

      {/* Modal Card */}
      <div className="relative w-full max-w-md bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden flex flex-col">
        {/* Close button */}
        <div className="absolute right-4 top-4">
          <button
            type="button"
            onClick={handleClose}
            disabled={isDeleting}
            className="p-1.5 rounded-lg text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors disabled:opacity-50"
            aria-label="Close dialog"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Content */}
        <div className="p-6 sm:p-7 text-center">
          {/* Warning Icon Badge */}
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-red-50 text-red-600 dark:bg-red-950/60 dark:text-red-400">
            <AlertTriangle className="h-7 w-7" strokeWidth={1.8} />
          </div>

          <h2
            id="delete-confirm-title"
            className="text-lg font-bold text-gray-900 dark:text-white"
          >
            {defaultTitle}
          </h2>

          <p className="mt-2 text-xs sm:text-sm text-gray-500 dark:text-gray-400 leading-relaxed">
            {defaultDesc}
          </p>

          {error && (
            <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-600 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
              {error}
            </div>
          )}

          {/* Action Buttons */}
          <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={handleClose}
              disabled={isDeleting}
              className="inline-flex w-full sm:w-auto items-center justify-center rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-2.5 text-xs font-semibold text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleConfirm}
              disabled={isDeleting}
              className="inline-flex w-full sm:w-auto items-center justify-center gap-1.5 rounded-xl bg-red-600 px-4 py-2.5 text-xs font-semibold text-white shadow-xs hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900 transition-colors disabled:opacity-50"
            >
              <Trash2 className="h-4 w-4" />
              <span>{isDeleting ? "Deleting..." : `Delete ${typeLabel}`}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

