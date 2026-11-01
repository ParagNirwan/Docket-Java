import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SearchPage } from "./SearchPage";
import { parseSnippet } from "../services/searchService";

vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({ token: "test-token" }),
}));

const result = {
  documentId: "d1",
  fileName: "notes.pdf",
  contentType: "application/pdf",
  fileSize: 2048,
  uploadedByName: "Ada",
  createdAt: "2026-10-01T10:00:00Z",
  folderId: "f1",
  folderName: "Contracts",
  trayId: "t1",
  trayName: "Inbox",
  snippet: "before \u0002invoice\u0003 <script>alert(1)</script> after",
  page: 3,
};

function json(body: unknown) {
  return new Response(JSON.stringify(body));
}

function ShowWorkspace() {
  return <p>workspace {JSON.stringify(useLocation().state)}</p>;
}

describe("parseSnippet", () => {
  it("separates matches from the text around them", () => {
    expect(parseSnippet("a \u0002b\u0003 c \u0002d\u0003")).toEqual([
      { text: "a ", match: false },
      { text: "b", match: true },
      { text: " c ", match: false },
      { text: "d", match: true },
    ]);
  });

  it("copes with no matches at all", () => {
    expect(parseSnippet("plain")).toEqual([{ text: "plain", match: false }]);
  });
});

describe("SearchPage", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockImplementation(async (url: string) =>
      String(url).endsWith("/trays")
        ? json([{ id: "t1", name: "Inbox" }, { id: "t2", name: "Trash" }])
        : json({ content: [result], number: 0, totalPages: 2, totalElements: 21 }));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  const searchCalls = () => fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.includes("/search?"));

  function renderPage(url = "/workspace/acme/search?q=invoice") {
    render(
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/workspace/:slug/search" element={<SearchPage />} />
          <Route path="/workspace/:slug" element={<ShowWorkspace />} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it("runs the search from the URL and shows where results live", async () => {
    renderPage();

    expect(await screen.findByText("notes.pdf")).toBeTruthy();
    expect(searchCalls()[0]).toContain("/tenants/acme/search?q=invoice");
    expect(screen.getByText(/Inbox \/ Contracts/)).toBeTruthy();
    expect(screen.getByText("21 documents")).toBeTruthy();
  });

  it("highlights matches and shows document text as text, never as HTML", async () => {
    const { container } = (renderPage(), { container: document.body });
    await screen.findByText("notes.pdf");

    expect(container.querySelector("mark")?.textContent).toBe("invoice");
    expect(container.querySelector("script")).toBeNull();
    expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeTruthy();
  });

  it("doesn't search for something too short", async () => {
    renderPage("/workspace/acme/search?q=a");

    expect(await screen.findByText(/at least 2 characters/)).toBeTruthy();
    expect(searchCalls()).toHaveLength(0);
  });

  it("applies a filter and starts again from the first page", async () => {
    renderPage("/workspace/acme/search?q=invoice&page=1");
    await screen.findByText("notes.pdf");

    fireEvent.change(screen.getByLabelText("Type"), { target: { value: "pdf" } });

    await waitFor(() => {
      const last = searchCalls().at(-1)!;
      expect(last).toContain("type=pdf");
      expect(last).toContain("page=0");
    });
  });

  it("doesn't offer Trash as a tray to filter by", async () => {
    renderPage();

    expect(await screen.findByRole("option", { name: "Inbox" })).toBeTruthy();
    expect(screen.queryByRole("option", { name: "Trash" })).toBeNull();
  });

  it("moves between pages", async () => {
    renderPage();
    await screen.findByText("notes.pdf");

    fireEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => expect(searchCalls().at(-1)).toContain("page=1"));
  });

  it("opens the folder a result is in", async () => {
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Open folder" }));

    const landed = await screen.findByText(/^workspace/);
    expect(landed.textContent).toContain('"openTrayId":"t1"');
    expect(landed.textContent).toContain('"openFolderId":"f1"');
  });

  it("says when nothing matches, and when the search fails", async () => {
    fetchMock.mockImplementation(async (url: string) =>
      String(url).endsWith("/trays") ? json([]) : json({ content: [], number: 0, totalPages: 0, totalElements: 0 }));
    renderPage();
    expect(await screen.findByText(/Nothing matches/)).toBeTruthy();

    cleanup();
    fetchMock.mockImplementation(async (url: string) =>
      String(url).endsWith("/trays") ? json([]) : new Response(null, { status: 500 }));
    renderPage();
    expect((await screen.findByRole("alert")).textContent).toContain("Could not run the search");
  });
});
