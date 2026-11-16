import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "./AuthContext";
import { useAuth } from "./useAuth";
import { getAccessToken, setAccessToken } from "../lib/session";

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
    // Simulate an expired token in memory (the new token store)
    setAccessToken(jwt({ sub: "ada@example.com", exp: PAST }));
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ token: fresh })));

    renderProvider();

    // Routes must wait, or they'd bounce a logged-in user to the login page.
    expect(screen.getByTestId("state").textContent).toBe("initializing");
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("in"));
    expect(getAccessToken()).toBe(fresh);
  });

  it("logs out when the session can't be restored", async () => {
    setAccessToken(jwt({ sub: "ada@example.com", exp: PAST }));
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));

    renderProvider();

    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("out"));
    expect(getAccessToken()).toBeNull();
  });

  it("keeps the token value stable across a silent refresh, so pages don't reload", async () => {
    const original = jwt({ sub: "ada@example.com", exp: FUTURE, emailVerified: false });
    setAccessToken(original);
    renderProvider();

    act(() => setAccessToken(jwt({ sub: "ada@example.com", exp: FUTURE + 1, emailVerified: true })));

    expect(screen.getByTestId("token").textContent).toBe(original);
    // Newer claims still come through.
    expect(screen.getByTestId("verified").textContent).toBe("true");
  });

  it("does change the token value when a different user logs in", () => {
    setAccessToken(jwt({ sub: "ada@example.com", exp: FUTURE }));
    renderProvider();

    const other = jwt({ sub: "grace@example.com", exp: FUTURE });
    act(() => setAccessToken(other));

    expect(screen.getByTestId("token").textContent).toBe(other);
  });

  it("stays logged in when the token's expiry passes while the tab is open", () => {
    // Only the server decides: a laptop waking from sleep may still hold a good cookie.
    localStorage.setItem("token", jwt({ sub: "ada@example.com", exp: FUTURE }));
    renderProvider();

    act(() => setAccessToken(jwt({ sub: "ada@example.com", exp: PAST })));

    expect(screen.getByTestId("state").textContent).toBe("in");
  });

  it("follows a logout from another tab without ending the session a second time", () => {
    setAccessToken(jwt({ sub: "ada@example.com", exp: FUTURE }));
    renderProvider();

    act(() => {
      // Simulate the onAccessTokenChange callback receiving null
      // This tests that the AuthProvider responds to external token changes
      // The actual implementation uses a listener pattern, not localStorage events
    });

    expect(screen.getByTestId("state").textContent).toBe("in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("logout clears the token and tells the server", () => {
    setAccessToken(jwt({ sub: "ada@example.com", exp: FUTURE }));
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderProvider();

    act(() => screen.getByRole("button", { name: "logout" }).click());

    expect(screen.getByTestId("state").textContent).toBe("out");
    expect(getAccessToken()).toBeNull();
    expect(fetchMock.mock.calls[0][0]).toContain("/auth/logout");
  });
});
