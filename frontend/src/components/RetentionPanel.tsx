import { useCallback, useEffect, useState, type FormEvent } from "react";
import { createPortal } from "react-dom";
import { Lock, Pencil, Plus, Trash2 } from "lucide-react";
import { DeleteConfirmationModal } from "./DeleteConfirmationModal";
import { notify } from "../lib/notifications";
import { hasPermission } from "../lib/permissions";
import { formatDate, LOCK_MODE_LABELS, type LockMode } from "../lib/retention";
import {
  createPolicy,
  deletePolicy,
  getRetentionSummary,
  listPolicies,
  updatePolicy,
  updateTrashRetention,
  type ClockStart,
  type OnExpiry,
  type RetentionPolicy,
  type RetentionSummary,
} from "../services/retentionService";
import type { Tray } from "../services/trayService";

const PRESET_YEARS = [1, 3, 5, 7, 10];
const DAYS_PER_YEAR = 365;

const ON_EXPIRY_LABELS: Record<OnExpiry, string> = {
  NOTIFY: "Notify admins",
  AUTO_DELETE: "Move to the Trash",
  NONE: "Do nothing",
};

const CLOCK_LABELS: Record<ClockStart, string> = {
  ARCHIVED_AT: "When filed here",
  UPLOADED_AT: "When uploaded",
};

const INPUT = "mt-1 w-full rounded-xl border border-gray-200 bg-gray-50/50 px-3 py-2 text-sm text-gray-900 disabled:opacity-60 dark:border-gray-700 dark:bg-gray-800/50 dark:text-white";
const LABEL = "block text-xs font-medium text-gray-600 dark:text-gray-400";

function describeDays(days: number): string {
  if (days % DAYS_PER_YEAR === 0) {
    const years = days / DAYS_PER_YEAR;
    return `${years} ${years === 1 ? "year" : "years"}`;
  }
  return `${days} ${days === 1 ? "day" : "days"}`;
}

type RetentionPanelProps = {
  slug: string;
  token: string;
  trays: Tray[];
  permissions: ReadonlySet<string>;
};

