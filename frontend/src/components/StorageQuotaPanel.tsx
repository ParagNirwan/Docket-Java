import { useEffect, useState } from "react";
import { HardDrive, CheckCircle2, ShieldCheck, Loader2 } from "lucide-react";
import { getTenantUsage, updateTenantQuota, type TenantUsageDTO } from "../services/trayService";
import { formatFileSize } from "../services/documentService";

interface StorageQuotaPanelProps {
  slug: string;
  token: string;
  canManageQuota: boolean;
}

export function StorageQuotaPanel({ slug, token, canManageQuota }: StorageQuotaPanelProps) {
  const [usage, setUsage] = useState<TenantUsageDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Edit quota state
  const [quotaInputMb, setQuotaInputMb] = useState("");
  const [isUnlimited, setIsUnlimited] = useState(false);

  async function loadUsage() {
    try {
      setLoading(true);
      setError(null);
      const data = await getTenantUsage(slug, token);
      setUsage(data);
      if (data.storageQuotaBytes === null) {
        setIsUnlimited(true);
        setQuotaInputMb("");
      } else {
        setIsUnlimited(false);
        setQuotaInputMb(String(Math.round(data.storageQuotaBytes / (1024 * 1024))));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load storage usage");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadUsage();
  }, [slug, token]);

  async function handleSaveQuota(e: React.FormEvent) {
    e.preventDefault();
    try {
      setSaving(true);
      setError(null);
      setSuccess(null);

      const quotaBytes = isUnlimited ? null : Number(quotaInputMb) * 1024 * 1024;
      const updated = await updateTenantQuota(slug, quotaBytes, token);
      setUsage(updated);
      setSuccess("Storage quota updated successfully");
      setTimeout(() => setSuccess(null), 3000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update storage quota");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-12 text-gray-400">
        <Loader2 className="w-6 h-6 animate-spin" />
      </div>
    );
  }

  const bytesUsed = usage?.storageBytesUsed ?? 0;
  const quotaBytes = usage?.storageQuotaBytes;
  const percentage = quotaBytes && quotaBytes > 0 ? Math.min(100, (bytesUsed / quotaBytes) * 100) : 0;

  const isWarning = quotaBytes && bytesUsed >= quotaBytes * 0.8 && bytesUsed < quotaBytes;
  const isExceeded = quotaBytes && bytesUsed >= quotaBytes;

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h2 className="text-base font-bold text-gray-900 dark:text-white">Storage & Quotas</h2>
        <p className="text-xs text-gray-500 dark:text-gray-400">
          Monitor your organization's storage consumption across documents and versions.
        </p>
      </div>

      {error && (
        <div role="alert" className="p-3 text-xs text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-950/30 rounded-xl border border-red-200 dark:border-red-900/60">
          {error}
        </div>
      )}

      {success && (
        <div role="status" className="p-3 text-xs text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/30 rounded-xl border border-emerald-200 dark:border-emerald-900/60 flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>{success}</span>
        </div>
      )}

      {/* Usage Overview Card */}
      <div className="p-5 rounded-2xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-xs space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center">
              <HardDrive className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs text-gray-500 dark:text-gray-400">Current Storage Used</div>
              <div className="text-lg font-bold text-gray-900 dark:text-white">
                {formatFileSize(bytesUsed)}{" "}
                <span className="text-xs font-normal text-gray-500 dark:text-gray-400">
                  {quotaBytes ? `/ ${formatFileSize(quotaBytes)}` : "(Unlimited quota)"}
                </span>
              </div>
            </div>
          </div>

          <div className="text-right">
            <div className="text-xs text-gray-500 dark:text-gray-400">Total Documents</div>
            <div className="text-lg font-bold text-gray-900 dark:text-white">
              {usage?.documentCount ?? 0}
            </div>
          </div>
        </div>

        {/* Progress bar (if quota is configured) */}
        {quotaBytes ? (
          <div className="space-y-1.5 pt-1">
            <div className="flex justify-between text-xs text-gray-500 dark:text-gray-400">
              <span>{percentage.toFixed(1)}% used</span>
              <span>
                {isExceeded
                  ? "Quota exceeded!"
                  : isWarning
                  ? "Approaching quota limit"
                  : `${formatFileSize(Math.max(0, quotaBytes - bytesUsed))} remaining`}
              </span>
            </div>
            <div className="w-full h-2.5 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
              <div
                style={{ width: `${percentage}%` }}
                className={`h-full rounded-full transition-all duration-300 ${
                  isExceeded
                    ? "bg-red-600"
                    : isWarning
                    ? "bg-amber-500"
                    : "bg-indigo-600"
                }`}
              />
            </div>
          </div>
        ) : (
          <div className="pt-1 flex items-center gap-1.5 text-xs text-emerald-600 dark:text-emerald-400 font-medium">
            <ShieldCheck className="w-4 h-4" />
            Unlimited storage plan active. No upload restrictions.
          </div>
        )}
      </div>

      {/* Quota Management Card (Admins only) */}
      {canManageQuota && (
        <form
          onSubmit={handleSaveQuota}
          className="p-5 rounded-2xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-xs space-y-4"
        >
          <div>
            <h3 className="text-sm font-bold text-gray-900 dark:text-white">Manage Storage Limit</h3>
            <p className="text-xs text-gray-500 dark:text-gray-400">
              Set the maximum total file storage allowed for this workspace.
            </p>
          </div>

          <div className="space-y-3">
            <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-gray-700 dark:text-gray-300">
              <input
                type="checkbox"
                checked={isUnlimited}
                onChange={(e) => {
                  setIsUnlimited(e.target.checked);
                  if (e.target.checked) setQuotaInputMb("");
                }}
                className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 h-4 w-4"
              />
              No storage limit (Unlimited)
            </label>

            {!isUnlimited && (
              <div className="max-w-xs space-y-1">
                <label className="block text-xs font-semibold text-gray-700 dark:text-gray-300">
                  Storage Quota (in Megabytes)
                </label>
                <div className="flex items-center gap-2">
                  <input
                    type="number"
                    min="1"
                    placeholder="e.g. 500"
                    value={quotaInputMb}
                    onChange={(e) => setQuotaInputMb(e.target.value)}
                    required={!isUnlimited}
                    className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 px-3.5 py-2 text-xs text-gray-900 dark:text-white focus:outline-none focus:ring-1 focus:ring-indigo-500"
                  />
                  <span className="text-xs font-medium text-gray-500 dark:text-gray-400">MB</span>
                </div>
              </div>
            )}
          </div>

          <div className="pt-2 border-t border-gray-100 dark:border-gray-800 flex justify-end">
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 text-xs font-semibold rounded-full bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors disabled:opacity-50"
            >
              {saving ? "Saving..." : "Save Quota"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

