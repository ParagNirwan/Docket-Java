import { useEffect, useState, useCallback } from "react";
import { createPortal } from "react-dom";
import { AlertTriangle, Trash2, X } from "lucide-react";

interface DeleteConfirmationModalProps {
  isOpen: boolean;
  title?: string;
  itemName: string;
  itemType?: "tray" | "folder" | "document";
  description?: string;
  // For confirming something other than a delete, such as removing a person.
  confirmLabel?: string;
  onClose: () => void;
  onConfirm: () => Promise<void> | void;
}

export function DeleteConfirmationModal({
  isOpen,
  title,
  itemName,
  itemType = "folder",
  description,
  confirmLabel,
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
      ? `Are you sure you want to delete "${itemName}"? It will go to the Trash, where it can be restored.`
      : `Are you sure you want to delete "${itemName}"? Sub-folders inside it will be removed, and its documents will go to the Trash.`);

  // In a portal because the workspace's enter animation would otherwise make "fixed" relative to it.
  return createPortal(
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
      <div className="relative w-full max-w-md bg-white border border-[#DDDDDD] rounded-[8px] shadow-[0_8px_30px_rgba(0,0,0,0.2)] z-10 overflow-hidden flex flex-col">
        {/* Close button */}
        <div className="absolute right-4 top-4">
          <button
            type="button"
            onClick={handleClose}
            disabled={isDeleting}
            className="p-1.5 rounded-[8px] text-[#616061] hover:text-[#1D1C1D] hover:bg-[#F8F8F8] transition-colors disabled:opacity-50"
            aria-label="Close dialog"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Content */}
        <div className="p-6 sm:p-7 text-center">
          {/* Warning Icon Badge */}
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-[8px] bg-[#E01E5A]/10 text-[#E01E5A]">
            <AlertTriangle className="h-6 w-6" strokeWidth={1.8} />
          </div>

          <h2
            id="delete-confirm-title"
            className="text-[18px] font-bold text-[#1D1C1D]"
          >
            {defaultTitle}
          </h2>

          <p className="mt-1.5 text-[13px] text-[#616061] leading-relaxed">
            {defaultDesc}
          </p>

          {error && (
            <div className="mt-4 rounded-[4px] border border-[#E01E5A]/30 bg-[#E01E5A]/10 p-3 text-[12px] font-medium text-[#E01E5A]">
              {error}
            </div>
          )}

          {/* Action Buttons */}
          <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={handleClose}
              disabled={isDeleting}
              className="inline-flex w-full sm:w-auto items-center justify-center rounded-[8px] border border-[#DDDDDD] bg-white px-3.5 py-1.5 text-[13px] font-medium text-[#1D1C1D] hover:bg-[#F8F8F8] transition-colors disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleConfirm}
              disabled={isDeleting}
              className="inline-flex w-full sm:w-auto items-center justify-center gap-1.5 rounded-[8px] bg-[#E01E5A] px-4 py-1.5 text-[13px] font-bold text-white shadow-[0_1px_2px_rgba(0,0,0,0.1)] hover:bg-[#c0184b] focus:outline-none focus:ring-2 focus:ring-[#E01E5A] focus:ring-offset-2 transition-colors disabled:opacity-50"
            >
              <Trash2 className="h-4 w-4" />
              <span>{isDeleting ? "Working..." : confirmLabel || `Delete ${typeLabel}`}</span>
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}

