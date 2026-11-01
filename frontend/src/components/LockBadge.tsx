import { Archive, Gavel, Lock } from "lucide-react";
import { isRetentionLocked, lockDescription, type LockFields } from "../lib/retention";

// Shown on a document that can't be changed, and says why.
export function LockBadge({ doc }: { doc: LockFields }) {
  const description = lockDescription(doc);
  if (!description) return null;

  const Icon = doc.legalHold ? Gavel : isRetentionLocked(doc) ? Lock : Archive;
  const colors = doc.legalHold
    ? "bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300"
    : isRetentionLocked(doc)
      ? "bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300"
      : "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300";

  return (
    <span title={description} aria-label={description} role="img" className={`inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-md ${colors}`}>
      <Icon className="h-3 w-3" />
    </span>
  );
}
