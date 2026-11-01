import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { InviteMembersModal } from "./InviteMembersModal";

describe("InviteMembersModal", () => {
  const fetchMock = vi.fn();
  const onClose = vi.fn();
  const onInvited = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    onClose.mockReset();
    onInvited.mockReset();
    fetchMock.mockImplementation(async () => new Response(JSON.stringify({ id: "1" }), { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  function renderModal(roles = ["ADMIN", "MANAGER", "MEMBER"] as ("ADMIN" | "MANAGER" | "MEMBER")[]) {
    render(<InviteMembersModal slug="acme" token="token" roles={roles} onClose={onClose} onInvited={onInvited} />);
    return screen.getByLabelText("Email addresses");
  }

  it("only offers the roles it's given, defaulting to the lowest", () => {
    renderModal(["MANAGER", "MEMBER"]);

    const role = screen.getByLabelText("Role") as HTMLSelectElement;
    expect(within(role).getAllByRole("option").map((o) => o.textContent)).toEqual(["Manager", "Member"]);
    expect(role.value).toBe("MEMBER");
  });

  it("turns a pasted list into chips, without duplicates", () => {
    const input = renderModal();

    fireEvent.change(input, { target: { value: "a@x.com, b@x.com; A@x.com" } });
    fireEvent.keyDown(input, { key: "Enter" });

    expect(screen.getByRole("button", { name: "Remove a@x.com" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Remove b@x.com" })).toBeTruthy();
    expect(screen.getAllByRole("button", { name: /^Remove / })).toHaveLength(2);
  });

  it("keeps an invalid address in the box and doesn't send anything", async () => {
    const input = renderModal();

    fireEvent.change(input, { target: { value: "not-an-email" } });
    fireEvent.click(screen.getByRole("button", { name: "Send invitations" }));

    expect(await screen.findByText(/Check this address/)).toBeTruthy();
    expect((input as HTMLInputElement).value).toBe("not-an-email");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("invites each address with the chosen role and closes", async () => {
    const input = renderModal();

    fireEvent.change(input, { target: { value: "a@x.com b@x.com" } });
    fireEvent.change(screen.getByLabelText("Role"), { target: { value: "MANAGER" } });
    fireEvent.click(screen.getByRole("button", { name: "Send invitations" }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(onInvited).toHaveBeenCalled();
    const bodies = fetchMock.mock.calls.map(([, init]) => JSON.parse(String(init.body)));
    expect(bodies).toEqual([{ email: "a@x.com", role: "MANAGER" }, { email: "b@x.com", role: "MANAGER" }]);
  });

  it("keeps only the addresses that failed, and says why", async () => {
    fetchMock.mockImplementation(async (_url: string, init: RequestInit) =>
      JSON.parse(String(init.body)).email === "taken@x.com"
        ? new Response(null, { status: 409 })
        : new Response(JSON.stringify({ id: "1" }), { status: 201 }));
    const input = renderModal();

    fireEvent.change(input, { target: { value: "ok@x.com taken@x.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Send invitations" }));

    expect((await screen.findByRole("alert")).textContent).toContain("Already a member, or already invited");
    expect(screen.getByRole("button", { name: "Remove taken@x.com" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Remove ok@x.com" })).toBeNull();
    expect(onInvited).toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });
});
