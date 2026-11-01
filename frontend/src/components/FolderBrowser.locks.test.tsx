import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { FolderBrowser } from "./FolderBrowser";
import type { Tray } from "../services/trayService";
import type { DocumentDTO } from "../services/documentService";

vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({ user: { email: "ada@example.com" }, token: "test-token" }),
}));

const documentsMock = vi.hoisted(() => ({ list: [] as unknown[] }));

vi.mock("../services/trayService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/trayService")>()),
  getFolderChildren: vi.fn(async () => ({ folders: [], files: [] })),
  getBreadcrumbs: vi.fn(async () => [{ id: "11111111-1111-1111-1111-111111111111", name: "Inbox", isTray: true }]),
}));

const deleteDocument = vi.hoisted(() => vi.fn(async () => undefined));
const updateDocument = vi.hoisted(() => vi.fn(async () => ({})));

vi.mock("../services/documentService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/documentService")>()),
  getDocuments: vi.fn(async () => ({
    content: documentsMock.list,
    totalElements: documentsMock.list.length,
    totalPages: 1,
    number: 0,
    size: 50,
    first: true,
    last: true,
  })),
  deleteDocument,
  updateDocument,
}));

const archiveDocument = vi.hoisted(() => vi.fn(async () => ({})));

vi.mock("../services/retentionService", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../services/retentionService")>()),
  archiveDocument,
  getArchivePreview: vi.fn(async () => ({
    folderId: "archive",
    folderName: "Archive",
    retainUntil: "2033-03-12T00:00:00Z",
    lockMode: "COMPLIANCE",
  })),
}));

const tray: Tray = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Inbox",
  description: "",
  isTray: true,
  isDefault: true,
  visibility: "ALL_MEMBERS",
  sortOrder: 1,
  itemCount: 1,
};

function doc(overrides: Partial<DocumentDTO>): DocumentDTO {
  return {
    id: "doc-1",
    originalFileName: "contract.pdf",
    contentType: "application/pdf",
    uploadedBy: "someone",
    fileSize: 100,
    createdAt: "2026-10-18T14:30:00Z",
    updatedAt: "2026-10-18T14:30:00Z",
    ...overrides,
  };
}

async function show(document: DocumentDTO, props: Partial<Parameters<typeof FolderBrowser>[0]> = {}) {
  documentsMock.list = [document];
  localStorage.setItem("docket_folder_view_mode", "cards");
  render(
    <FolderBrowser
      slug="acme"
      token="test-token"
      activeTray={tray}
      canCreateFolder
      canDeleteFolder
      canDeleteTray={false}
      canUpload
      canUpdateDocuments
      canCopyDocuments
      canDeleteDocuments
      {...props}
    />,
  );
  await screen.findByText("contract.pdf");
}

function openMenu() {
  fireEvent.click(screen.getByRole("button", { name: "More actions for contract.pdf" }));
}

