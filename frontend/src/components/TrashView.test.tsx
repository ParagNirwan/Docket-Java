import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TrashView } from "./TrashView";
import { emptyTrash, listTrash, purgeDocument, restoreDocument, type TrashDocument } from "../services/retentionService";

vi.mock("../services/retentionService", () => ({
  listTrash: vi.fn(),
  restoreDocument: vi.fn(async () => undefined),
  purgeDocument: vi.fn(async () => undefined),
  emptyTrash: vi.fn(async () => ({ purged: 1, skipped: 1 })),
}));

function item(overrides: Partial<TrashDocument>): TrashDocument {
  return {
    id: "doc-1",
    originalFileName: "report.pdf",
    contentType: "application/pdf",
    fileSize: 100,
    deletedAt: "2026-10-01T00:00:00Z",
    deletedByName: "Ada",
    originalFolderName: "Contracts",
    purgeAfter: "2026-10-31T00:00:00Z",
    retainUntil: null,
    lockMode: null,
    legalHold: false,
    ...overrides,
  };
}

function trashContains(...items: TrashDocument[]) {
  vi.mocked(listTrash).mockResolvedValue({ content: items, totalElements: items.length, totalPages: 1, number: 0 });
}

describe("TrashView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(cleanup);

  it("shows where a document came from and when it goes", async () => {
    trashContains(item({}));
    render(<TrashView slug="acme" token="t" />);

    expect(await screen.findByText("report.pdf")).toBeTruthy();
    expect(screen.getByText("Contracts")).toBeTruthy();
    expect(screen.getByText("31 Oct 2026")).toBeTruthy();
  });

  it("says when the original folder is gone", async () => {
    trashContains(item({ originalFolderName: null }));
    render(<TrashView slug="acme" token="t" />);

    expect(await screen.findByText(/restores to the Inbox/)).toBeTruthy();
  });

  it("restores and reloads", async () => {
    trashContains(item({}));
    render(<TrashView slug="acme" token="t" />);

    fireEvent.click(await screen.findByRole("button", { name: "Restore report.pdf" }));

    await waitFor(() => expect(restoreDocument).toHaveBeenCalledWith("acme", "doc-1", "t"));
    await waitFor(() => expect(listTrash).toHaveBeenCalledTimes(2));
  });

  it("asks before deleting for good", async () => {
    trashContains(item({}));
    render(<TrashView slug="acme" token="t" />);

    fireEvent.click(await screen.findByRole("button", { name: "Delete report.pdf permanently" }));
    expect(purgeDocument).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Delete permanently" }));

    await waitFor(() => expect(purgeDocument).toHaveBeenCalledWith("acme", "doc-1", "t"));
  });

  it("won't let a held document be deleted for good", async () => {
    trashContains(item({ legalHold: true }));
    render(<TrashView slug="acme" token="t" />);

    const button = (await screen.findByRole("button", { name: "Delete report.pdf permanently" })) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.title).toBe("Under legal hold");
  });

  it("empties the Trash after asking", async () => {
    trashContains(item({}));
    render(<TrashView slug="acme" token="t" />);

    fireEvent.click(await screen.findByRole("button", { name: "Empty Trash" }));
    const buttons = screen.getAllByRole("button", { name: "Empty Trash" });
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() => expect(emptyTrash).toHaveBeenCalledWith("acme", "t"));
  });

  it("has an empty state", async () => {
    trashContains();
    render(<TrashView slug="acme" token="t" />);

    expect(await screen.findByText("The Trash is empty")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Empty Trash" })).toBeNull();
  });
});
