import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { StorageQuotaPanel } from "./StorageQuotaPanel";
import * as trayService from "../services/trayService";

vi.mock("../services/trayService", () => ({
  getTenantUsage: vi.fn(),
  updateTenantQuota: vi.fn(),
}));

describe("StorageQuotaPanel", () => {
  afterEach(cleanup);

  it("renders storage usage and quota", async () => {
    vi.mocked(trayService.getTenantUsage).mockResolvedValue({
      storageBytesUsed: 50 * 1024 * 1024,
      storageQuotaBytes: 100 * 1024 * 1024,
      documentCount: 12,
    });

    render(<StorageQuotaPanel slug="test-org" token="token-123" canManageQuota={true} />);

    await waitFor(() => {
      expect(screen.getByText("Storage & Quotas")).toBeTruthy();
      expect(screen.getByText("50.0% used")).toBeTruthy();
      expect(screen.getByText("12")).toBeTruthy();
    });
  });

  it("shows unlimited when quota is null", async () => {
    vi.mocked(trayService.getTenantUsage).mockResolvedValue({
      storageBytesUsed: 10 * 1024 * 1024,
      storageQuotaBytes: null,
      documentCount: 3,
    });

    render(<StorageQuotaPanel slug="test-org" token="token-123" canManageQuota={false} />);

    await waitFor(() => {
      expect(screen.getByText("(Unlimited quota)")).toBeTruthy();
    });
  });
});
