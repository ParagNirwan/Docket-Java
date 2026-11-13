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

      <div className="relative w-full max-w-md bg-white border border-[#DDDDDD] rounded-[8px] shadow-[0_8px_30px_rgba(0,0,0,0.2)] z-10 overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#DDDDDD]">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-[8px] bg-[#1D1C1D] flex items-center justify-center text-white">
              <FolderPlus className="w-5 h-5" />
            </div>
            <div>
              <h2
                id="create-folder-title"
                className="text-[18px] font-bold text-[#1D1C1D]"
              >
                New Folder
              </h2>
              <p className="text-[12px] text-[#616061]">
                Inside <span className="font-semibold text-[#1D1C1D]">{parentName}</span>
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleClose}
            className="p-1.5 rounded-[8px] text-[#616061] hover:text-[#1D1C1D] hover:bg-[#F8F8F8] transition-colors"
            aria-label="Close dialog"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="rounded-[4px] border border-[#E01E5A]/30 bg-[#E01E5A]/10 p-3 text-[12px] font-medium text-[#E01E5A]">
              {error}
            </div>
          )}

          <div>
            <label
              htmlFor="folder-name-input"
              className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-1.5"
            >
              Folder Name <span className="text-[#E01E5A]">*</span>
            </label>
            <input
              id="folder-name-input"
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Q4 Invoices, Vendor Contracts"
              className="w-full rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3.5 py-2 text-[14px] text-[#1D1C1D] placeholder-[#616061] focus:border-[#36C5F0] focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#36C5F0] transition-all"
              autoFocus
            />
          </div>

          <div>
            <label
              htmlFor="folder-desc-input"
              className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-1.5"
            >
              Description <span className="text-[#616061] font-normal lowercase">(optional)</span>
            </label>
            <textarea
              id="folder-desc-input"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional notes or details about this folder"
              className="w-full rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3.5 py-2 text-[14px] text-[#1D1C1D] placeholder-[#616061] focus:border-[#36C5F0] focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#36C5F0] transition-all"
            />
          </div>

          {/* Footer Actions */}
          <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-[#DDDDDD]">
            <button
              type="button"
              onClick={handleClose}
              disabled={isSubmitting}
              className="px-3.5 py-1.5 text-[13px] font-medium rounded-[8px] border border-[#DDDDDD] text-[#1D1C1D] hover:bg-[#F8F8F8] transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting || !name.trim()}
              className="px-4 py-1.5 text-[13px] font-bold rounded-[8px] bg-[#007A5A] hover:bg-[#00664B] text-white shadow-[0_1px_2px_rgba(0,0,0,0.1)] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? "Creating..." : "Create Folder"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
