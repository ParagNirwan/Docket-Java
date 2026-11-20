import { useEffect, useState } from "react";
import { CheckCircle2, ChevronDown, ChevronUp, Tags } from "lucide-react";
import { sendTenantRequest, tenantUrl } from "../services/enterpriseService";

type Insights = {
  documentType: string | null;
  suggestedTags: string[] | null;
  fields: Record<string, unknown> | null;
  keyDate: string | null;
  reviewedAt: string | null;
};

const FIELD_LABELS: Record<string, string> = {
  parties: "Parties",
  amounts: "Amounts",
  keyClauses: "Key clauses",
  effectiveDate: "Effective",
  expiryDate: "Expires",
};

function label(type: string) {
  return type.replaceAll("_", " ").replace(/^./, (c) => c.toUpperCase());
}

// AI classification and extracted fields (#121). Hidden when there's nothing to show.
export function DocumentInsights({ slug, docId, token }: { slug: string; docId: string; token: string }) {
  const [insights, setInsights] = useState<Insights | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const url = tenantUrl(slug, `/documents/${encodeURIComponent(docId)}/insights`);

  useEffect(() => {
    let active = true;
    sendTenantRequest<Insights>(url, token, {}, "Couldn't load insights")
      .then((loaded) => active && setInsights(loaded))
      .catch(() => undefined); // optional extra; the preview works without it
    return () => {
      active = false;
    };
  }, [url, token]);

  if (!insights || (!insights.documentType && !Object.keys(insights.fields ?? {}).length)) return null;

  async function confirm() {
    if (!insights) return;
    setSaving(true);
    try {
      setInsights(await sendTenantRequest<Insights>(url, token,
        { method: "POST", body: JSON.stringify(insights) }, "Couldn't save"));
    } finally {
      setSaving(false);
    }
  }

  const fields = Object.entries(insights.fields ?? {}).filter(([key]) => key in FIELD_LABELS);

  return (
    <section aria-label="AI insights" className="shrink-0 border-b border-gray-100 px-5 py-3 dark:border-gray-800">
      <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open}
        className="flex w-full items-center gap-1.5 text-xs font-semibold text-gray-700 dark:text-gray-200">
        <Tags className="h-3.5 w-3.5" />
        {insights.documentType ? label(insights.documentType) : "Insights"}
        {insights.keyDate && <span className="font-normal text-gray-500">· key date {insights.keyDate}</span>}
        {insights.reviewedAt && <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" aria-label="Reviewed" />}
        {open ? <ChevronUp className="ml-auto h-3.5 w-3.5" /> : <ChevronDown className="ml-auto h-3.5 w-3.5" />}
      </button>
      {open && (
        <div className="mt-2 space-y-2 text-xs text-gray-700 dark:text-gray-300">
          {fields.length > 0 && (
            <dl className="grid grid-cols-[6rem_1fr] gap-x-3 gap-y-1">
              {fields.map(([key, value]) => (
                <div key={key} className="contents">
                  <dt className="font-medium text-gray-500">{FIELD_LABELS[key]}</dt>
                  <dd>{Array.isArray(value) ? value.join("; ") : String(value)}</dd>
                </div>
              ))}
            </dl>
          )}
          {(insights.suggestedTags?.length ?? 0) > 0 && (
            <p>
              <span className="font-medium text-gray-500">Suggested tags: </span>
              {insights.suggestedTags!.join(", ")}
            </p>
          )}
          <div className="flex items-center gap-3">
            <span className="text-[10px] text-gray-400">
              {insights.reviewedAt ? "Reviewed by a person." : "Extracted by AI. Check the document before relying on it."}
            </span>
            {!insights.reviewedAt && (
              <button type="button" onClick={() => void confirm()} disabled={saving}
                className="ml-auto rounded-full border border-gray-300 px-2.5 py-1 text-[11px] font-medium hover:bg-gray-50 disabled:opacity-50 dark:border-gray-700 dark:hover:bg-gray-800">
                Mark as correct
              </button>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
