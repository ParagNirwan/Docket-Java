import { useEffect, useState } from "react";
import { Loader2, Sparkles } from "lucide-react";
import { getAiSettings, updateAiSettings, type AiSettings } from "../services/aiService";

interface AiSettingsPanelProps {
  slug: string;
  token: string;
  canManage: boolean;
}

export function AiSettingsPanel({ slug, token, canManage }: AiSettingsPanelProps) {
  const [settings, setSettings] = useState<AiSettings | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getAiSettings(slug, token)
      .then((loaded) => {
        if (active) setSettings(loaded);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : "Failed to load AI settings");
      });
    return () => {
      active = false;
    };
  }, [slug, token]);

  async function save(change: Partial<AiSettings>) {
    if (!settings) return;
    setSaving(true);
    setError(null);
    try {
      setSettings(await updateAiSettings(slug, { summariesEnabled: settings.summariesEnabled, ...change }, token));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update AI settings");
    } finally {
      setSaving(false);
    }
  }

  const toggles: { key: "summariesEnabled" | "classificationEnabled" | "autoApplyTags"; title: string; help: string; disabled?: boolean }[] = [
    {
      key: "summariesEnabled",
      title: "Summarize documents automatically",
      help: "Turning this off deletes the summaries already written. Turning it back on only summarizes documents uploaded or re-indexed afterwards.",
    },
    {
      key: "classificationEnabled",
      title: "Classify documents and extract key fields",
      help: "Detects the document type, parties, amounts, key dates and clauses, and suggests tags. Turning this off deletes unreviewed results.",
    },
    {
      key: "autoApplyTags",
      title: "Apply suggested tags automatically",
      help: "Only tags that already exist in this workspace are applied; new ones are suggested, never created.",
      disabled: settings?.classificationEnabled === false,
    },
  ];

  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-xs dark:border-gray-800 dark:bg-gray-900">
      <div className="mb-4 flex items-start gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
          <Sparkles className="h-5 w-5" />
        </div>
        <div>
          <h2 className="text-base font-semibold text-gray-900 dark:text-white">AI document processing</h2>
          <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
            When a document is indexed, AI can summarize it, classify it and pull out key fields. Results appear
            in the document preview and help AI search.
          </p>
        </div>
      </div>

      {settings === null && !error ? (
        <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading…
        </div>
      ) : (
        settings !== null && (
          <div className="space-y-4">
            {toggles.map((toggle) => (
              <label key={toggle.key} className="flex items-start gap-3">
                <input
                  type="checkbox"
                  checked={settings[toggle.key] !== false}
                  disabled={!canManage || saving || toggle.disabled}
                  onChange={(event) => void save({ [toggle.key]: event.target.checked })}
                  className="mt-0.5 h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 disabled:opacity-50"
                />
                <span>
                  <span className="block text-sm font-medium text-gray-900 dark:text-white">{toggle.title}</span>
                  <span className="block text-xs text-gray-500 dark:text-gray-400">{toggle.help}</span>
                </span>
              </label>
            ))}
            {canManage && <SearchTuning settings={settings} saving={saving} onSave={save} />}
            {!canManage && (
              <p className="text-xs text-gray-400 dark:text-gray-500">Only an owner or admin can change these.</p>
            )}
          </div>
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

const TUNING_FIELDS = [
  { key: "maxDistance", label: "Max distance", min: 0.05, max: 2, step: 0.05,
    help: "How close a passage must be to the question. Lower is stricter (server default 0.45)." },
  { key: "fallbackMaxDistance", label: "Fallback distance", min: 0.05, max: 2, step: 0.05,
    help: "Looser bound used when nothing passes the first (default 0.6). Must be at least the max distance." },
  { key: "rerankMinScore", label: "Re-rank minimum score", min: 0, max: 1, step: 0.05,
    help: "When re-ranking is on, passages scoring below this are dropped (default 0.3)." },
] as const;

type TuningKey = (typeof TUNING_FIELDS)[number]["key"];

// Per-organization overrides of the server's search thresholds. Blank = server default.
function SearchTuning({ settings, saving, onSave }: {
  settings: AiSettings;
  saving: boolean;
  onSave: (change: Partial<AiSettings>) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<Record<TuningKey, string>>({
    maxDistance: settings.maxDistance?.toString() ?? "",
    fallbackMaxDistance: settings.fallbackMaxDistance?.toString() ?? "",
    rerankMinScore: settings.rerankMinScore?.toString() ?? "",
  });
  const customized = TUNING_FIELDS.some((field) => settings[field.key] != null);

  return (
    <div className="rounded-xl border border-gray-200 p-3 dark:border-gray-800">
      <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open}
        className="text-xs font-semibold text-gray-700 dark:text-gray-200">
        Advanced search tuning{customized ? " (customized)" : ""}
      </button>
      {open && (
        <div className="mt-3 space-y-3">
          {TUNING_FIELDS.map((field) => (
            <label key={field.key} className="block text-xs text-gray-600 dark:text-gray-300">
              <span className="font-medium">{field.label}</span>
              <input type="number" min={field.min} max={field.max} step={field.step} placeholder="Server default"
                value={draft[field.key]} onChange={(e) => setDraft({ ...draft, [field.key]: e.target.value })}
                className="mt-1 block w-40 rounded-lg border border-gray-300 bg-white px-2 py-1 text-sm dark:border-gray-700 dark:bg-gray-950" />
              <span className="mt-0.5 block text-[11px] text-gray-500">{field.help}</span>
            </label>
          ))}
          <div className="flex gap-2">
            <button type="button" disabled={saving}
              onClick={() => void onSave(Object.fromEntries(
                TUNING_FIELDS.filter((f) => draft[f.key] !== "").map((f) => [f.key, Number(draft[f.key])]),
              ))}
              className="rounded-full bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-indigo-700 disabled:opacity-50">
              Save tuning
            </button>
            {customized && (
              <button type="button" disabled={saving}
                onClick={() => {
                  setDraft({ maxDistance: "", fallbackMaxDistance: "", rerankMinScore: "" });
                  void onSave({ resetRetrievalTuning: true });
                }}
                className="rounded-full border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-700 dark:text-gray-200">
                Reset to defaults
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
