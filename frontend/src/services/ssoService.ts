import { apiMessage } from "../lib/apiMessage";
import { API_BASE } from "./documentService";

export type SsoProvider = "google" | "microsoft";

export const SSO_PROVIDER_LABELS: Record<SsoProvider, string> = {
  google: "Google",
  microsoft: "Microsoft",
};

const STATE_KEY = "docket.sso.state";
const PROVIDER_KEY = "docket.sso.provider";
const REDIRECT_KEY = "docket.sso.redirect";

export function ssoCallbackUrl(): string {
  return `${window.location.origin}/auth/sso/callback`;
}

export async function getSsoProviders(): Promise<SsoProvider[]> {
  const response = await fetch(`${API_BASE}/auth/sso/providers`);
  if (!response.ok) return [];
  const body = (await response.json()) as { providers?: unknown };
  return Array.isArray(body.providers)
    ? body.providers.filter((p): p is SsoProvider => p === "google" || p === "microsoft")
    : [];
}

// The state value ties the provider's redirect back to this browser tab. Without checking
// it, an attacker could make someone's browser finish a login into the attacker's account.
export async function startSso(provider: SsoProvider): Promise<void> {
  const state = crypto.randomUUID();
  const redirectUri = ssoCallbackUrl();
  sessionStorage.setItem(STATE_KEY, state);
  sessionStorage.setItem(PROVIDER_KEY, provider);
  sessionStorage.setItem(REDIRECT_KEY, redirectUri);

  const params = new URLSearchParams({ redirectUri, state });
  const response = await fetch(`${API_BASE}/auth/sso/${provider}?${params}`);
  if (!response.ok) {
    throw new Error(await apiMessage(response, `Couldn't start ${SSO_PROVIDER_LABELS[provider]} sign-in`));
  }
  const { authUrl } = (await response.json()) as { authUrl: string };
  window.location.assign(authUrl);
}

export async function completeSso(code: string, state: string | null): Promise<string> {
  const expectedState = sessionStorage.getItem(STATE_KEY);
  const provider = sessionStorage.getItem(PROVIDER_KEY) as SsoProvider | null;
  const redirectUri = sessionStorage.getItem(REDIRECT_KEY) ?? ssoCallbackUrl();
  sessionStorage.removeItem(STATE_KEY);
  sessionStorage.removeItem(PROVIDER_KEY);
  sessionStorage.removeItem(REDIRECT_KEY);

  if (!expectedState || !state || state !== expectedState || !provider) {
    throw new Error("This sign-in link has expired or was started in another tab. Please try again.");
  }

  const response = await fetch(`${API_BASE}/auth/sso/${provider}/callback`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code, redirectUri }),
  });
  if (!response.ok) {
    throw new Error(await apiMessage(response, "Sign-in failed"));
  }
  const { token } = (await response.json()) as { token: string };
  return token;
}
