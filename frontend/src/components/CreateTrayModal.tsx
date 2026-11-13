import { useState, useEffect, useCallback, type FormEvent } from "react";
import { FolderPlus, X, Shield, Users, UserCheck } from "lucide-react";
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
      <div className="relative w-full max-w-lg bg-white border border-[#DDDDDD] rounded-[8px] shadow-[0_8px_30px_rgba(0,0,0,0.2)] z-10 overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#DDDDDD]">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-[8px] bg-[#4A154B] flex items-center justify-center text-white">
              <FolderPlus className="w-5 h-5" />
            </div>
            <div>
              <h2
                id="create-tray-title"
                className="text-[18px] font-bold text-[#1D1C1D]"
              >
                Create New Tray
              </h2>
              <p className="text-[12px] text-[#616061]">
                Trays appear as top-level categories in your workspace sidebar
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
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-4">
          {error && (
            <div className="rounded-[4px] border border-[#E01E5A]/30 bg-[#E01E5A]/10 p-3 text-[12px] font-medium text-[#E01E5A]">
              {error}
            </div>
          )}

          <div>
            <label
              htmlFor="tray-name-input"
              className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-1.5"
            >
              Tray Name <span className="text-[#E01E5A]">*</span>
            </label>
            <input
              id="tray-name-input"
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Legal Filings, Tax Records"
              className="w-full rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3.5 py-2 text-[14px] text-[#1D1C1D] placeholder-[#616061] focus:border-[#36C5F0] focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#36C5F0] transition-all"
              autoFocus
            />
          </div>

          <div>
            <label
              htmlFor="tray-desc-input"
              className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-1.5"
            >
              Description <span className="text-[#616061] font-normal lowercase">(optional)</span>
            </label>
            <textarea
              id="tray-desc-input"
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Brief description of what documents go into this tray"
              className="w-full rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3.5 py-2 text-[14px] text-[#1D1C1D] placeholder-[#616061] focus:border-[#36C5F0] focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#36C5F0] transition-all"
            />
          </div>

          <div>
            <span className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-2">
              Visibility & Access
            </span>
            <div className="grid gap-2.5 sm:grid-cols-3">
              <label
                className={`relative flex cursor-pointer flex-col rounded-[8px] border p-3 transition-all ${
                  visibility === "ALL_MEMBERS"
                    ? "border-[#1164A3] bg-[#36C5F0]/10"
                    : "border-[#DDDDDD] hover:border-[#616061]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="visibility"
                    value="ALL_MEMBERS"
                    checked={visibility === "ALL_MEMBERS"}
                    onChange={() => setVisibility("ALL_MEMBERS")}
                    className="text-[#1164A3] focus:ring-[#36C5F0]"
                  />
                  <Users className="h-4 w-4 text-[#1164A3]" />
                  <span className="text-[12px] font-semibold text-[#1D1C1D]">
                    All Members
                  </span>
                </div>
                <p className="mt-1 text-[11px] text-[#616061] pl-6">
                  Available to everyone in workspace.
                </p>
              </label>

              <label
                className={`relative flex cursor-pointer flex-col rounded-[8px] border p-3 transition-all ${
                  visibility === "CUSTOM"
                    ? "border-[#1164A3] bg-[#36C5F0]/10"
                    : "border-[#DDDDDD] hover:border-[#616061]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="visibility"
                    value="CUSTOM"
                    checked={visibility === "CUSTOM"}
                    onChange={() => setVisibility("CUSTOM")}
                    className="text-[#1164A3] focus:ring-[#36C5F0]"
                  />
                  <UserCheck className="h-4 w-4 text-[#36C5F0]" />
                  <span className="text-[12px] font-semibold text-[#1D1C1D]">
                    Custom Access
                  </span>
                </div>
                <p className="mt-1 text-[11px] text-[#616061] pl-6">
                  Specific members or guests granted access.
                </p>
              </label>

              <label
                className={`relative flex cursor-pointer flex-col rounded-[8px] border p-3 transition-all ${
                  visibility === "ADMIN_ONLY"
                    ? "border-[#1164A3] bg-[#36C5F0]/10"
                    : "border-[#DDDDDD] hover:border-[#616061]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="visibility"
                    value="ADMIN_ONLY"
                    checked={visibility === "ADMIN_ONLY"}
                    onChange={() => setVisibility("ADMIN_ONLY")}
                    className="text-[#1164A3] focus:ring-[#36C5F0]"
                  />
                  <Shield className="h-4 w-4 text-[#ECB22E]" />
                  <span className="text-[12px] font-semibold text-[#1D1C1D]">
                    Admin Only
                  </span>
                </div>
                <p className="mt-1 text-[11px] text-[#616061] pl-6">
                  Restricted to Owners and Admins only.
                </p>
              </label>
            </div>
            <p className="mt-2 text-[11px] text-[#616061]">
              Note: Sub-folders and files created inside this tray will strictly inherit this visibility setting.
            </p>
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
              {isSubmitting ? "Creating..." : "Create Tray"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
