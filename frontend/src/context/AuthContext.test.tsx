import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "./AuthContext";
import { getStoredToken, storeToken } from "../lib/session";

function jwt(claims: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

const FUTURE = 4102444800;
const PAST = 1000000000;

function Probe() {
  const { token, user, isAuthenticated, isInitializing, logout } = useAuth();
  return (
    <div>
      <span data-testid="state">{isInitializing ? "initializing" : isAuthenticated ? "in" : "out"}</span>
      <span data-testid="token">{token}</span>
      <span data-testid="verified">{String(user?.emailVerified)}</span>
      <button onClick={logout}>logout</button>
    </div>
  );
}

function renderProvider() {
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
}

describe("AuthProvider", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    localStorage.clear();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("doesn't ask the server about a session when this browser has none", () => {
    renderProvider();

    expect(screen.getByTestId("state").textContent).toBe("out");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("restores a session from the cookie when the stored token has expired", async () => {
    const fresh = jwt({ sub: "ada@example.com", exp: FUTURE });
    localStorage.setItem("token", jwt({ sub: "ada@example.com", exp: PAST }));
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ token: fresh })));

    renderProvider();

    // Routes must wait, or they'd bounce a logged-in user to the login page.
    expect(screen.getByTestId("state").textContent).toBe("initializing");
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("in"));
    expect(getStoredToken()).toBe(fresh);
  });

  it("logs out when the session can't be restored", async () => {
    localStorage.setItem("token", jwt({ sub: "ada@example.com", exp: PAST }));
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));

    renderProvider();

    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("out"));
    expect(getStoredToken()).toBeNull();
  });

  it("keeps the token value stable across a silent refresh, so pages don't reload", async () => {
    const original = jwt({ sub: "ada@example.com", exp: FUTURE, emailVerified: false });
    localStorage.setItem("token", original);
    renderProvider();

    act(() => storeToken(jwt({ sub: "ada@example.com", exp: FUTURE + 1, emailVerified: true })));

    expect(screen.getByTestId("token").textContent).toBe(original);
    // Newer claims still come through.
    expect(screen.getByTestId("verified").textContent).toBe("true");
  });

  it("does change the token value when a different user logs in", () => {
    localStorage.setItem("token", jwt({ sub: "ada@example.com", exp: FUTURE }));
    renderProvider();

    const other = jwt({ sub: "grace@example.com", exp: FUTURE });
    act(() => storeToken(other));

    expect(screen.getByTestId("token").textContent).toBe(other);
  });

  it("stays logged in when the token's expiry passes while the tab is open", () => {
    // Only the server decides: a laptop waking from sleep may still hold a good cookie.
    localStorage.setItem("token", jwt({ sub: "ada@example.com", exp: FUTURE }));
    renderProvider();

    act(() => storeToken(jwt({ sub: "ada@example.com", exp: PAST })));

    expect(screen.getByTestId("state").textContent).toBe("in");
  });

  it("follows a logout from another tab without ending the session a second time", () => {
    localStorage.setItem("token", jwt({ sub: "ada@example.com", exp: FUTURE }));
    renderProvider();

    act(() => {
      window.dispatchEvent(new StorageEvent("storage", { key: "token", newValue: null }));
    });

    expect(screen.getByTestId("state").textContent).toBe("out");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("logout clears the token and tells the server", () => {
    localStorage.setItem("token", jwt({ sub: "ada@example.com", exp: FUTURE }));
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderProvider();

    act(() => screen.getByRole("button", { name: "logout" }).click());

    expect(screen.getByTestId("state").textContent).toBe("out");
    expect(getStoredToken()).toBeNull();
    expect(fetchMock.mock.calls[0][0]).toContain("/auth/logout");
  });
});
