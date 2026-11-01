import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AcceptInvitePage } from "./AcceptInvitePage";

const auth = vi.hoisted(() => ({
  value: {} as Record<string, unknown>,
}));

vi.mock("../context/AuthContext", () => ({
  useAuth: () => auth.value,
}));

const preview = {
  tenantName: "Acme",
  tenantSlug: "acme",
  inviterName: "Ada",
  email: "bob@x.com",
  role: "MANAGER",
  hasAccount: true,
};

function ShowLocation({ label }: { label: string }) {
  const location = useLocation();
  return <p>{label} {location.pathname}{location.search} {JSON.stringify(location.state)}</p>;
}

function renderPage(url = "/invite?token=abc") {
  render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/invite" element={<AcceptInvitePage />} />
        <Route path="/login" element={<ShowLocation label="login" />} />
        <Route path="/register" element={<ShowLocation label="register" />} />
        <Route path="/workspace/:slug" element={<ShowLocation label="workspace" />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AcceptInvitePage", () => {
  const fetchMock = vi.fn();
  const logout = vi.fn();

  beforeEach(() => {
    logout.mockReset();
    auth.value = { user: null, token: null, isAuthenticated: false, isInitializing: false, logout };
    fetchMock.mockReset();
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === "POST"
        ? new Response(JSON.stringify({ tenantName: "Acme", tenantSlug: "acme" }))
        : new Response(JSON.stringify(preview)));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("says who invited them, to what and as which role", async () => {
    renderPage();

    const text = (await screen.findByText(/invited you/)).textContent;
    expect(text).toContain("Ada invited you");
    expect(text).toContain("Acme");
    expect(text).toContain("Manager");
  });

  it("sends someone with an account to sign in, and back here afterwards", async () => {
    renderPage();

    fireEvent.click(await screen.findByRole("link", { name: "Sign in to accept" }));

    const landed = await screen.findByText(/^login/);
    expect(landed.textContent).toContain('"redirectTo":"/invite?token=abc"');
    expect(landed.textContent).toContain('"email":"bob@x.com"');
  });

  it("sends someone without an account to register with the invite", async () => {
    fetchMock.mockImplementation(async () => new Response(JSON.stringify({ ...preview, hasAccount: false })));
    renderPage();

    fireEvent.click(await screen.findByRole("link", { name: "Create an account to accept" }));

    expect((await screen.findByText(/^register/)).textContent).toContain("/register?invite=abc");
  });

  it("joins and goes to the workspace when signed in as the invited address", async () => {
    auth.value = { ...auth.value, user: { email: "BOB@x.com", emailVerified: true }, token: "jwt", isAuthenticated: true };
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Join Acme" }));

    expect((await screen.findByText(/^workspace/)).textContent).toContain("/workspace/acme");
    const call = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
    expect(String(call?.[0])).toContain("/invitations/abc/accept");
  });

  it("won't let the wrong account accept, and offers to sign out", async () => {
    auth.value = { ...auth.value, user: { email: "mallory@x.com" }, token: "jwt", isAuthenticated: true };
    renderPage();

    expect((await screen.findByRole("alert")).textContent).toContain("signed in as mallory@x.com");
    expect(screen.queryByRole("button", { name: "Join Acme" })).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Sign out to continue" }));
    expect(logout).toHaveBeenCalled();
  });

  it("explains a dead link", async () => {
    fetchMock.mockImplementation(async () => new Response(null, { status: 404 }));
    renderPage();

    expect((await screen.findByRole("alert")).textContent).toContain("invalid or has expired");
  });

  it("explains a link with no token, without calling the server", async () => {
    renderPage("/invite");

    expect((await screen.findByRole("alert")).textContent).toContain("invalid or has expired");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
