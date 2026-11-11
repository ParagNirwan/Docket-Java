import { useEffect, useState } from "react";
import { Loader2, Sparkles } from "lucide-react";
import { getAiSettings, updateAiSettings } from "../services/aiService";

interface AiSettingsPanelProps {
  slug: string;
  token: string;
  canManage: boolean;
}

export function AiSettingsPanel({ slug, token, canManage }: AiSettingsPanelProps) {
  const [summariesEnabled, setSummariesEnabled] = useState<boolean | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getAiSettings(slug, token)
      .then((settings) => {
        if (active) setSummariesEnabled(settings.summariesEnabled);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : "Failed to load AI settings");
      });
    return () => {
      active = false;
    };
  }, [slug, token]);

  async function handleToggle(enabled: boolean) {
    setSaving(true);
    setError(null);
    try {
      const saved = await updateAiSettings(slug, { summariesEnabled: enabled }, token);
      setSummariesEnabled(saved.summariesEnabled);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update AI settings");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-xs dark:border-gray-800 dark:bg-gray-900">
      <div className="mb-4 flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
          <Sparkles className="h-5 w-5" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-gray-900 dark:text-white">AI summaries</h2>
          <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
            When a document is indexed, AI writes a short summary and its key points. They appear in the
            document preview and help AI search answer questions about the workspace as a whole.
          </p>
        </div>
      </div>

      {summariesEnabled === null && !error ? (
        <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading…
        </div>
      ) : (
        summariesEnabled !== null && (
          <label className="flex items-start gap-3">
            <input
              type="checkbox"
              checked={summariesEnabled}
              disabled={!canManage || saving}
              onChange={(event) => void handleToggle(event.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 disabled:opacity-50"
            />
            <span>
              <span className="block text-sm font-medium text-gray-900 dark:text-white">
                Summarize documents automatically
              </span>
              <span className="block text-xs text-gray-500 dark:text-gray-400">
                Turning this off deletes the summaries already written. Turning it back on only summarizes
                documents uploaded or re-indexed afterwards.
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
