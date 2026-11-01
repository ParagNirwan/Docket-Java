import { API_BASE } from "./documentService";
import { apiFetch } from "../lib/session";

export interface ActiveSession {
  id: string;
  userAgent: string | null;
  ip: string | null;
  lastActiveAt: string;
  current: boolean;
}

function authHeader(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export async function listSessions(token: string): Promise<ActiveSession[]> {
  const response = await apiFetch(`${API_BASE}/me/sessions`, { headers: authHeader(token) });
  if (!response.ok) throw new Error("Could not load sessions");
  return response.json();
}

export async function revokeSession(sessionId: string, token: string): Promise<void> {
  const response = await apiFetch(`${API_BASE}/me/sessions/${sessionId}`, {
    method: "DELETE",
    headers: authHeader(token),
  });
  if (!response.ok) throw new Error("Could not end session");
}

export async function revokeAllSessions(token: string): Promise<void> {
  const response = await apiFetch(`${API_BASE}/me/sessions`, { method: "DELETE", headers: authHeader(token) });
  if (!response.ok) throw new Error("Could not end sessions");
}
