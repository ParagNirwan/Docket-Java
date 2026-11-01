import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, getStoredToken, refreshAccessToken, retryAfterMessage, storeToken } from "./session";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

// A real-looking JWT with a far-future expiry.
function jwt(name: string): string {
  const payload = btoa(JSON.stringify({ sub: name, exp: 4102444800 }));
  return `header.${payload}.signature`;
}

function sentToken(call: unknown[]): string | null {
  return new Headers((call[1] as RequestInit).headers).get("Authorization");
}

describe("session", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("leaves requests without credentials alone", async () => {
    fetchMock.mockResolvedValue(jsonResponse({}, 401));

    const response = await apiFetch("/api/public");

    expect(response.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("sends the newest token even if the caller holds an older one", async () => {
    storeToken(jwt("new"));
    fetchMock.mockResolvedValue(jsonResponse({}));

    await apiFetch("/api/x", { headers: { Authorization: "Bearer old" } });

    expect(sentToken(fetchMock.mock.calls[0])).toBe(`Bearer ${jwt("new")}`);
  });

  it("refreshes on 401 and retries once with the new token", async () => {
    storeToken(jwt("stale"));
    fetchMock
      .mockResolvedValueOnce(jsonResponse({}, 401))
      .mockResolvedValueOnce(jsonResponse({ token: jwt("fresh") }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }));

    const response = await apiFetch("/api/x", { method: "POST", body: "data", headers: { Authorization: "Bearer a" } });

    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toContain("/auth/refresh");
    expect(sentToken(fetchMock.mock.calls[2])).toBe(`Bearer ${jwt("fresh")}`);
    expect(fetchMock.mock.calls[2][1].body).toBe("data");
    expect(getStoredToken()).toBe(jwt("fresh"));
  });

  it("does not retry a second time if the retry is also rejected", async () => {
    storeToken(jwt("stale"));
    fetchMock
      .mockResolvedValueOnce(jsonResponse({}, 401))
      .mockResolvedValueOnce(jsonResponse({ token: jwt("fresh") }))
      .mockResolvedValueOnce(jsonResponse({}, 401));

    const response = await apiFetch("/api/x", { headers: { Authorization: "Bearer a" } });

    expect(response.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("shares one refresh between simultaneous 401s, because refresh tokens are single-use", async () => {
    storeToken(jwt("stale"));
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (url.includes("/auth/refresh")) return jsonResponse({ token: jwt("fresh") });
      return new Headers(init.headers).get("Authorization") === `Bearer ${jwt("fresh")}`
        ? jsonResponse({ ok: true })
        : jsonResponse({}, 401);
    });

    const responses = await Promise.all([
      apiFetch("/api/a", { headers: { Authorization: "Bearer a" } }),
      apiFetch("/api/b", { headers: { Authorization: "Bearer a" } }),
    ]);

    expect(responses.map((r) => r.status)).toEqual([200, 200]);
    const refreshes = fetchMock.mock.calls.filter(([url]) => String(url).includes("/auth/refresh"));
    expect(refreshes).toHaveLength(1);
  });

  it("uses a token another tab already refreshed instead of spending the cookie again", async () => {
    storeToken(jwt("from-other-tab"));

    await expect(refreshAccessToken(jwt("stale"))).resolves.toBe(jwt("from-other-tab"));

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("logs out when the server says the session is over", async () => {
    storeToken(jwt("stale"));
    fetchMock.mockResolvedValue(jsonResponse({}, 401));

    await expect(refreshAccessToken()).resolves.toBeNull();

    expect(getStoredToken()).toBeNull();
  });

  it("stays logged in through network failures and server errors", async () => {
    storeToken(jwt("stale"));

    fetchMock.mockRejectedValueOnce(new TypeError("offline"));
    await expect(refreshAccessToken()).resolves.toBeNull();
    expect(getStoredToken()).toBe(jwt("stale"));

    fetchMock.mockResolvedValueOnce(jsonResponse({}, 503));
    await expect(refreshAccessToken()).resolves.toBeNull();
    expect(getStoredToken()).toBe(jwt("stale"));
  });

  it("describes how long to wait", () => {
    const wait = (seconds: string) => new Response(null, { status: 429, headers: { "Retry-After": seconds } });

    expect(retryAfterMessage(wait("30"))).toContain("1 minute.");
    expect(retryAfterMessage(wait("600"))).toContain("10 minutes.");
    expect(retryAfterMessage(new Response(null, { status: 429 }))).toContain("try again later");
  });
});