export function RetentionPanel({ slug, token, trays, permissions }: RetentionPanelProps) {
  const canManage = hasPermission(permissions, "retention.manage");
  const [policies, setPolicies] = useState<RetentionPolicy[]>([]);
  const [summary, setSummary] = useState<RetentionSummary | null>(null);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<RetentionPolicy | "new" | null>(null);
  const [removing, setRemoving] = useState<RetentionPolicy | null>(null);

  const load = useCallback(async () => {
    try {
      const [loadedPolicies, loadedSummary] = await Promise.all([listPolicies(slug, token), getRetentionSummary(slug, token)]);
      setPolicies(loadedPolicies);
      setSummary(loadedSummary);
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load retention settings");
    }
  }, [slug, token]);

  useEffect(() => {
    void load();
  }, [load]);

  const coveredIds = new Set(policies.map((policy) => policy.folderId));
  const availableTrays = trays.filter((tray) => !coveredIds.has(tray.id) && !(tray.isDefault && tray.name === "Trash"));

  return (
    <div className="space-y-6">
      {error && (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-600 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
          {error}
        </div>
      )}

      {summary && (
        <div className="grid gap-4 sm:grid-cols-2">
          <article className="rounded-2xl border border-gray-200 bg-white p-5 shadow-xs dark:border-gray-800 dark:bg-gray-900">
            <span className="text-xs font-medium text-gray-500 dark:text-gray-400">Documents locked</span>
            <p className="mt-2 text-2xl font-semibold tracking-tight text-gray-900 dark:text-white">{summary.lockedDocuments}</p>
          </article>
          <article className="rounded-2xl border border-gray-200 bg-white p-5 shadow-xs dark:border-gray-800 dark:bg-gray-900">
            <span className="text-xs font-medium text-gray-500 dark:text-gray-400">Expiring in the next 30 days</span>
            <p className="mt-2 text-2xl font-semibold tracking-tight text-gray-900 dark:text-white">{summary.expiringSoonCount}</p>
          </article>
        </div>
      )}

      <section className="rounded-2xl border border-gray-200 bg-white shadow-xs dark:border-gray-800 dark:bg-gray-900">
        <div className="flex items-center justify-between gap-3 border-b border-gray-100 px-5 py-4 dark:border-gray-800">
          <div>
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Retention policies</h3>
            <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">How long documents in a tray must be kept before they can be deleted.</p>
          </div>
          {canManage && availableTrays.length > 0 && (
            <button type="button" onClick={() => setEditing("new")} className="inline-flex shrink-0 items-center gap-2 rounded-xl bg-indigo-600 px-3.5 py-2 text-xs font-semibold text-white shadow-xs hover:bg-indigo-700">
              <Plus className="h-4 w-4" />
              Add policy
            </button>
          )}
        </div>

        {policies.length === 0 ? (
          <p className="px-5 py-8 text-center text-xs text-gray-500 dark:text-gray-400">No policies yet. Documents can be deleted at any time.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-gray-100 bg-gray-50/70 text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:border-gray-800 dark:bg-gray-800/40 dark:text-gray-400">
                <tr>
                  <th className="px-5 py-3">Tray</th>
                  <th className="px-5 py-3">Keep for</th>
                  <th className="px-5 py-3">Mode</th>
                  <th className="hidden px-5 py-3 md:table-cell">Counted from</th>
                  <th className="hidden px-5 py-3 md:table-cell">On expiry</th>
                  <th className="hidden px-5 py-3 sm:table-cell">Locked</th>
                  {canManage && <th className="px-5 py-3 text-right">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {policies.map((policy) => (
                  <tr key={policy.id}>
                    <td className="px-5 py-3 font-semibold text-gray-900 dark:text-white">
                      {policy.folderName}
                      {!policy.appliesToSubfolders && <span className="block text-[11px] font-normal text-gray-400">Not sub-folders</span>}
                    </td>
                    <td className="px-5 py-3 text-gray-600 dark:text-gray-300">{describeDays(policy.retentionDays)}</td>
                    <td className="px-5 py-3">
                      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold ${policy.lockMode === "COMPLIANCE" ? "bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300" : "bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300"}`}>
                        <Lock className="h-2.5 w-2.5" />
                        {LOCK_MODE_LABELS[policy.lockMode]}
                      </span>
                    </td>
                    <td className="hidden px-5 py-3 text-gray-500 dark:text-gray-400 md:table-cell">{CLOCK_LABELS[policy.clockStart]}</td>
                    <td className="hidden px-5 py-3 text-gray-500 dark:text-gray-400 md:table-cell">{ON_EXPIRY_LABELS[policy.onExpiry]}</td>
                    <td className="hidden px-5 py-3 text-gray-500 dark:text-gray-400 sm:table-cell">{policy.lockedDocuments}</td>
                    {canManage && (
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-1">
                          <button type="button" onClick={() => setEditing(policy)} aria-label={`Edit the policy on ${policy.folderName}`} className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200">
                            <Pencil className="h-3.5 w-3.5" />
                          </button>
                          {policy.lockMode !== "COMPLIANCE" && (
                            <button type="button" onClick={() => setRemoving(policy)} aria-label={`Remove the policy on ${policy.folderName}`} className="rounded-lg p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/40">
                              <Trash2 className="h-3.5 w-3.5" />
                            </button>
                          )}
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {summary && summary.expiringSoon.length > 0 && (
        <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-xs dark:border-gray-800 dark:bg-gray-900">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Expiring soon</h3>
          <ul className="mt-3 divide-y divide-gray-100 text-xs dark:divide-gray-800">
            {summary.expiringSoon.map((document) => (
              <li key={document.id} className="flex items-center justify-between gap-3 py-2">
                <span className="truncate font-medium text-gray-800 dark:text-gray-200">{document.originalFileName}</span>
                <span className="shrink-0 text-gray-500 dark:text-gray-400">{formatDate(document.retainUntil)}</span>
              </li>
            ))}
          </ul>
          {summary.expiringSoonCount > summary.expiringSoon.length && (
            <p className="mt-2 text-xs text-gray-400">and {summary.expiringSoonCount - summary.expiringSoon.length} more</p>
          )}
        </section>
      )}

      {summary && (
        <TrashPeriod
          key={summary.trashRetentionDays}
          slug={slug}
          token={token}
          days={summary.trashRetentionDays}
          canManage={canManage}
        />
      )}

      {editing && (
        <PolicyForm
          slug={slug}
          token={token}
          policy={editing === "new" ? null : editing}
          trays={availableTrays}
          onClose={() => setEditing(null)}
          onSaved={load}
        />
      )}

      <DeleteConfirmationModal
        isOpen={removing !== null}
        title="Remove policy"
        itemName={removing?.folderName ?? ""}
        description={`New documents in ${removing?.folderName} won't be locked. Documents already locked stay locked until their dates.`}
        confirmLabel="Remove policy"
        onClose={() => setRemoving(null)}
        onConfirm={async () => {
          if (!removing) return;
          await deletePolicy(slug, removing.id, token);
          notify.success("Policy removed", `${removing.folderName} no longer has a policy`);
          await load();
        }}
      />
    </div>
  );
}

function TrashPeriod({ slug, token, days, canManage }: { slug: string; token: string; days: number; canManage: boolean }) {
  const [value, setValue] = useState(String(days));
  const [saving, setSaving] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      setSaving(true);
      await updateTrashRetention(slug, Number(value), token);
      notify.success("Saved", `Deleted documents are kept for ${value} days`);
    } catch (err) {
      notify.error("Could not save", err instanceof Error ? err.message : undefined);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-xs dark:border-gray-800 dark:bg-gray-900">
      <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Trash</h3>
      <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">How long deleted documents can still be restored. After that they're removed for good, unless they're locked.</p>
      <form onSubmit={handleSubmit} className="mt-3 flex items-end gap-2">
        <label className={LABEL}>
          Days
          <input type="number" min={1} max={3650} required value={value} disabled={!canManage} onChange={(e) => setValue(e.target.value)} className={`${INPUT} w-28`} />
        </label>
        {canManage && (
          <button type="submit" disabled={saving || Number(value) === days} className="rounded-xl bg-indigo-600 px-3.5 py-2 text-xs font-semibold text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50">
            {saving ? "Saving..." : "Save"}
          </button>
        )}
      </form>
    </section>
  );
}

function PolicyForm({ slug, token, policy, trays, onClose, onSaved }: {
  slug: string;
  token: string;
  policy: RetentionPolicy | null;
  trays: Tray[];
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [folderId, setFolderId] = useState(policy?.folderId ?? trays[0]?.id ?? "");
  const [retentionDays, setRetentionDays] = useState(policy?.retentionDays ?? 7 * DAYS_PER_YEAR);
  const initialPreset = policy == null || PRESET_YEARS.includes(policy.retentionDays / DAYS_PER_YEAR);
  const [custom, setCustom] = useState(!initialPreset);
  const [lockMode, setLockMode] = useState<LockMode>(policy?.lockMode ?? "GOVERNANCE");
  const [clockStart, setClockStart] = useState<ClockStart>(policy?.clockStart ?? "ARCHIVED_AT");
  const [appliesToSubfolders, setAppliesToSubfolders] = useState(policy?.appliesToSubfolders ?? true);
  const [onExpiry, setOnExpiry] = useState<OnExpiry>(policy?.onExpiry ?? "NOTIFY");
  const [confirmName, setConfirmName] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const wasCompliance = policy?.lockMode === "COMPLIANCE";
  const trayName = policy?.folderName ?? trays.find((tray) => tray.id === folderId)?.name ?? "";
  // Asked whenever this would make something permanent that wasn't already.
  const needsConfirmation = lockMode === "COMPLIANCE" && !wasCompliance;
  const minimumDays = wasCompliance ? policy.retentionDays : 1;
  const tooShort = retentionDays < minimumDays;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const input = { folderId, retentionDays, lockMode, clockStart, appliesToSubfolders, onExpiry };
    try {
      setSaving(true);
      setError("");
      if (policy) {
        await updatePolicy(slug, policy.id, input, token);
      } else {
        await createPolicy(slug, input, token);
      }
      notify.success("Policy saved", `Documents in ${trayName} are kept for ${describeDays(retentionDays)}`);
      await onSaved();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the policy");
      setSaving(false);
    }
  }

  // In a portal because the workspace's enter animation would otherwise make "fixed" relative
  // to it. m-auto rather than centering the container, so a tall form on a short screen scrolls
  // from its top instead of having it cut off.
  return createPortal(
    <div role="dialog" aria-modal="true" aria-label={policy ? "Edit retention policy" : "Add retention policy"} className="fixed inset-0 z-50 flex overflow-y-auto p-4">
      <div className="fixed inset-0 bg-black/50 backdrop-blur-xs" onClick={onClose} />
      <form onSubmit={handleSubmit} className="relative z-10 m-auto w-full max-w-lg space-y-4 rounded-2xl border border-gray-200 bg-white p-6 shadow-2xl dark:border-gray-800 dark:bg-gray-900">
        <h2 className="text-base font-bold text-gray-900 dark:text-white">{policy ? `Policy on ${policy.folderName}` : "Add retention policy"}</h2>

        {!policy && (
          <label className={LABEL}>
            Tray
            <select value={folderId} onChange={(e) => setFolderId(e.target.value)} required className={INPUT}>
              {trays.map((tray) => <option key={tray.id} value={tray.id}>{tray.name}</option>)}
            </select>
          </label>
        )}

        <fieldset>
          <legend className={LABEL}>Keep for</legend>
          <div className="mt-1 flex flex-wrap gap-1.5">
            {PRESET_YEARS.map((years) => {
              const days = years * DAYS_PER_YEAR;
              const selected = !custom && retentionDays === days;
              return (
                <button
                  key={years}
                  type="button"
                  aria-pressed={selected}
                  disabled={days < minimumDays}
                  onClick={() => { setCustom(false); setRetentionDays(days); }}
                  className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${selected ? "border-indigo-600 bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300" : "border-gray-200 text-gray-600 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800"}`}
                >
                  {years}y
                </button>
              );
            })}
            <button
              type="button"
              aria-pressed={custom}
              onClick={() => setCustom(true)}
              className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors ${custom ? "border-indigo-600 bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300" : "border-gray-200 text-gray-600 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800"}`}
            >
              Custom
            </button>
          </div>
          {custom && (
            <label className={`${LABEL} mt-2`}>
              Days
              <input type="number" min={minimumDays} max={36500} required value={retentionDays} onChange={(e) => setRetentionDays(Number(e.target.value))} className={INPUT} />
            </label>
          )}
        </fieldset>

        <fieldset>
          <legend className={LABEL}>Lock mode</legend>
          <div className="mt-1 space-y-2">
            <label className={`flex items-start gap-2 rounded-xl border border-gray-200 p-3 text-xs dark:border-gray-700 ${wasCompliance ? "opacity-50" : ""}`}>
              <input type="radio" name="lockMode" checked={lockMode === "GOVERNANCE"} disabled={wasCompliance} onChange={() => setLockMode("GOVERNANCE")} className="mt-0.5" />
              <span>
                <span className="font-semibold text-gray-900 dark:text-white">Governance</span>
                <span className="mt-0.5 block text-gray-500 dark:text-gray-400">The Owner can make an exception, with a reason that's recorded. The policy can be changed or removed.</span>
              </span>
            </label>
            <label className="flex items-start gap-2 rounded-xl border border-gray-200 p-3 text-xs dark:border-gray-700">
              <input type="radio" name="lockMode" checked={lockMode === "COMPLIANCE"} onChange={() => setLockMode("COMPLIANCE")} className="mt-0.5" />
              <span>
                <span className="font-semibold text-gray-900 dark:text-white">Compliance</span>
                <span className="mt-0.5 block text-gray-500 dark:text-gray-400">Nobody can delete these documents early, including the Owner. The policy can only be extended, never shortened or removed.</span>
              </span>
            </label>
          </div>
        </fieldset>

        <div className="grid gap-3 sm:grid-cols-2">
          <label className={LABEL}>
            Count from
            <select value={clockStart} disabled={wasCompliance} onChange={(e) => setClockStart(e.target.value as ClockStart)} className={INPUT}>
              {(Object.keys(CLOCK_LABELS) as ClockStart[]).map((value) => <option key={value} value={value}>{CLOCK_LABELS[value]}</option>)}
            </select>
          </label>
          <label className={LABEL}>
            When the time is up
            <select value={onExpiry} onChange={(e) => setOnExpiry(e.target.value as OnExpiry)} className={INPUT}>
              {(Object.keys(ON_EXPIRY_LABELS) as OnExpiry[]).map((value) => <option key={value} value={value}>{ON_EXPIRY_LABELS[value]}</option>)}
            </select>
          </label>
        </div>
        {onExpiry === "AUTO_DELETE" && clockStart === "UPLOADED_AT" && (
          <p className="rounded-xl bg-amber-50 p-3 text-xs text-amber-800 dark:bg-amber-950/30 dark:text-amber-300">
            Documents already older than this will go to the Trash the next time the job runs.
          </p>
        )}

        <label className="flex items-center gap-2 text-xs text-gray-700 dark:text-gray-300">
          <input type="checkbox" checked={appliesToSubfolders} disabled={wasCompliance && policy.appliesToSubfolders} onChange={(e) => setAppliesToSubfolders(e.target.checked)} />
          Include sub-folders
        </label>

        {needsConfirmation && (
          <label className="block rounded-xl border border-red-200 bg-red-50 p-3 text-xs font-medium text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-300">
            This can't be shortened or removed, by anyone. Type <strong>{trayName}</strong> to confirm.
            <input value={confirmName} onChange={(e) => setConfirmName(e.target.value)} aria-label="Type the tray name to confirm" autoComplete="off" className={INPUT} />
          </label>
        )}

        {tooShort && <p className="text-xs text-red-600 dark:text-red-400">A Compliance policy can only be extended: at least {minimumDays} days.</p>}
        {error && (
          <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-600 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
            {error}
          </div>
        )}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-xl border border-gray-200 px-4 py-2 text-xs font-semibold text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800">Cancel</button>
          <button
            type="submit"
            disabled={saving || !folderId || tooShort || (needsConfirmation && confirmName !== trayName)}
            className="rounded-xl bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {saving ? "Saving..." : "Save policy"}
          </button>
        </div>
      </form>
    </div>,
    document.body,
  );
}
