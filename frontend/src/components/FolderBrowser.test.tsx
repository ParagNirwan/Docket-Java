import { render, screen, fireEvent, cleanup, waitFor, within } from "@testing-library/react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { FolderBrowser } from "./FolderBrowser";
import type { Tray } from "../services/trayService";

// Mock AuthContext
vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({
    user: { email: "sarah.connor@example.com" },
    token: "test-token",
  }),
}));

// Mock trayService
vi.mock("../services/trayService", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/trayService")>();
  return {
    ...actual,
    getFolderChildren: vi.fn(async () => ({
      folders: [
        {
          id: "folder-1",
          name: "Contracts & Agreements",
          description: "Active vendor contracts",
          isTray: false,
          isDefault: false,
          visibility: "ALL_MEMBERS",
          itemCount: 3,
          createdAt: "2026-10-15T10:00:00Z",
        },
      ],
      files: [
        {
          id: "file-1",
          name: "Board resolution — Q3 approval.pdf",
          detail: "Corporate governance · PDF",
          size: "1.4 MB",
          date: "Today",
          folderId: "general",
        },
      ],
    })),
    getBreadcrumbs: vi.fn(async () => [
      { id: "general", name: "General", isTray: true },
    ]),
  };
});

// Mock documentService
vi.mock("../services/documentService", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/documentService")>();
  return {
    ...actual,
    getDocuments: vi.fn(async () => ({
      content: [
        {
          id: "doc-1",
          originalFileName: "Financial_Report_2026.xlsx",
          contentType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          uploadedBy: "b8a53df4-6e82-41a4-bb50-2bcfe4df5481",
          uploadedByName: "Sarah Connor",
          fileSize: 2048000,
          createdAt: "2026-10-18T14:30:00Z",
          updatedAt: "2026-10-18T14:30:00Z",
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
      first: true,
      last: true,
    })),
    downloadDocument: vi.fn(),
    deleteDocument: vi.fn(),
    updateDocument: vi.fn(),
    copyDocument: vi.fn(),
  };
});

const mockTray: Tray = {
  id: "general",
  name: "General",
  description: "General documents and records",
  isTray: true,
  isDefault: true,
  visibility: "ALL_MEMBERS",
  sortOrder: 1,
  itemCount: 2,
};

describe("FolderBrowser - View Mode Switcher (List, Thumbnail, Cards)", () => {
  afterEach(() => {
    cleanup();
    if (typeof localStorage?.clear === "function") {
      localStorage.clear();
    }
  });

  it("renders with view mode switcher buttons for List, Thumbnail, and Cards", async () => {
    render(
      <FolderBrowser
        slug="test-tenant"
        token="test-token"
        activeTray={mockTray}
        canCreateFolder={true}
        canDeleteFolder={true}
        canDeleteTray={false}
        canUpload={true}
      />
    );

    // Verify view mode switcher buttons exist
    expect(screen.getByRole("button", { name: /list view/i })).toBeTruthy();
    expect(screen.getByRole("button", { name: /thumbnail view/i })).toBeTruthy();
    expect(screen.getByRole("button", { name: /cards view/i })).toBeTruthy();
  });

  it("renders Cards view with folder and document card details", async () => {
    render(
      <FolderBrowser
        slug="test-tenant"
        token="test-token"
        activeTray={mockTray}
        canCreateFolder={true}
        canDeleteFolder={true}
        canDeleteTray={false}
        canUpload={true}
      />
    );

    // Switch to Cards view
    const cardsBtn = screen.getByRole("button", { name: /cards view/i });
    fireEvent.click(cardsBtn);

    // Wait for contents to load
    expect(await screen.findByText("Contracts & Agreements")).toBeTruthy();
    expect(screen.getByText("Active vendor contracts")).toBeTruthy();
    expect(screen.getByText("3 items")).toBeTruthy();

    // Verify document details
    expect(await screen.findByText("Board resolution — Q3 approval.pdf")).toBeTruthy();
    expect(screen.getByText(/PDF Document/i)).toBeTruthy();
  });

  it("switches to List view and renders table headers and columns", async () => {
    render(
      <FolderBrowser
        slug="test-tenant"
        token="test-token"
        activeTray={mockTray}
        canCreateFolder={true}
        canDeleteFolder={true}
        canDeleteTray={false}
        canUpload={true}
      />
    );

    // Switch to List view
    const listBtn = screen.getByRole("button", { name: /list view/i });
    fireEvent.click(listBtn);

    // In list view, table headers should appear
    expect(await screen.findByText("Contracts & Agreements")).toBeTruthy();
    const nameHeaders = screen.getAllByText("Name");
    expect(nameHeaders.length).toBeGreaterThanOrEqual(1);

    expect(screen.getAllByText("Size").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Actions").length).toBeGreaterThanOrEqual(1);
  });

  it("switches to Thumbnail view and renders thumbnail preview elements", async () => {
    render(
      <FolderBrowser
        slug="test-tenant"
        token="test-token"
        activeTray={mockTray}
        canCreateFolder={true}
        canDeleteFolder={true}
        canDeleteTray={false}
        canUpload={true}
      />
    );

    // Switch to Thumbnail view
    const thumbBtn = screen.getByRole("button", { name: /thumbnail view/i });
    fireEvent.click(thumbBtn);

    expect(await screen.findByText("Contracts & Agreements")).toBeTruthy();
    expect(await screen.findByText("Board resolution — Q3 approval.pdf")).toBeTruthy();
  });

  it("renders pagination footer with 50-200 files to load options when files are displayed", async () => {
    render(
      <FolderBrowser
        slug="test-tenant"
        token="test-token"
        activeTray={mockTray}
        canCreateFolder={true}
        canDeleteFolder={true}
        canDeleteTray={false}
        canUpload={true}
      />
    );

    // Wait for documents to load
    expect(await screen.findByText("Board resolution — Q3 approval.pdf")).toBeTruthy();

    // Verify pagination footer
    expect(screen.getByText(/files to load:/i)).toBeTruthy();
    const select = screen.getByRole("combobox", { name: /how many files to load/i }) as HTMLSelectElement;
    expect(select).toBeTruthy();
    expect(select.value).toBe("50");

    // Verify options are 50, 100, 150, 200
    const options = Array.from(select.options).map((opt) => opt.value);
    expect(options).toEqual(["50", "100", "150", "200"]);

    // Verify showing text and pagination buttons
    expect(screen.getByText(/showing/i)).toBeTruthy();
    expect(screen.getByRole("button", { name: /previous page/i })).toBeTruthy();
    expect(screen.getByRole("button", { name: /next page/i })).toBeTruthy();

    // Changing files to load updates value
    fireEvent.change(select, { target: { value: "100" } });
    expect(select.value).toBe("100");
  });

  it("displays the user's name instead of raw UUID for uploaded by", async () => {
    const uuidTray: Tray = {
      ...mockTray,
      id: "e9f73117-a068-45ec-b1ea-2d7c7144e548",
    };

    render(
      <FolderBrowser
        slug="test-tenant"
        token="test-token"
        activeTray={uuidTray}
        canCreateFolder={true}
        canDeleteFolder={true}
        canDeleteTray={false}
        canUpload={true}
      />
    );

    // Switch to Cards view
    const cardsBtn = screen.getByRole("button", { name: /cards view/i });
    fireEvent.click(cardsBtn);

    // Verify Sarah Connor is displayed in Cards view and raw UUID is not
    expect(await screen.findByText("Sarah Connor")).toBeTruthy();
    expect(screen.queryByText("b8a53df4-6e82-41a4-bb50-2bcfe4df5481")).toBeNull();

    // Switch to List view
    const listBtn = screen.getByRole("button", { name: /list view/i });
    fireEvent.click(listBtn);

    // Verify Sarah Connor is displayed in List view as well
    expect(await screen.findByText("Sarah Connor")).toBeTruthy();
    expect(screen.queryByText("b8a53df4-6e82-41a4-bb50-2bcfe4df5481")).toBeNull();
  });

  it("never shows raw UUIDs even when uploadedByName is not provided", async () => {
    const documentService = await import("../services/documentService");
    vi.mocked(documentService.getDocuments).mockImplementationOnce(async () => ({
      content: [
        {
          id: "doc-uuid-only",
          originalFileName: "Legacy_Doc.pdf",
          contentType: "application/pdf",
          uploadedBy: "f310f545-09c3-488b-b83c-1b7774e1d7d0",
          fileSize: 1024,
          createdAt: "2026-10-18T14:30:00Z",
          updatedAt: "2026-10-18T14:30:00Z",
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 50,
      first: true,
      last: true,
    }));

    const uuidTray: Tray = {
      ...mockTray,
      id: "e9f73117-a068-45ec-b1ea-2d7c7144e548",
    };

    render(
      <FolderBrowser
        slug="test-tenant"
        token="test-token"
        activeTray={uuidTray}
        canCreateFolder={true}
        canDeleteFolder={true}
        canDeleteTray={false}
        canUpload={true}
      />
    );

    // Switch to Cards view
    const cardsBtn = screen.getByRole("button", { name: /cards view/i });
    fireEvent.click(cardsBtn);

    expect(await screen.findByText("Legacy_Doc.pdf")).toBeTruthy();
    // Raw UUID must NEVER be rendered
    expect(screen.queryByText("f310f545-09c3-488b-b83c-1b7774e1d7d0")).toBeNull();
    // Should display human-friendly name from currentUser or Member
    expect(screen.getByText("Sarah.connor")).toBeTruthy();
  });
});

describe("FolderBrowser - rename, move and copy", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  const uuidTray: Tray = { ...mockTray, id: "e9f73117-a068-45ec-b1ea-2d7c7144e548" };

  function renderBrowser(overrides: Partial<React.ComponentProps<typeof FolderBrowser>> = {}) {
    render(
      <FolderBrowser
        slug="test-tenant"
        token="test-token"
        activeTray={uuidTray}
        canCreateFolder
        canDeleteFolder={false}
        canDeleteTray={false}
        canUpload={false}
        canUpdateDocuments
        canCopyDocuments
        canUpdateFolders
        trays={[uuidTray]}
        {...overrides}
      />
    );
  }

  const openMenu = async (name: string) =>
    fireEvent.click(await screen.findByRole("button", { name: `More actions for ${name}` }));

  it("hides the actions from people who can't use them", async () => {
    renderBrowser({ canUpdateDocuments: false, canCopyDocuments: false, canUpdateFolders: false });

    expect(await screen.findByText("Financial_Report_2026.xlsx")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /More actions/ })).toBeNull();
  });

  it("renames a document while keeping its extension out of reach", async () => {
    const documentService = await import("../services/documentService");
    vi.mocked(documentService.updateDocument).mockResolvedValueOnce({ originalFileName: "Summary.xlsx" } as never);
    renderBrowser();

    await openMenu("Financial_Report_2026.xlsx");
    fireEvent.click(screen.getByRole("menuitem", { name: "Rename" }));

    const input = screen.getByLabelText("New name") as HTMLInputElement;
    expect(input.value).toBe("Financial_Report_2026");
    fireEvent.change(input, { target: { value: "Summary" } });
    fireEvent.click(screen.getByRole("button", { name: "Rename" }));

    expect(await screen.findByText("Summary.xlsx")).toBeTruthy();
    expect(documentService.updateDocument).toHaveBeenCalledWith("test-tenant", "doc-1", { name: "Summary.xlsx" }, "test-token");
  });

  it("shows why a rename was refused and keeps the dialog open", async () => {
    const documentService = await import("../services/documentService");
    vi.mocked(documentService.updateDocument).mockRejectedValueOnce(new Error("A document with this name already exists in the folder"));
    renderBrowser();

    await openMenu("Financial_Report_2026.xlsx");
    fireEvent.click(screen.getByRole("menuitem", { name: "Rename" }));
    fireEvent.change(screen.getByLabelText("New name"), { target: { value: "Taken" } });
    fireEvent.click(screen.getByRole("button", { name: "Rename" }));

    expect((await screen.findByRole("alert")).textContent).toContain("already exists");
  });

  it("moves a document with the picker, and it leaves the view", async () => {
    const documentService = await import("../services/documentService");
    vi.mocked(documentService.updateDocument).mockResolvedValueOnce({} as never);
    renderBrowser();

    await openMenu("Financial_Report_2026.xlsx");
    fireEvent.click(screen.getByRole("menuitem", { name: "Move to..." }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "General", pressed: false }));
    fireEvent.click(screen.getByRole("button", { name: "Move to General" }));

    await waitFor(() => expect(screen.queryByText("Financial_Report_2026.xlsx")).toBeNull());
    expect(documentService.updateDocument).toHaveBeenCalledWith("test-tenant", "doc-1", { folderId: uuidTray.id }, "test-token");
  });

  it("copies a document into this folder, and it appears", async () => {
    const documentService = await import("../services/documentService");
    vi.mocked(documentService.copyDocument).mockResolvedValueOnce({
      id: "doc-2",
      originalFileName: "Financial_Report_2026 (1).xlsx",
      contentType: "application/vnd.ms-excel",
      uploadedBy: "someone",
      uploadedByName: "Sarah Connor",
      fileSize: 10,
      createdAt: "2026-10-18T14:30:00Z",
      updatedAt: "2026-10-18T14:30:00Z",
    });
    renderBrowser();

    await openMenu("Financial_Report_2026.xlsx");
    fireEvent.click(screen.getByRole("menuitem", { name: "Copy to..." }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "General", pressed: false }));
    fireEvent.click(screen.getByRole("button", { name: "Copy to General" }));

    expect(await screen.findByText("Financial_Report_2026 (1).xlsx")).toBeTruthy();
  });

  it("doesn't offer a folder as its own destination", async () => {
    const trayService = await import("../services/trayService");
    renderBrowser();

    await openMenu("Contracts & Agreements");
    fireEvent.click(screen.getByRole("menuitem", { name: "Move to..." }));
    const dialog = screen.getByRole("dialog");
    vi.mocked(trayService.getFolderChildren).mockResolvedValueOnce({
      folders: [
        { id: "folder-1", name: "Contracts & Agreements", parentFolderId: uuidTray.id, isTray: false, isDefault: false, visibility: "ALL_MEMBERS" },
        { id: "folder-2", name: "Other Folder", parentFolderId: uuidTray.id, isTray: false, isDefault: false, visibility: "ALL_MEMBERS" },
      ],
      files: [],
    });
    fireEvent.click(within(dialog).getByRole("button", { name: "Expand General" }));

    expect(await within(dialog).findByText("Other Folder")).toBeTruthy();
    expect(within(dialog).queryByRole("button", { name: "Contracts & Agreements" })).toBeNull();
  });

  // A stand-in for the browser's drag data, which jsdom doesn't provide.
  function dataTransfer() {
    const store: Record<string, string> = {};
    return {
      get types() { return Object.keys(store); },
      files: [] as unknown as FileList,
      effectAllowed: "",
      setData: (type: string, value: string) => { store[type] = value; },
      getData: (type: string) => store[type] ?? "",
    };
  }

  it("moves a document by dragging it onto a folder", async () => {
    const documentService = await import("../services/documentService");
    vi.mocked(documentService.updateDocument).mockResolvedValueOnce({} as never);
    renderBrowser();
    fireEvent.click(screen.getByRole("button", { name: /cards view/i }));

    const docCard = (await screen.findByText("Financial_Report_2026.xlsx")).closest("[draggable]")!;
    const folderCard = screen.getByText("Contracts & Agreements").closest("[draggable]")!;
    const transfer = dataTransfer();
    fireEvent.dragStart(docCard, { dataTransfer: transfer });
    fireEvent.dragOver(folderCard, { dataTransfer: transfer });
    expect(folderCard.className).toContain("ring-indigo-400");
    fireEvent.drop(folderCard, { dataTransfer: transfer });

    await waitFor(() => expect(documentService.updateDocument).toHaveBeenCalledWith(
      "test-tenant", "doc-1", { folderId: "folder-1" }, "test-token"));
    await waitFor(() => expect(screen.queryByText("Financial_Report_2026.xlsx")).toBeNull());
  });

  it("ignores a folder dropped on itself", async () => {
    const documentService = await import("../services/documentService");
    renderBrowser();
    fireEvent.click(screen.getByRole("button", { name: /cards view/i }));

    const folderCard = (await screen.findByText("Contracts & Agreements")).closest("[draggable]")!;
    const transfer = dataTransfer();
    fireEvent.dragStart(folderCard, { dataTransfer: transfer });
    fireEvent.drop(folderCard, { dataTransfer: transfer });

    expect(documentService.updateDocument).not.toHaveBeenCalled();
    expect(screen.getByText("Contracts & Agreements")).toBeTruthy();
  });

  it("opens straight into a folder when told to", async () => {
    const trayService = await import("../services/trayService");
    renderBrowser({ initialFolderId: "b8a53df4-6e82-41a4-bb50-2bcfe4df5481" });

    await screen.findByText("Financial_Report_2026.xlsx");
    expect(trayService.getFolderChildren).toHaveBeenCalledWith("test-tenant", "b8a53df4-6e82-41a4-bb50-2bcfe4df5481", "test-token");
  });
});

