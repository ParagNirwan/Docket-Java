import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { EmailVerificationSettingsPanel } from "./EmailVerificationSettingsPanel";

const { getEmailVerificationSettings, updateEmailVerificationSettings } = vi.hoisted(() => ({
  getEmailVerificationSettings: vi.fn(),
  updateEmailVerificationSettings: vi.fn(),
}));

vi.mock("../services/emailVerificationService", () => ({
  getEmailVerificationSettings,
  updateEmailVerificationSettings,
}));

describe("EmailVerificationSettingsPanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("loads the default-off setting and enables reminders for workspace members", async () => {
    getEmailVerificationSettings.mockResolvedValue({ enabled: false });
    updateEmailVerificationSettings.mockResolvedValue({ enabled: true });

    render(
      <EmailVerificationSettingsPanel slug="acme" token="token" canManage />,
    );

    const checkbox = await screen.findByRole("checkbox");
    expect(checkbox).toHaveProperty("checked", false);
    fireEvent.click(checkbox);

    await waitFor(() => {
      expect(updateEmailVerificationSettings).toHaveBeenCalledWith(
        "acme",
        { enabled: true },
        "token",
      );
    });
    expect(checkbox).toHaveProperty("checked", true);
  });

  it("does not allow members to change the setting", async () => {
    getEmailVerificationSettings.mockResolvedValue({ enabled: false });

    render(
      <EmailVerificationSettingsPanel slug="acme" token="token" canManage={false} />,
    );

    expect(await screen.findByRole("checkbox")).toHaveProperty("disabled", true);
  });
});
