import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DangerZone } from "./DangerZone";

const members = [
  { membershipId: "1", name: "Ada", email: "ada@x.com", role: "OWNER", currentUser: true },
  { membershipId: "2", name: "Bob", email: "bob@x.com", role: "ADMIN", currentUser: false },
];

describe("DangerZone", () => {
  const fetchMock = vi.fn();
  const onOwnershipTransferred = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    onOwnershipTransferred.mockReset();
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === "POST" ? new Response(null, { status: 204 }) : new Response(JSON.stringify(members)));
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  function renderZone(permissions: string[]) {
    render(
      <MemoryRouter initialEntries={["/workspace/acme"]}>
        <Routes>
          <Route
            path="/workspace/acme"
            element={(
              <DangerZone
                slug="acme"
                token="token"
                tenantName="Acme"
                permissions={new Set(permissions)}
                onOwnershipTransferred={onOwnershipTransferred}
              />
            )}
          />
          <Route path="/tenants" element={<p>All tenants</p>} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it("offers the Owner a transfer to anyone but themselves, and no way to leave", async () => {
    renderZone(["tenant.transfer_ownership"]);

    expect(await screen.findByRole("option", { name: "Bob (Admin)" })).toBeTruthy();
    expect(screen.queryByRole("option", { name: "Ada (Owner)" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Leave organization" })).toBeNull();
  });

  it("transfers with the password, then asks for permissions to reload", async () => {
    renderZone(["tenant.transfer_ownership"]);
    await screen.findByRole("option", { name: "Bob (Admin)" });

    fireEvent.change(screen.getByLabelText("New owner"), { target: { value: "2" } });
    fireEvent.change(screen.getByLabelText("Your password"), { target: { value: "secret123" } });
    fireEvent.click(screen.getByRole("button", { name: "Transfer ownership" }));

    await waitFor(() => expect(onOwnershipTransferred).toHaveBeenCalled());
    const call = fetchMock.mock.calls.find(([, init]) => init?.method === "POST");
    expect(String(call?.[0])).toContain("/tenants/acme/transfer-ownership");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ newOwnerMembershipId: "2", password: "secret123" });
  });

  it("says when the password is wrong, and stays put", async () => {
    fetchMock.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === "POST" ? new Response(null, { status: 403 }) : new Response(JSON.stringify(members)));
    renderZone(["tenant.transfer_ownership"]);
    await screen.findByRole("option", { name: "Bob (Admin)" });

    fireEvent.change(screen.getByLabelText("New owner"), { target: { value: "2" } });
    fireEvent.change(screen.getByLabelText("Your password"), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: "Transfer ownership" }));

    expect((await screen.findByRole("alert")).textContent).toContain("password isn't right");
    expect(onOwnershipTransferred).not.toHaveBeenCalled();
  });

  it("lets anyone else leave, after confirming", async () => {
    renderZone([]);

    fireEvent.click(screen.getByRole("button", { name: "Leave organization" }));
    fireEvent.click(screen.getByRole("button", { name: "Leave" }));

    expect(await screen.findByText("All tenants")).toBeTruthy();
    expect(String(fetchMock.mock.calls[0][0])).toContain("/tenants/acme/members/leave");
  });
});
