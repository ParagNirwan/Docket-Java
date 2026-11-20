import { apiMessage } from "../lib/apiMessage";
import { apiFetch } from "../lib/session";
import type { MemberRole } from "../lib/permissions";
import { API_BASE } from "./documentService";

// ─── Domains ────────────────────────────────────────────────────────────────

export type DomainJoinMode = "AUTO" | "REQUEST";

export interface TenantDomain {
  domain: string;
  verificationToken: string;
  verifiedAt: string | null;
  joinMode: DomainJoinMode;
  ssoRequired: boolean;
  createdAt: string;
}

// Must match TenantDomainService.TXT_RECORD_PREFIX / txtRecordValue on the backend.
export function txtRecordName(domain: string): string {
  return `_docket-verification.${domain}`;
}

export function txtRecordValue(token: string): string {
  return `docket-verification=${token}`;
}

function tenantUrl(slug: string, path: string): string {
  return `${API_BASE}/tenants/${encodeURIComponent(slug)}${path}`;
}

async function send<T>(url: string, token: string, init: RequestInit, fallback: string): Promise<T> {
  const response = await apiFetch(url, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    throw new Error(await apiMessage(response, fallback));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export { send as sendTenantRequest, tenantUrl };

export function listDomains(slug: string, token: string) {
  return send<TenantDomain[]>(tenantUrl(slug, "/domains"), token, {}, "Couldn't load domains");
}

export function addDomain(slug: string, domain: string, token: string) {
  return send<TenantDomain>(tenantUrl(slug, "/domains"), token,
    { method: "POST", body: JSON.stringify({ domain }) }, "Couldn't add the domain");
}

export function verifyDomain(slug: string, domain: string, token: string) {
  return send<TenantDomain>(tenantUrl(slug, "/domains/verify"), token,
    { method: "POST", body: JSON.stringify({ domain }) }, "Verification failed");
}

export function updateDomain(
  slug: string,
  domain: string,
  changes: { joinMode?: DomainJoinMode; ssoRequired?: boolean },
  token: string,
) {
  return send<TenantDomain>(tenantUrl(slug, `/domains/${encodeURIComponent(domain)}`), token,
    { method: "PUT", body: JSON.stringify(changes) }, "Couldn't update the domain");
}

export function removeDomain(slug: string, domain: string, token: string) {
  return send<void>(tenantUrl(slug, `/domains/${encodeURIComponent(domain)}`), token,
    { method: "DELETE" }, "Couldn't remove the domain");
}

// ─── Invite links ───────────────────────────────────────────────────────────

export interface InviteLink {
  id: string;
  role: MemberRole;
  maxUses: number | null;
  uses: number;
  expiresAt: string | null;
  createdByName: string | null;
  createdAt: string;
  // Only present in the response that created the link.
  url: string | null;
}

export interface InviteLinkPreview {
  tenantName: string;
  tenantSlug: string;
  role: MemberRole;
}

export function getInviteLinkSettings(slug: string, token: string) {
  return send<{ enabled: boolean }>(tenantUrl(slug, "/invite-links/settings"), token, {}, "Couldn't load invite link settings");
}

export function setInviteLinksEnabled(slug: string, enabled: boolean, token: string) {
  return send<{ enabled: boolean }>(tenantUrl(slug, "/invite-links/settings"), token,
    { method: "PUT", body: JSON.stringify({ enabled }) }, "Couldn't update invite link settings");
}

export function listInviteLinks(slug: string, token: string) {
  return send<InviteLink[]>(tenantUrl(slug, "/invite-links"), token, {}, "Couldn't load invite links");
}

export function createInviteLink(
  slug: string,
  request: { role: MemberRole; maxUses?: number; expiresInDays?: number },
  token: string,
) {
  return send<InviteLink>(tenantUrl(slug, "/invite-links"), token,
    { method: "POST", body: JSON.stringify(request) }, "Couldn't create the invite link");
}

export function revokeInviteLink(slug: string, linkId: string, token: string) {
  return send<void>(tenantUrl(slug, `/invite-links/${encodeURIComponent(linkId)}`), token,
    { method: "DELETE" }, "Couldn't revoke the invite link");
}

export async function previewInviteLink(linkToken: string): Promise<InviteLinkPreview> {
  const response = await fetch(`${API_BASE}/invite-links/${encodeURIComponent(linkToken)}`);
  if (!response.ok) {
    throw new Error("This invite link is invalid or has expired. Ask for a new one.");
  }
  return response.json();
}

export async function joinWithInviteLink(linkToken: string, token: string): Promise<{ tenantName: string; tenantSlug: string }> {
  return send(`${API_BASE}/invite-links/${encodeURIComponent(linkToken)}/join`, token,
    { method: "POST" }, "Couldn't join with this link");
}

// ─── Insights ───────────────────────────────────────────────────────────────

export interface TenantInsights {
  plan: string;
  billingEnforced: boolean;
  seatsUsed: number;
  seatLimit: number | null;
  aiQueriesThisMonth: number;
  aiQueryLimit: number | null;
  auditEventsLast30Days: number;
  topActionsLast30Days: Record<string, number>;
}

export function getTenantInsights(slug: string, token: string) {
  return send<TenantInsights>(tenantUrl(slug, "/insights"), token, {}, "Couldn't load insights");
}
