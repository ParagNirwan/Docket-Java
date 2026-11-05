import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TwoFactorSettings } from "./TwoFactorSettings";
import * as twoFactorService from "../services/twoFactorService";

vi.mock("../services/twoFactorService", () => ({
  get2faStatus: vi.fn(),
  setup2fa: vi.fn(),
  enable2fa: vi.fn(),
  disable2fa: vi.fn(),
}));

vi.mock("../lib/session", () => ({
  getStoredToken: () => "mock-jwt-token",
}));

describe("TwoFactorSettings", () => {
  afterEach(cleanup);

  it("renders disabled status when 2FA is not enabled", async () => {
    vi.mocked(twoFactorService.get2faStatus).mockResolvedValue({
      enabled: false,
    });

    render(<TwoFactorSettings />);

    await waitFor(() => {
      expect(screen.getByText("Two-Factor Authentication (2FA)")).toBeTruthy();
      expect(screen.getByText("Enable 2FA")).toBeTruthy();
    });
  });

  it("renders enabled status when 2FA is active", async () => {
    vi.mocked(twoFactorService.get2faStatus).mockResolvedValue({
      enabled: true,
    });

    render(<TwoFactorSettings />);

    await waitFor(() => {
      expect(screen.getByText("Active")).toBeTruthy();
      expect(screen.getByText("Disable 2FA")).toBeTruthy();
    });
  });
});
