import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RetentionPanel } from "./RetentionPanel";
import { createPolicy, getRetentionSummary, listPolicies, updatePolicy, type RetentionPolicy } from "../services/retentionService";
import type { Tray } from "../services/trayService";

vi.mock("../services/retentionService", () => ({
  listPolicies: vi.fn(),
  getRetentionSummary: vi.fn(),
  createPolicy: vi.fn(async () => ({})),
  updatePolicy: vi.fn(async () => ({})),
  deletePolicy: vi.fn(async () => undefined),
  updateTrashRetention: vi.fn(async () => undefined),
}));

const MANAGE = new Set(["retention.read", "retention.manage"]);
const READ_ONLY = new Set(["retention.read"]);

function tray(id: string, name: string, isDefault = false): Tray {
  return { id, name, description: null, isTray: true, isDefault, visibility: "ALL_MEMBERS", sortOrder: 1, itemCount: 0 } as Tray;
}

const TRAYS = [tray("archive", "Archive", true), tray("trash", "Trash", true)];

const COMPLIANCE: RetentionPolicy = {
  id: "p1",
  folderId: "archive",
  folderName: "Archive",
  retentionDays: 2555,
  lockMode: "COMPLIANCE",
  clockStart: "ARCHIVED_AT",
  appliesToSubfolders: true,
  onExpiry: "NOTIFY",
  lockedDocuments: 4,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

function setUp(policies: RetentionPolicy[], permissions: ReadonlySet<string> = MANAGE) {
  vi.mocked(listPolicies).mockResolvedValue(policies);
  vi.mocked(getRetentionSummary).mockResolvedValue({
    lockedDocuments: 4,
    expiringSoonCount: 1,
    expiringSoon: [{ id: "d", originalFileName: "soon.pdf", folderId: "archive", retainUntil: "2026-12-01T00:00:00Z", lockMode: "COMPLIANCE" }],
    trashRetentionDays: 30,
  });
  render(<RetentionPanel slug="acme" token="t" trays={TRAYS} permissions={permissions} />);
}

describe("RetentionPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(cleanup);

  it("lists policies, the locked counter and what's expiring soon", async () => {
    setUp([COMPLIANCE]);

    expect(await screen.findByText("7 years")).toBeTruthy();
    expect(screen.getByText("Documents locked")).toBeTruthy();
    expect(screen.getByText("soon.pdf")).toBeTruthy();
  });

  it("doesn't let the Trash have a policy", async () => {
    setUp([]);

    fireEvent.click(await screen.findByRole("button", { name: "Add policy" }));

    const select = screen.getByLabelText("Tray") as HTMLSelectElement;
    expect(Array.from(select.options).map((option) => option.text)).toEqual(["Archive"]);
  });

  it("makes someone type the tray name before creating a Compliance policy", async () => {
    setUp([]);

    fireEvent.click(await screen.findByRole("button", { name: "Add policy" }));
    fireEvent.click(screen.getByLabelText(/^Compliance/));

    const save = screen.getByRole("button", { name: "Save policy" }) as HTMLButtonElement;
    expect(save.disabled).toBe(true);

    fireEvent.change(screen.getByLabelText("Type the tray name to confirm"), { target: { value: "archive" } });
    expect(save.disabled).toBe(true);

    fireEvent.change(screen.getByLabelText("Type the tray name to confirm"), { target: { value: "Archive" } });
    expect(save.disabled).toBe(false);
    fireEvent.click(save);

    await waitFor(() => expect(createPolicy).toHaveBeenCalledWith(
      "acme",
      expect.objectContaining({ folderId: "archive", lockMode: "COMPLIANCE", retentionDays: 2555 }),
      "t",
    ));
  });

  it("doesn't ask for that on a Governance policy", async () => {
    setUp([]);

    fireEvent.click(await screen.findByRole("button", { name: "Add policy" }));

    expect(screen.queryByLabelText("Type the tray name to confirm")).toBeNull();
    expect((screen.getByRole("button", { name: "Save policy" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("uses the presets and a custom length", async () => {
    setUp([]);

    fireEvent.click(await screen.findByRole("button", { name: "Add policy" }));
    fireEvent.click(screen.getByRole("button", { name: "Custom" }));
    fireEvent.change(screen.getByLabelText("Days", { selector: "input[max='36500']" }), { target: { value: "90" } });
    fireEvent.click(screen.getByRole("button", { name: "Save policy" }));

    await waitFor(() => expect(createPolicy).toHaveBeenCalledWith("acme", expect.objectContaining({ retentionDays: 90 }), "t"));
  });

  it("only lets a Compliance policy get stricter", async () => {
    setUp([COMPLIANCE]);

    fireEvent.click(await screen.findByRole("button", { name: "Edit the policy on Archive" }));

    expect((screen.getByRole("button", { name: "1y" }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByLabelText(/^Governance/) as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByLabelText("Count from") as HTMLSelectElement).disabled).toBe(true);
    expect(screen.queryByLabelText("Type the tray name to confirm")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "10y" }));
    fireEvent.click(screen.getByRole("button", { name: "Save policy" }));

    await waitFor(() => expect(updatePolicy).toHaveBeenCalledWith("acme", "p1", expect.objectContaining({ retentionDays: 3650 }), "t"));
  });

  it("has no remove button on a Compliance policy", async () => {
    setUp([COMPLIANCE]);

    await screen.findByText("7 years");

    expect(screen.queryByRole("button", { name: "Remove the policy on Archive" })).toBeNull();
  });

  it("is read-only without retention.manage", async () => {
    setUp([COMPLIANCE], READ_ONLY);

    await screen.findByText("7 years");

    expect(screen.queryByRole("button", { name: "Add policy" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Edit the policy on Archive" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Save" })).toBeNull();
  });
});
