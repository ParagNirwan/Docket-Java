import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EmailVerificationBanner } from "./EmailVerificationBanner";

const { useAuth, isEmailVerificationEnabled } = vi.hoisted(() => ({
  useAuth: vi.fn(),
  isEmailVerificationEnabled: vi.fn(),
}));

vi.mock("../context/useAuth", () => ({ useAuth }));
vi.mock("../services/emailVerificationService", () => ({
  EMAIL_VERIFICATION_SETTINGS_CHANGED_EVENT: "email-verification-settings-changed",
  isEmailVerificationEnabled,
}));
vi.mock("../lib/session", () => ({ apiFetch: vi.fn() }));
vi.mock("../lib/notifications", () => ({ notify: { success: vi.fn(), error: vi.fn() } }));

describe("EmailVerificationBanner", () => {
  beforeEach(() => {
    useAuth.mockReturnValue({
      isAuthenticated: true,
      user: { emailVerified: false },
      token: "token",
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("hides the reminder when verification emails are disabled", async () => {
    isEmailVerificationEnabled.mockResolvedValue(false);

    render(<EmailVerificationBanner />);

    await waitFor(() => expect(isEmailVerificationEnabled).toHaveBeenCalledWith("token"));
    expect(screen.queryByText("Please verify your email address.")).toBeNull();
  });

  it("shows the reminder when a workspace enables verification emails", async () => {
    isEmailVerificationEnabled.mockResolvedValue(true);

    render(<EmailVerificationBanner />);

    expect(await screen.findByText("Please verify your email address.")).toBeTruthy();
  });
});
