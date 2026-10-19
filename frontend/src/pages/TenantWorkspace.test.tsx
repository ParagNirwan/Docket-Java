import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Link, MemoryRouter, Route, Routes } from "react-router-dom";
import { TenantWorkspace } from "./TenantWorkspace";

// Matches the dynamic greeting heading regardless of time of day
const greetingPattern = /^Good (morning|afternoon|evening)$/;

vi.mock("../context/AuthContext", () => ({
  useAuth: () => ({ token: "test-token" }),
}));

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

function renderWorkspace(permissions: string[] | null) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/permissions")) {
        if (url.includes("/other/")) return response([]);
        return permissions === null ? response({}, 403) : response(permissions);
      }
      if (url.includes("/trays") && init?.method === "POST") {
        const body = JSON.parse(String(init.body || "{}"));
        return response({
          id: "tray-corporate-legal",
          name: body.name,
          description: body.description,
          isTray: true,
          isDefault: false,
          visibility: body.visibility || "ALL_MEMBERS",
          createdAt: new Date().toISOString(),
        });
      }
      if (url.endsWith("/trays")) {
        return response([
          { id: "general", name: "General", isTray: true, isDefault: true, visibility: "ALL_MEMBERS" },
          { id: "trash", name: "Trash", isTray: true, isDefault: true, visibility: "ADMIN_ONLY" },
        ]);
      }
      return response({ name: "Acme" });
    }),
  );

  render(
    <MemoryRouter initialEntries={["/workspace/acme"]}>
      <Link to="/workspace/other">Switch tenant</Link>
      <Routes>
        <Route path="/workspace/:slug" element={<TenantWorkspace />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("TenantWorkspace permissions", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("shows only sections granted by the active tenant permissions", async () => {
    renderWorkspace([
      "tenant.read",
      "documents.read",
      "members.read",
      "members.invite",
    ]);

    expect(await screen.findByRole("heading", { name: greetingPattern })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Review" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "General" })).toBeTruthy();
    const partnersNavigation = screen.getByRole("button", { name: "Partners" });
    expect(partnersNavigation).toBeTruthy();
    fireEvent.click(partnersNavigation);
    expect(await screen.findByRole("heading", { name: "Partners" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Add a partner" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Trash" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Connected Emails" })).toBeNull();
  });

  it("renders no workspace content when the server returns no permissions", async () => {
    renderWorkspace([]);

    expect(
      await screen.findByText("You don’t have permission to view any workspace sections."),
    ).toBeTruthy();
    expect(screen.queryByRole("heading", { name: greetingPattern })).toBeNull();
  });

  it("fails closed when the permissions endpoint rejects access", async () => {
    renderWorkspace(null);

    expect(await screen.findByRole("alert")).toBeTruthy();
    expect(screen.queryByRole("heading", { name: greetingPattern })).toBeNull();
  });

  it("clears the previous tenant's permissions immediately when switching workspaces", async () => {
    renderWorkspace(["tenant.read", "documents.read"]);

    expect(await screen.findByRole("heading", { name: greetingPattern })).toBeTruthy();
    fireEvent.click(screen.getByRole("link", { name: "Switch tenant" }));

    expect(screen.queryByRole("button", { name: "Review" })).toBeNull();
    expect(await screen.findByText("You don’t have permission to view any workspace sections.")).toBeTruthy();
  });

  it("shows tray + button only to users with trays.create permission and opens creation modal", async () => {
    // 1. Without trays.create
    renderWorkspace(["tenant.read", "documents.read"]);
    expect(await screen.findByRole("heading", { name: greetingPattern })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Create tray" })).toBeNull();

    cleanup();

    // 2. With trays.create
    renderWorkspace(["tenant.read", "documents.read", "trays.create", "folders.create"]);
    expect(await screen.findByRole("heading", { name: greetingPattern })).toBeTruthy();
    const createTrayButton = screen.getByRole("button", { name: "Create tray" });
    expect(createTrayButton).toBeTruthy();

    // Click + button
    fireEvent.click(createTrayButton);
    expect(screen.getByRole("heading", { name: "Create New Tray" })).toBeTruthy();

    // Fill in tray form
    fireEvent.change(screen.getByLabelText(/Tray Name/i), {
      target: { value: "Corporate Legal" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create Tray" }));

    // Verify new tray appears in sidebar and folder browser opens
    expect(await screen.findByRole("button", { name: "Corporate Legal" })).toBeTruthy();
    expect(await screen.findByRole("heading", { name: "Corporate Legal" })).toBeTruthy();
  });

  it("navigates into General tray and opens FolderBrowser", async () => {
    renderWorkspace(["tenant.read", "documents.read", "folders.create"]);
    expect(await screen.findByRole("heading", { name: greetingPattern })).toBeTruthy();

    const generalTrayButton = screen.getByRole("button", { name: "General" });
    fireEvent.click(generalTrayButton);

    expect(await screen.findByRole("heading", { name: "General" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "New Folder" })).toBeTruthy();
  });
});