describe("FolderBrowser with locked documents", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows the lock and when it ends", async () => {
    await show(doc({ retainUntil: "2031-03-12T00:00:00Z", lockMode: "COMPLIANCE" }));

    expect(screen.getByRole("img", { name: "Locked until 12 Mar 2031 (Compliance)" })).toBeTruthy();
  });

  it("disables delete and removes move and rename on a locked document", async () => {
    await show(doc({ retainUntil: "2031-03-12T00:00:00Z", lockMode: "COMPLIANCE" }));

    const remove = screen.getByRole("button", { name: "Delete contract.pdf" }) as HTMLButtonElement;
    expect(remove.disabled).toBe(true);
    expect(remove.title).toBe("Locked until 12 Mar 2031 (Compliance)");

    openMenu();
    expect(screen.queryByRole("menuitem", { name: "Rename" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "Move to..." })).toBeNull();
    expect(screen.getByRole("menuitem", { name: "Copy to..." })).toBeTruthy();
  });

  it("leaves everything available once a lock has passed", async () => {
    await show(doc({ retainUntil: "2001-03-12T00:00:00Z", lockMode: "COMPLIANCE" }));

    expect((screen.getByRole("button", { name: "Delete contract.pdf" }) as HTMLButtonElement).disabled).toBe(false);
    openMenu();
    expect(screen.getByRole("menuitem", { name: "Rename" })).toBeTruthy();
  });

  it("offers an Owner an override on Governance, and requires a reason", async () => {
    await show(doc({ retainUntil: "2031-03-12T00:00:00Z", lockMode: "GOVERNANCE" }), { canOverrideLocks: true });

    openMenu();
    fireEvent.click(screen.getByRole("menuitem", { name: "Delete anyway..." }));

    const submit = screen.getByRole("button", { name: "Override" }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Filed in error" } });
    fireEvent.click(submit);

    await waitFor(() => expect(deleteDocument).toHaveBeenCalledWith("acme", "doc-1", "test-token", { reason: "Filed in error" }));
  });

  it("never offers an override on Compliance or a legal hold", async () => {
    await show(doc({ retainUntil: "2031-03-12T00:00:00Z", lockMode: "COMPLIANCE" }), { canOverrideLocks: true });
    openMenu();
    expect(screen.queryByRole("menuitem", { name: "Delete anyway..." })).toBeNull();
    cleanup();

    await show(doc({ legalHold: true, retainUntil: "2031-03-12T00:00:00Z", lockMode: "GOVERNANCE" }), { canOverrideLocks: true });
    openMenu();
    expect(screen.queryByRole("menuitem", { name: "Delete anyway..." })).toBeNull();
  });

  it("doesn't offer an override to someone without the permission", async () => {
    await show(doc({ retainUntil: "2031-03-12T00:00:00Z", lockMode: "GOVERNANCE" }));

    openMenu();
    expect(screen.queryByRole("menuitem", { name: "Delete anyway..." })).toBeNull();
  });

  it("shows the resulting lock date before archiving", async () => {
    await show(doc({}));

    openMenu();
    fireEvent.click(screen.getByRole("menuitem", { name: "Archive..." }));

    expect(await screen.findByText(/locked until 12 Mar 2033 \(Compliance\)/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Move to Archive" }));

    await waitFor(() => expect(archiveDocument).toHaveBeenCalledWith("acme", "doc-1", "test-token"));
    await waitFor(() => expect(screen.queryByText("contract.pdf")).toBeNull());
  });

  it("offers Unarchive only once the lock is over", async () => {
    await show(doc({ archivedAt: "2020-01-01T00:00:00Z", retainUntil: "2031-03-12T00:00:00Z", lockMode: "GOVERNANCE" }));
    openMenu();
    expect(screen.queryByRole("menuitem", { name: "Unarchive" })).toBeNull();
    cleanup();

    await show(doc({ archivedAt: "2020-01-01T00:00:00Z", retainUntil: "2021-03-12T00:00:00Z", lockMode: "GOVERNANCE" }));
    openMenu();
    expect(screen.getByRole("menuitem", { name: "Unarchive" })).toBeTruthy();
    expect(screen.queryByRole("menuitem", { name: "Rename" })).toBeNull();
  });

  it("asks for the same confirmation when the folder picker's destination is the Archive", async () => {
    const archive = { ...tray, id: "22222222-2222-2222-2222-222222222222", name: "Archive", isArchive: true };
    await show(doc({}), { trays: [tray, archive] });

    openMenu();
    fireEvent.click(screen.getByRole("menuitem", { name: "Move to..." }));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByText("Archive"));
    fireEvent.click(within(dialog).getByRole("button", { name: /^Move/ }));

    expect(await screen.findByText(/locked until 12 Mar 2033 \(Compliance\)/)).toBeTruthy();
    expect(updateDocument).not.toHaveBeenCalled();
    expect(archiveDocument).not.toHaveBeenCalled();
  });

  it("lets a Manager delete folders without showing them a document delete they can't use", async () => {
    await show(doc({}), { canDeleteDocuments: false });

    expect(screen.queryByRole("button", { name: "Delete contract.pdf" })).toBeNull();
  });
});
