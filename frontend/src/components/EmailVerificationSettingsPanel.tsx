import { useEffect, useState } from "react";
import { Loader2, MailCheck } from "lucide-react";
import {
  getEmailVerificationSettings,
  updateEmailVerificationSettings,
} from "../services/emailVerificationService";

interface EmailVerificationSettingsPanelProps {
  slug: string;
  token: string;
  canManage: boolean;
}

export function EmailVerificationSettingsPanel({
  slug,
  token,
  canManage,
}: EmailVerificationSettingsPanelProps) {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getEmailVerificationSettings(slug, token)
      .then((settings) => {
        if (active) setEnabled(settings.enabled);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : "Failed to load email settings");
      });
    return () => {
      active = false;
    };
  }, [slug, token]);

  async function handleToggle(nextEnabled: boolean) {
    setSaving(true);
    setError(null);
    try {
      const settings = await updateEmailVerificationSettings(slug, { enabled: nextEnabled }, token);
      setEnabled(settings.enabled);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update email settings");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-xs dark:border-gray-800 dark:bg-gray-900">
      <div className="mb-4 flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
          <MailCheck className="h-5 w-5" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-gray-900 dark:text-white">Email verification</h2>
          <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
            Send verification links to unverified members of this workspace. Verification is optional and
            does not restrict access. Email delivery must be configured by the service administrator.
          </p>
        </div>
      </div>

      {enabled === null && !error ? (
        <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading…
        </div>
      ) : (
        enabled !== null && (
          <label className="flex items-start gap-3">
            <input
              type="checkbox"
              checked={enabled}
              disabled={!canManage || saving}
              onChange={(event) => void handleToggle(event.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 disabled:opacity-50"
            />
            <span>
              <span className="block text-sm font-medium text-gray-900 dark:text-white">
                Send verification emails
              </span>
              <span className="block text-xs text-gray-500 dark:text-gray-400">
                Enabling this sends a verification email to existing unverified members and enables
                reminders for workspace members. It is off by default.
              </span>
              {!canManage && (
                <span className="mt-1 block text-xs text-gray-400 dark:text-gray-500">
                  Only an owner or admin can change this.
                </span>
              )}
            </span>
          </label>
        )
      )}

      {error && (
        <p role="alert" className="mt-3 text-xs text-red-600 dark:text-red-400">
          {error}
        </p>
      )}
    </section>
  );
}
