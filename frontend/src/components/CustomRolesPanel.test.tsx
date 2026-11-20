import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CustomRolesPanel } from "./CustomRolesPanel";

const api = vi.hoisted(() => ({
  listCustomRoles: vi.fn(),
  createCustomRole: vi.fn(),
  updateCustomRole: vi.fn(),
  deleteCustomRole: vi.fn(),
}));

vi.mock("../services/roleService", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/roleService")>();
  return { ...actual, ...api };
});

describe("CustomRolesPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("only offers permissions the editor holds", async () => {
    api.listCustomRoles.mockResolvedValue({
      roles: [{ id: "r1", name: "Records clerk", description: null, permissions: ["retention.manage"], memberCount: 2 }],
      grantablePermissions: ["retention.manage", "api.manage", "audit.read"],
    });

    render(<CustomRolesPanel slug="acme" token="t" myPermissions={new Set(["retention.manage", "audit.read"])} />);

    expect(await screen.findByText(/Records clerk/)).toBeTruthy();
    expect(screen.getByLabelText("Retention: manage")).toBeTruthy();
    expect(screen.getByLabelText("Audit: read")).toBeTruthy();
    expect(screen.queryByLabelText("Api: manage")).toBeNull();
  });
});
