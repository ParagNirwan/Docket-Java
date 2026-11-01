import { isTokenExpired, parseJwt } from "./jwt";

const API_BASE = import.meta.env.VITE_API_URL || "/api";
const TOKEN_KEY = "token";

type TokenListener = (token: string | null) => void;
const listeners = new Set<TokenListener>();

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function storeToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
  listeners.forEach((listener) => listener(token));
}

export function onTokenChange(listener: TokenListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

let refreshInFlight: Promise<string | null> | null = null;

// Gets a new access token using the refresh cookie. Resolves to null if there isn't
// one. staleToken is the token the caller found to be out of date.
//
// Refresh tokens are single-use, so simultaneous refreshes must be avoided: the
// promise covers this tab and the Web Lock covers other tabs. Whoever waited for the
// lock usually finds that another tab already did the work.
export function refreshAccessToken(staleToken: string | null = getStoredToken()): Promise<string | null> {
  if (!refreshInFlight) {
    const run = () => performRefresh(staleToken);
    const attempt = navigator.locks ? navigator.locks.request("docket-token-refresh", run) : run();
    refreshInFlight = attempt.finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function performRefresh(staleToken: string | null): Promise<string | null> {
  const current = getStoredToken();
  if (current && current !== staleToken && !isTokenExpired(parseJwt(current))) {
    return current;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}/auth/refresh`, { method: "POST", credentials: "include" });
  } catch {
    // Offline isn't logged out: keep the session and let a later request try again.
    return null;
  }

  if (response.status === 401) {
    storeToken(null);
    return null;
  }
  if (!response.ok) {
    return null;
  }

  const { token } = (await response.json()) as { token: string };
  storeToken(token);
  return token;
}

// fetch() for authenticated API calls. It always sends the newest token, whichever one
// the caller was holding, and when the server answers 401 it refreshes and retries once.
export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has("Authorization")) {
    return fetch(input, init);
  }

  const latest = getStoredToken();
  if (latest) {
    headers.set("Authorization", `Bearer ${latest}`);
  }
  const usedToken = headers.get("Authorization")!.slice("Bearer ".length);

  const response = await fetch(input, { ...init, headers });
  if (response.status !== 401) {
    return response;
  }

  const refreshed = await refreshAccessToken(usedToken);
  if (!refreshed) {
    return response;
  }
  headers.set("Authorization", `Bearer ${refreshed}`);
  return fetch(input, { ...init, headers });
}

// Tells the server to end this session. The local logout happens either way.
export async function endServerSession() {
  try {
    await fetch(`${API_BASE}/auth/logout`, { method: "POST", credentials: "include" });
  } catch {
    // The refresh token simply expires by itself.
  }
}

// Turns a Retry-After header into words for the user.
export function retryAfterMessage(response: Response): string {
  const seconds = Number(response.headers.get("Retry-After"));
  if (!seconds) {
    return "Too many attempts. Please try again later.";
  }
  const minutes = Math.ceil(seconds / 60);
  return `Too many attempts. Please try again in ${minutes} minute${minutes === 1 ? "" : "s"}.`;
}
