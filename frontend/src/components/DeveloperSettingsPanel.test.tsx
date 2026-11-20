import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DeveloperSettingsPanel } from "./DeveloperSettingsPanel";

const api = vi.hoisted(() => ({
  listApiKeys: vi.fn(),
  createApiKey: vi.fn(),
  revokeApiKey: vi.fn(),
  listServiceAccounts: vi.fn(),
  createServiceAccount: vi.fn(),
  revokeServiceAccount: vi.fn(),
  listWebhooks: vi.fn(),
  createWebhook: vi.fn(),
  setWebhookActive: vi.fn(),
  deleteWebhook: vi.fn(),
}));

vi.mock("../services/developerService", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/developerService")>();
  return { ...actual, ...api };
});

describe("DeveloperSettingsPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  function setup() {
    api.listApiKeys.mockResolvedValue([]);
    api.listServiceAccounts.mockResolvedValue([]);
    api.listWebhooks.mockResolvedValue([]);
    render(<DeveloperSettingsPanel slug="acme" token="t" />);
  }

  it("shows a new API key once, with the scopes that were picked", async () => {
    setup();
    api.createApiKey.mockResolvedValue({
      key: { id: "k1", name: "Export", prefix: "dkt_live_ab", scopes: ["documents:read"], createdAt: new Date().toISOString(), lastUsedAt: null, expiresAt: null, revokedAt: null },
      value: "dkt_live_secret",
    });

    fireEvent.change(await screen.findByLabelText("API key name"), { target: { value: "Export" } });
    fireEvent.click(screen.getByRole("button", { name: "Create API key" }));

    await waitFor(() => expect(screen.getByText("dkt_live_secret")).toBeTruthy());
    expect(api.createApiKey).toHaveBeenCalledWith("acme", "Export", ["documents:read"], "t");

    fireEvent.click(screen.getByRole("button", { name: "Done" }));
    expect(screen.queryByText("dkt_live_secret")).toBeNull();
  });

  it("shows the server's reason when a webhook is rejected", async () => {
    setup();
    api.createWebhook.mockRejectedValue(new Error("Webhook URLs must use https"));

    fireEvent.change(await screen.findByLabelText("Webhook URL"), { target: { value: "http://internal" } });
    fireEvent.click(screen.getByRole("button", { name: "Add webhook" }));

    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("must use https"));
  });

  it("flags credentials that haven't been used for a long time", async () => {
    api.listApiKeys.mockResolvedValue([
      { id: "old", name: "Legacy", prefix: "dkt_live_zz", scopes: ["documents:read"], createdAt: "2020-01-01T00:00:00Z", lastUsedAt: null, expiresAt: null, revokedAt: null },
    ]);
    api.listServiceAccounts.mockResolvedValue([]);
    api.listWebhooks.mockResolvedValue([]);
    render(<DeveloperSettingsPanel slug="acme" token="t" />);

    expect(await screen.findByText("Rotate?")).toBeTruthy();
  });
});
