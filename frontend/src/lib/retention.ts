// What's protecting a document, in words. The server enforces all of this: here it only
// decides what to show, so the dates are checked against now rather than a stored flag.
export type LockMode = "GOVERNANCE" | "COMPLIANCE";

export interface LockFields {
  retainUntil?: string | null;
  lockMode?: LockMode | null;
  legalHold?: boolean;
  archivedAt?: string | null;
}

export const LOCK_MODE_LABELS: Record<LockMode, string> = {
  GOVERNANCE: "Governance",
  COMPLIANCE: "Compliance",
};

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
}

export function isRetentionLocked(doc: LockFields): boolean {
  return !!doc.retainUntil && new Date(doc.retainUntil).getTime() > Date.now();
}

// Can't be deleted or moved at all without an override, or not even with one.
export function isLocked(doc: LockFields): boolean {
  return !!doc.legalHold || isRetentionLocked(doc);
}

// Read-only: the name and the location can't change.
export function isReadOnly(doc: LockFields): boolean {
  return isLocked(doc) || !!doc.archivedAt;
}

// An Owner can go past this one with a reason. Nobody can go past the others.
export function canBeOverridden(doc: LockFields): boolean {
  return !doc.legalHold && isRetentionLocked(doc) && doc.lockMode === "GOVERNANCE";
}

export function lockDescription(doc: LockFields): string | null {
  if (doc.legalHold) return "Under legal hold";
  if (isRetentionLocked(doc)) {
    const mode = doc.lockMode ? ` (${LOCK_MODE_LABELS[doc.lockMode]})` : "";
    return `Locked until ${formatDate(doc.retainUntil!)}${mode}`;
  }
  if (doc.archivedAt) return "Archived, so it's read-only";
  return null;
}
