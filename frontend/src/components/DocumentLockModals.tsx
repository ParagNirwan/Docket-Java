import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Archive, Gavel, ShieldAlert, X } from "lucide-react";
import { formatDate, LOCK_MODE_LABELS } from "../lib/retention";
import { getArchivePreview, type ArchivePreview } from "../services/retentionService";

const INPUT = "mt-1 w-full rounded-xl border border-gray-200 bg-gray-50/50 px-3 py-2 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-800/50 dark:text-white";

function ModalCard({ title, icon, onClose, children }: { title: string; icon: ReactNode; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  // In a portal because the workspace's enter animation would otherwise make "fixed" relative to it.
  return createPortal(
    <div role="dialog" aria-modal="true" aria-label={title} className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-black/50 backdrop-blur-xs" onClick={onClose} />
      <div className="relative z-10 w-full max-w-md rounded-2xl border border-gray-200 bg-white p-6 shadow-2xl dark:border-gray-800 dark:bg-gray-900">
        <button type="button" onClick={onClose} aria-label="Close dialog" className="absolute right-4 top-4 rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200">
          <X className="h-5 w-5" />
        </button>
        <div className="mb-3 flex items-center gap-3">
          {icon}
          <h2 className="text-base font-bold text-gray-900 dark:text-white">{title}</h2>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  );
}

function Failure({ message }: { message: string }) {
  if (!message) return null;
  return (
    <div role="alert" className="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-600 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
      {message}
    </div>
  );
}

function Actions({ onClose, label, busy, disabled, danger }: { onClose: () => void; label: string; busy: boolean; disabled?: boolean; danger?: boolean }) {
  return (
    <div className="mt-5 flex justify-end gap-2">
      <button type="button" onClick={onClose} className="rounded-xl border border-gray-200 px-4 py-2 text-xs font-semibold text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800">
        Cancel
      </button>
      <button
        type="submit"
        disabled={busy || disabled}
        className={`rounded-xl px-4 py-2 text-xs font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50 ${danger ? "bg-red-600 hover:bg-red-700" : "bg-indigo-600 hover:bg-indigo-700"}`}
      >
        {busy ? "Working..." : label}
      </button>
    </div>
  );
}

// Shows the date the document will be locked until before anyone commits to it.
export function ArchiveModal({ slug, token, docId, name, onClose, onConfirm }: {
  slug: string;
  token: string;
  docId: string;
  name: string;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}) {
  const [preview, setPreview] = useState<ArchivePreview | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getArchivePreview(slug, docId, token)
      .then((loaded) => {
        if (!cancelled) setPreview(loaded);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Could not work out the lock date");
      });
    return () => {
      cancelled = true;
    };
  }, [slug, docId, token]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      setBusy(true);
      setError("");
      await onConfirm();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not archive this document");
      setBusy(false);
    }
  }

  return (
    <ModalCard title="Move to Archive" icon={<Archive className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <p className="text-sm text-gray-600 dark:text-gray-300">"{name}" will move to {preview?.folderName ?? "the Archive"} and become read-only.</p>
        {preview && (
          <p className="mt-3 rounded-xl bg-amber-50 p-3 text-xs text-amber-800 dark:bg-amber-950/30 dark:text-amber-300">
            {preview.retainUntil
              ? `It will be locked until ${formatDate(preview.retainUntil)}${preview.lockMode ? ` (${LOCK_MODE_LABELS[preview.lockMode]})` : ""}, and can't be deleted before then.`
              : "No retention policy covers the Archive, so it won't be locked. You can unarchive it at any time."}
          </p>
        )}
        <Failure message={error} />
        <Actions onClose={onClose} label="Move to Archive" busy={busy} disabled={!preview} />
      </form>
    </ModalCard>
  );
}

// Going past a Governance lock is on the record, so it needs a reason.
export function OverrideLockModal({ name, action, retainUntil, onClose, onConfirm }: {
  name: string;
  action: string;
  retainUntil?: string | null;
  onClose: () => void;
  onConfirm: (reason: string) => Promise<void>;
}) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      setBusy(true);
      setError("");
      await onConfirm(reason.trim());
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not override the lock");
      setBusy(false);
    }
  }

  return (
    <ModalCard title="Override lock" icon={<ShieldAlert className="h-5 w-5 text-amber-600" />} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <p className="text-sm text-gray-600 dark:text-gray-300">
          "{name}" is locked{retainUntil ? ` until ${formatDate(retainUntil)}` : ""}. This will {action} it anyway and remove the lock. Your reason goes in the activity log.
        </p>
        <label className="mt-4 block text-xs font-medium text-gray-600 dark:text-gray-400">
          Reason
          <textarea value={reason} onChange={(e) => setReason(e.target.value)} required maxLength={500} rows={3} className={INPUT} />
        </label>
        <Failure message={error} />
        <Actions onClose={onClose} label="Override" busy={busy} disabled={!reason.trim()} danger />
      </form>
    </ModalCard>
  );
}

export function LegalHoldModal({ subject, onClose, onConfirm }: {
  subject: string;
  onClose: () => void;
  onConfirm: (name: string, reason: string) => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      setBusy(true);
      setError("");
      await onConfirm(name.trim(), reason.trim());
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not apply the legal hold");
      setBusy(false);
    }
  }

  return (
    <ModalCard title="Apply legal hold" icon={<Gavel className="h-5 w-5 text-red-600" />} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <p className="text-sm text-gray-600 dark:text-gray-300">
          {subject} can't be deleted, by anyone, until the Owner releases this hold.
        </p>
        <label className="mt-4 block text-xs font-medium text-gray-600 dark:text-gray-400">
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={255} placeholder="Case 2026-114" className={INPUT} />
        </label>
        <label className="mt-3 block text-xs font-medium text-gray-600 dark:text-gray-400">
          Reason
          <textarea value={reason} onChange={(e) => setReason(e.target.value)} required maxLength={2000} rows={3} className={INPUT} />
        </label>
        <Failure message={error} />
        <Actions onClose={onClose} label="Apply hold" busy={busy} disabled={!name.trim() || !reason.trim()} danger />
      </form>
    </ModalCard>
  );
}
