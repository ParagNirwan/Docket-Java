import { apiFetch } from "../lib/session";
import { API_BASE } from "./documentService";

export const EMAIL_VERIFICATION_SETTINGS_CHANGED_EVENT = "email-verification-settings-changed";

export interface EmailVerificationSettings {
  enabled: boolean;
}

export async function getEmailVerificationSettings(
  slug: string,
  token: string,
): Promise<EmailVerificationSettings> {
  const response = await apiFetch(
    `${API_BASE}/tenants/${encodeURIComponent(slug)}/email-verification-settings`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!response.ok) {
    throw new Error(`Failed to load email settings (${response.status})`);
  }
  return response.json();
}

export async function updateEmailVerificationSettings(
  slug: string,
  settings: EmailVerificationSettings,
  token: string,
): Promise<EmailVerificationSettings> {
  const response = await apiFetch(
    `${API_BASE}/tenants/${encodeURIComponent(slug)}/email-verification-settings`,
    {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(settings),
    },
  );
  if (!response.ok) {
    throw new Error(`Failed to update email settings (${response.status})`);
  }
  const updated: EmailVerificationSettings = await response.json();
  window.dispatchEvent(new Event(EMAIL_VERIFICATION_SETTINGS_CHANGED_EVENT));
  return updated;
}

export async function isEmailVerificationEnabled(token: string): Promise<boolean> {
  const response = await apiFetch(`${API_BASE}/auth/email-verification-enabled`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to load email verification status (${response.status})`);
  }
  const result: unknown = await response.json();
  if (
    !result ||
    typeof result !== "object" ||
    !("enabled" in result) ||
    typeof result.enabled !== "boolean"
  ) {
    throw new Error("Email verification status response was invalid");
  }
  return result.enabled;
}
