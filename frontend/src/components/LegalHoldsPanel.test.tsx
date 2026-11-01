import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LegalHoldsPanel } from "./LegalHoldsPanel";
import { createLegalHold, listHoldDocuments, listLegalHolds, releaseLegalHold, type LegalHold } from "../services/retentionService";
import type { Tray } from "../services/trayService";

vi.mock("../services/retentionService", () => ({
  listLegalHolds: vi.fn(),
  createLegalHold: vi.fn(async () => ({ documentCount: 3 })),
  releaseLegalHold: vi.fn(async () => undefined),
  listHoldDocuments: vi.fn(async () => ({ content: [{ id: "d", originalFileName: "evidence.pdf" }], totalElements: 1, totalPages: 1, number: 0 })),
}));

const TRAYS = [
  { id: "inbox", name: "Inbox", isDefault: true },
  { id: "trash", name: "Trash", isDefault: true },
] as Tray[];

const HOLD: LegalHold = {
  id: "h1",
  name: "Case 42",
  reason: "Litigation",
  createdByName: "Ada",
  createdAt: "2026-01-01T00:00:00Z",
  releasedAt: null,
  documentCount: 1,
};

function setUp(permissions: string[], holds: LegalHold[] = [HOLD]) {
  vi.mocked(listLegalHolds).mockResolvedValue(holds);
  render(<LegalHoldsPanel slug="acme" token="t" trays={TRAYS} permissions={new Set(permissions)} />);
}

describe("LegalHoldsPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(cleanup);

  it("only offers release to someone who can release", async () => {
    setUp(["retention.read", "retention.manage"]);

    expect(await screen.findByText("Case 42")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Release" })).toBeNull();
    expect(screen.getByRole("button", { name: "Apply hold" })).toBeTruthy();
  });

  it("releases after asking", async () => {
    setUp(["retention.read", "retention.manage", "retention.override"]);

    fireEvent.click(await screen.findByRole("button", { name: "Release" }));
    fireEvent.click(screen.getByRole("button", { name: "Release hold" }));

    await waitFor(() => expect(releaseLegalHold).toHaveBeenCalledWith("acme", "h1", "t"));
  });

  it("doesn't offer release on one already released", async () => {
    setUp(["retention.read", "retention.override"], [{ ...HOLD, releasedAt: "2026-02-01T00:00:00Z" }]);

    expect(await screen.findByText(/Released 1 Feb 2026/)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Release" })).toBeNull();
  });

  it("applies a hold to a whole tray, and the Trash isn't a choice", async () => {
    setUp(["retention.read", "retention.manage"], []);

    const select = (await screen.findByLabelText("Tray")) as HTMLSelectElement;
    expect(Array.from(select.options).map((option) => option.text)).toEqual(["Choose a tray", "Inbox"]);

    fireEvent.change(select, { target: { value: "inbox" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply hold" }));
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Case 43" } });
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Audit" } });
    const buttons = screen.getAllByRole("button", { name: "Apply hold" });
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() => expect(createLegalHold).toHaveBeenCalledWith("acme", "Case 43", "Audit", { folderIds: ["inbox"] }, "t"));
  });

  it("applies a hold to search results", async () => {
    setUp(["retention.read", "retention.manage"], []);

    fireEvent.change(await screen.findByLabelText("Cover"), { target: { value: "search" } });
    fireEvent.change(screen.getByLabelText("Search for"), { target: { value: "contract" } });
    fireEvent.click(screen.getByRole("button", { name: "Apply hold" }));
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Case 44" } });
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Discovery" } });
    const buttons = screen.getAllByRole("button", { name: "Apply hold" });
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() => expect(createLegalHold).toHaveBeenCalledWith("acme", "Case 44", "Discovery", { searchQuery: "contract" }, "t"));
  });

  it("shows what a hold covers", async () => {
    setUp(["retention.read"]);

    fireEvent.click(await screen.findByRole("button", { name: "1 document" }));

    expect(await screen.findByText("evidence.pdf")).toBeTruthy();
    expect(listHoldDocuments).toHaveBeenCalledWith("acme", "h1", "t");
  });
});
