import { useState, type ReactNode } from "react";
import { Check, Copy, KeyRound, type LucideIcon } from "lucide-react";
import { SECONDARY_BUTTON } from "../lib/settingsUi";

export function SettingsSection({
  icon: Icon,
  title,
  description,
  action,
  children,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-xs dark:border-gray-800 dark:bg-gray-900">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
            <Icon className="h-5 w-5" />
          </div>
          <div>
            <h2 className="text-base font-semibold text-gray-900 dark:text-white">{title}</h2>
            <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{description}</p>
          </div>
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

export function ErrorText({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="mt-3 text-xs text-red-600 dark:text-red-400">
      {message}
    </p>
  );
}

export function CopyButton({ value, label = "Copy" }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      className={SECONDARY_BUTTON}
      onClick={() => {
        void navigator.clipboard?.writeText(value).then(() => {
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        });
      }}
    >
      {copied ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
      {copied ? "Copied" : label}
    </button>
  );
}

// Secrets are only returned once by the API, so this says so plainly.
export function OneTimeSecret({ title, value, onDismiss }: { title: string; value: string; onDismiss: () => void }) {
  return (
    <div role="status" className="mb-4 rounded-xl border border-amber-300 bg-amber-50 p-4 dark:border-amber-800/60 dark:bg-amber-950/30">
      <div className="flex items-center gap-2 text-sm font-semibold text-amber-900 dark:text-amber-200">
        <KeyRound className="h-4 w-4" />
        {title}
      </div>
      <p className="mt-1 text-xs text-amber-800 dark:text-amber-300">
        Copy it now. For your security it won't be shown again.
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <code className="min-w-0 flex-1 break-all rounded-lg bg-white px-3 py-2 font-mono text-xs text-gray-900 dark:bg-gray-950 dark:text-gray-100">
          {value}
        </code>
        <CopyButton value={value} />
        <button type="button" className={SECONDARY_BUTTON} onClick={onDismiss}>
          Done
        </button>
      </div>
    </div>
  );
}

