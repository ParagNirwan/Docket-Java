import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MembersPanel } from "./MembersPanel";

const member = (id: string, name: string, role: string, currentUser = false) => ({
  membershipId: id,
  name,
  email: `${name.toLowerCase()}@example.com`,
  role,
  status: "ACTIVE",
  joinedAt: "2026-10-01T10:00:00Z",
  lastActiveAt: null,
  currentUser,
});

const invitation = (id: string, email: string, role: string) => ({
  id,
  email,
  role,
  invitedByName: "Ada",
  createdAt: "2026-10-20T10:00:00Z",
  expiresAt: "2026-10-27T10:00:00Z",
});

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

describe("MembersPanel", () => {
  const fetchMock = vi.fn();
  let members = [member("1", "Ada", "OWNER", true), member("2", "Bob", "MEMBER")];
  let invitations = [invitation("i1", "new@example.com", "MANAGER")];

  beforeEach(() => {
    members = [member("1", "Ada", "OWNER", true), member("2", "Bob", "MEMBER")];
    invitations = [invitation("i1", "new@example.com", "MANAGER")];
    fetchMock.mockReset();
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method && init.method !== "GET") return new Response(null, { status: 204 });
      return String(url).endsWith("/members") ? json(members) : json(invitations);
    });
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  function renderPanel(permissions: string[]) {
    render(<MembersPanel slug="acme" token="token" permissions={new Set(permissions)} />);
    return screen.findByText("bob@example.com");
  }

  const OWNER_PERMISSIONS = ["members.read", "members.invite", "members.update_role", "members.remove"];

  it("lists members and pending invitations", async () => {
    await renderPanel(OWNER_PERMISSIONS);

    expect(screen.getByText("ada@example.com")).toBeTruthy();
    expect(screen.getByText("new@example.com")).toBeTruthy();
    expect(screen.getByText("(you)")).toBeTruthy();
  });

  it("filters by name or email", async () => {
    await renderPanel(OWNER_PERMISSIONS);

    fireEvent.change(screen.getByLabelText("Search members"), { target: { value: "bob" } });

    expect(screen.queryByText("ada@example.com")).toBeNull();
    expect(screen.getByText("bob@example.com")).toBeTruthy();
  });

  it("never offers controls for yourself or the Owner", async () => {
    await renderPanel(OWNER_PERMISSIONS);

    expect(screen.queryByLabelText("Role for Ada")).toBeNull();
    expect(screen.queryByRole("button", { name: "Remove Ada" })).toBeNull();
    expect(screen.getByLabelText("Role for Bob")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Remove Bob" })).toBeTruthy();
  });

  it("doesn't let an Admin manage another Admin", async () => {
    members = [member("1", "Ada", "ADMIN", true), member("2", "Bob", "ADMIN"), member("3", "Cleo", "MEMBER")];

    await renderPanel(OWNER_PERMISSIONS);

    expect(screen.queryByLabelText("Role for Bob")).toBeNull();
    expect(screen.queryByRole("button", { name: "Remove Bob" })).toBeNull();
    const options = within(screen.getByLabelText("Role for Cleo")).getAllByRole("option").map((o) => o.textContent);
    expect(options).toEqual(["Manager", "Member"]);
  });

  it("gives a Manager the invite button but no role or remove controls", async () => {
    members = [member("1", "Ada", "MANAGER", true), member("2", "Bob", "MEMBER")];

    await renderPanel(["members.read", "members.invite"]);

    expect(screen.getByRole("button", { name: "Invite people" })).toBeTruthy();
    expect(screen.queryByLabelText("Role for Bob")).toBeNull();
    expect(screen.queryByRole("button", { name: "Remove Bob" })).toBeNull();
    // The pending invite is for a Manager, which a Manager can't manage.
    expect(screen.queryByRole("button", { name: "Resend" })).toBeNull();
  });

  it("changes a role through the API", async () => {
    await renderPanel(OWNER_PERMISSIONS);

    fireEvent.change(screen.getByLabelText("Role for Bob"), { target: { value: "MANAGER" } });

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([, init]) => init?.method === "PATCH");
      expect(String(call?.[0])).toContain("/tenants/acme/members/2");
      expect(JSON.parse(String(call?.[1]?.body))).toEqual({ role: "MANAGER" });
    });
  });

  it("removes someone only after confirming", async () => {
    await renderPanel(OWNER_PERMISSIONS);

    fireEvent.click(screen.getByRole("button", { name: "Remove Bob" }));
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(false);

    fireEvent.click(screen.getByRole("button", { name: "Remove" }));

    await waitFor(() => expect(screen.queryByText("bob@example.com")).toBeNull());
    const call = fetchMock.mock.calls.find(([, init]) => init?.method === "DELETE");
    expect(String(call?.[0])).toContain("/members/2");
  });

  it("resends and revokes invitations", async () => {
    await renderPanel(OWNER_PERMISSIONS);

    fireEvent.click(screen.getByRole("button", { name: "Resend" }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith("/invitations/i1/resend"))).toBe(true));

    fireEvent.click(screen.getAllByRole("button", { name: "Revoke" })[0]);
    fireEvent.click(screen.getAllByRole("button", { name: "Revoke" })[1]);
    await waitFor(() => expect(screen.queryByText("new@example.com")).toBeNull());
  });

  it("says so when members can't be loaded", async () => {
    fetchMock.mockImplementation(async () => new Response(null, { status: 403 }));

    render(<MembersPanel slug="acme" token="token" permissions={new Set(OWNER_PERMISSIONS)} />);

    expect((await screen.findByRole("alert")).textContent).toContain("Could not load members");
  });
});
