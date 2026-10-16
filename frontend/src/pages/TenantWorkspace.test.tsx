import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Link, MemoryRouter, Route, Routes } from "react-router-dom";
import { TenantWorkspace } from "./TenantWorkspace";

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
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/permissions")) {
        if (url.includes("/other/")) return response([]);
        return permissions === null ? response({}, 403) : response(permissions);
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

    expect(await screen.findByRole("heading", { name: "Good afternoon" })).toBeTruthy();
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
    expect(screen.queryByRole("heading", { name: "Good afternoon" })).toBeNull();
  });

  it("fails closed when the permissions endpoint rejects access", async () => {
    renderWorkspace(null);

    expect(await screen.findByRole("alert")).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "Good afternoon" })).toBeNull();
  });

  it("clears the previous tenant's permissions immediately when switching workspaces", async () => {
    renderWorkspace(["tenant.read", "documents.read"]);

    expect(await screen.findByRole("heading", { name: "Good afternoon" })).toBeTruthy();
    fireEvent.click(screen.getByRole("link", { name: "Switch tenant" }));

    expect(screen.queryByRole("button", { name: "Review" })).toBeNull();
    expect(await screen.findByText("You don’t have permission to view any workspace sections.")).toBeTruthy();
  });
});
