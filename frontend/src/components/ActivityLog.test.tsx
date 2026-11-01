import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ActivityLog } from "./ActivityLog";

const page = {
  content: [
    {
      id: "1",
      actorName: "Ada Lovelace",
      actorEmail: "ada@example.com",
      actorType: "USER",
      action: "DOCUMENT_DOWNLOADED",
      targetType: "DOCUMENT",
      targetId: "doc-1",
      metadata: { fileName: "contract.pdf" },
      ip: "203.0.113.7",
      createdAt: "2026-10-01T10:00:00Z",
    },
  ],
  number: 0,
  totalPages: 2,
  totalElements: 51,
};

describe("ActivityLog", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockImplementation(async () => new Response(JSON.stringify(page)));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("shows who did what, to what, and from where", async () => {
    render(<ActivityLog slug="acme" token="token" />);

    expect(await screen.findByText("Ada Lovelace")).toBeTruthy();
    expect(screen.getByRole("cell", { name: "Document downloaded" })).toBeTruthy();
    expect(screen.getByText("contract.pdf")).toBeTruthy();
    expect(screen.getByText("203.0.113.7")).toBeTruthy();
  });

  it("sends filters only once they're applied", async () => {
    render(<ActivityLog slug="acme" token="token" />);
    await screen.findByText("Ada Lovelace");

    fireEvent.change(screen.getByLabelText("Action"), { target: { value: "DOCUMENT_DELETED" } });
    fireEvent.change(screen.getByLabelText("Person (email)"), { target: { value: "ada@example.com" } });
    expect(fetchMock).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "Apply" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const url = String(fetchMock.mock.calls[1][0]);
    expect(url).toContain("/tenants/acme/audit-events?");
    expect(url).toContain("action=DOCUMENT_DELETED");
    expect(url).toContain("actor=ada%40example.com");
  });

  it("moves between pages", async () => {
    render(<ActivityLog slug="acme" token="token" />);
    await screen.findByText("Ada Lovelace");

    expect((screen.getByRole("button", { name: "Previous" }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Next" }));

    await waitFor(() => expect(String(fetchMock.mock.calls[1][0])).toContain("page=1"));
  });

  it("says so when the log can't be loaded", async () => {
    fetchMock.mockImplementation(async () => new Response(null, { status: 403 }));

    render(<ActivityLog slug="acme" token="token" />);

    expect((await screen.findByRole("alert")).textContent).toContain("Could not load");
  });
});
