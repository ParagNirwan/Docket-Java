import { describe, expect, it } from "vitest";
import { toRows } from "../lib/diffRows";

describe("toRows", () => {
  it("pairs a removed and added run side by side as a change", () => {
    const rows = toRows([
      { kind: "EQUAL", text: "Term: 12 months" },
      { kind: "REMOVED", text: "Rent: $4,000" },
      { kind: "ADDED", text: "Rent: $4,500\nDeposit: $9,000" },
    ]);

    expect(rows).toEqual([
      { left: "Term: 12 months", right: "Term: 12 months", kind: "same" },
      { left: "Rent: $4,000", right: "Rent: $4,500", kind: "changed" },
      { left: null, right: "Deposit: $9,000", kind: "changed" },
    ]);
  });

  it("keeps pure additions and removals on their own side", () => {
    expect(toRows([{ kind: "ADDED", text: "new" }])).toEqual([{ left: null, right: "new", kind: "added" }]);
    expect(toRows([{ kind: "REMOVED", text: "old" }])).toEqual([{ left: "old", right: null, kind: "removed" }]);
  });
});
