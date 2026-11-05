import { apiFetch } from "../lib/session";
import { API_BASE } from "./documentService";

export interface TwoFactorSetupDTO {
  secret: string;
  qrUri: string;
}

export interface TwoFactorEnableResponseDTO {
  backupCodes: string[];
}

export interface TwoFactorStatusDTO {
  enabled: boolean;
}

export async function setup2fa(token: string): Promise<TwoFactorSetupDTO> {
  const response = await apiFetch(`${API_BASE}/me/2fa/setup`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to initiate 2FA setup (${response.status})`);
  }
  return response.json();
}

export async function enable2fa(code: string, token: string): Promise<TwoFactorEnableResponseDTO> {
  const response = await apiFetch(`${API_BASE}/me/2fa/enable`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ code }),
  });
  if (!response.ok) {
    if (response.status === 400) {
      throw new Error("Invalid verification code. Please check your authenticator app.");
    }
    throw new Error(`Failed to enable 2FA (${response.status})`);
  }
  return response.json();
}

export async function disable2fa(password: string, token: string): Promise<void> {
  const response = await apiFetch(`${API_BASE}/me/2fa/disable`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ password }),
  });
  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("Incorrect password");
    }
    throw new Error(`Failed to disable 2FA (${response.status})`);
  }
}

export async function get2faStatus(token: string): Promise<TwoFactorStatusDTO> {
  const response = await apiFetch(`${API_BASE}/me/2fa/status`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to get 2FA status (${response.status})`);
  }
  return response.json();
}

export async function verify2faLogin(tempToken: string, code: string): Promise<{ token: string }> {
  const response = await fetch(`${API_BASE}/auth/2fa/verify`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ tempToken, code }),
  });
  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("Invalid verification code or session expired");
    }
    if (response.status === 429) {
      throw new Error("Too many attempts. Please try again in a minute.");
    }
    throw new Error(`2FA verification failed (${response.status})`);
  }
  const data = await response.json();
  return { token: data.accessToken ?? data.token };
}

