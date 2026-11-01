import { describe, expect, it } from "vitest";
import { canBeOverridden, isLocked, isReadOnly, lockDescription } from "./retention";

const FUTURE = "2099-03-12T00:00:00Z";
const PAST = "2001-03-12T00:00:00Z";

describe("retention", () => {
  it("describes the lock the way people should read it", () => {
    expect(lockDescription({ retainUntil: FUTURE, lockMode: "COMPLIANCE" })).toBe("Locked until 12 Mar 2099 (Compliance)");
  });

  it("treats a date that's passed as no lock at all", () => {
    const doc = { retainUntil: PAST, lockMode: "COMPLIANCE" as const };
    expect(isLocked(doc)).toBe(false);
    expect(isReadOnly(doc)).toBe(false);
    expect(lockDescription(doc)).toBeNull();
  });

  it("puts a legal hold ahead of everything, even an expired date", () => {
    const doc = { retainUntil: PAST, lockMode: "GOVERNANCE" as const, legalHold: true };
    expect(isLocked(doc)).toBe(true);
    expect(canBeOverridden(doc)).toBe(false);
    expect(lockDescription(doc)).toBe("Under legal hold");
  });

  it("only offers an override on an active Governance lock", () => {
    expect(canBeOverridden({ retainUntil: FUTURE, lockMode: "GOVERNANCE" })).toBe(true);
    expect(canBeOverridden({ retainUntil: FUTURE, lockMode: "COMPLIANCE" })).toBe(false);
    expect(canBeOverridden({ retainUntil: PAST, lockMode: "GOVERNANCE" })).toBe(false);
  });

  it("keeps an archived document read-only after its lock ends", () => {
    const doc = { archivedAt: PAST, retainUntil: PAST };
    expect(isLocked(doc)).toBe(false);
    expect(isReadOnly(doc)).toBe(true);
  });
});
