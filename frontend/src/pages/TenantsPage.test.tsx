import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { TenantsPage } from "./TenantsPage";

vi.mock("../context/useAuth", () => ({
  useAuth: () => ({ token: "jwt", openSettingsModal: vi.fn() }),
}));

const pendingInvitation = {
  id: "invitation-1",
  tenantName: "Acme",
  tenantSlug: "acme",
  role: "MANAGER",
  invitedByName: "Ada",
  createdAt: "2026-10-20T10:00:00Z",
  expiresAt: "2026-10-27T10:00:00Z",
};

describe("TenantsPage invitations", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockImplementation(async (input: string, init?: RequestInit) => {
      if (String(input).includes("/users/invitations") && init?.method === "POST") {
        return new Response(JSON.stringify({ tenantName: "Acme", tenantSlug: "acme" }));
      }
      if (String(input).includes("/users/invitations")) {
        return new Response(JSON.stringify([pendingInvitation]));
      }
      return new Response(JSON.stringify([]));
    });
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("shows a pending invitation and joins the organization when accepted", async () => {
    render(
      <MemoryRouter initialEntries={["/tenants"]}>
        <Routes>
          <Route path="/tenants" element={<TenantsPage />} />
          <Route path="/workspace/:slug" element={<p>Workspace opened</p>} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText("Acme")).toBeTruthy();
    expect(screen.getByText("Ada invited you as Manager")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Accept" }));

    expect(await screen.findByText("Workspace opened")).toBeTruthy();
    expect(fetchMock.mock.calls.some(([url, init]) =>
      String(url).endsWith("/users/invitations/invitation-1/accept") && init?.method === "POST"
    )).toBe(true);
  });
});
