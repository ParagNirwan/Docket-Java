import { afterEach, describe, expect, it, vi } from "vitest";
import { completeSso } from "./ssoService";

describe("completeSso", () => {
  afterEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("refuses a callback whose state doesn't match the one this tab started", async () => {
    sessionStorage.setItem("docket.sso.state", "expected");
    sessionStorage.setItem("docket.sso.provider", "google");
    const fetchSpy = vi.spyOn(globalThis, "fetch");

    await expect(completeSso("code", "forged")).rejects.toThrow(/expired|another tab/);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("exchanges the code when the state matches, and clears it so it can't be replayed", async () => {
    sessionStorage.setItem("docket.sso.state", "s1");
    sessionStorage.setItem("docket.sso.provider", "microsoft");
    sessionStorage.setItem("docket.sso.redirect", "http://localhost/auth/sso/callback");
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ token: "jwt" }), { status: 200 }),
    );

    await expect(completeSso("code", "s1")).resolves.toBe("jwt");
    expect(String(fetchSpy.mock.calls[0][0])).toContain("/auth/sso/microsoft/callback");
    expect(sessionStorage.getItem("docket.sso.state")).toBeNull();
  });
});
