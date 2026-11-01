import { API_BASE } from "./documentService";
import { apiFetch, retryAfterMessage } from "../lib/session";
import type { MemberRole } from "../lib/permissions";

export interface Member {
  membershipId: string;
  name: string;
  email: string;
  role: MemberRole;
  status: string;
  joinedAt: string | null;
  lastActiveAt: string | null;
  currentUser: boolean;
}

export interface Invitation {
  id: string;
  email: string;
  role: MemberRole;
  invitedByName: string | null;
  createdAt: string;
  expiresAt: string;
}

export interface InvitationPreview {
  tenantName: string;
  tenantSlug: string;
  inviterName: string | null;
  email: string;
  role: MemberRole;
  hasAccount: boolean;
}

export interface AcceptedInvitation {
  tenantName: string;
  tenantSlug: string;
}

function tenantUrl(slug: string, path: string): string {
  return `${API_BASE}/tenants/${encodeURIComponent(slug)}/${path}`;
}

function send(url: string, token: string, method = "GET", body?: unknown): Promise<Response> {
  return apiFetch(url, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

// The API answers with a status code and no text, so the wording lives here.
function failure(response: Response, messages: Record<number, string>, fallback: string): Error {
  if (response.status === 429) return new Error(retryAfterMessage(response));
  return new Error(messages[response.status] ?? fallback);
}

export async function listMembers(slug: string, token: string): Promise<Member[]> {
  const response = await send(tenantUrl(slug, "members"), token);
  if (!response.ok) throw failure(response, {}, "Could not load members");
  return response.json();
}

export async function updateMemberRole(slug: string, membershipId: string, role: MemberRole, token: string): Promise<void> {
  const response = await send(tenantUrl(slug, `members/${membershipId}`), token, "PATCH", { role });
  if (!response.ok) {
    throw failure(response, { 403: "You can't give this person that role" }, "Could not change the role");
  }
}

export async function removeMember(slug: string, membershipId: string, token: string): Promise<void> {
  const response = await send(tenantUrl(slug, `members/${membershipId}`), token, "DELETE");
  if (!response.ok) {
    throw failure(response, { 403: "You can't remove this person" }, "Could not remove this person");
  }
}

export async function leaveOrganization(slug: string, token: string): Promise<void> {
  const response = await send(tenantUrl(slug, "members/leave"), token, "POST");
  if (!response.ok) {
    throw failure(response, { 409: "Transfer ownership before leaving" }, "Could not leave the organization");
  }
}

export async function transferOwnership(slug: string, newOwnerMembershipId: string, password: string, token: string): Promise<void> {
  const response = await send(tenantUrl(slug, "transfer-ownership"), token, "POST", { newOwnerMembershipId, password });
  if (!response.ok) {
    throw failure(response, {
      403: "That password isn't right",
      409: "Ownership was changed by someone else. Reload and try again.",
    }, "Could not transfer ownership");
  }
}

export async function listInvitations(slug: string, token: string): Promise<Invitation[]> {
  const response = await send(tenantUrl(slug, "invitations"), token);
  if (!response.ok) throw failure(response, {}, "Could not load invitations");
  return response.json();
}

export async function inviteMember(slug: string, email: string, role: MemberRole, token: string): Promise<Invitation> {
  const response = await send(tenantUrl(slug, "invitations"), token, "POST", { email, role });
  if (!response.ok) {
    throw failure(response, {
      400: "This doesn't look like an email address",
      403: "You can't invite people with this role",
      409: "Already a member, or already invited",
    }, "Could not send the invitation");
  }
  return response.json();
}

export async function resendInvitation(slug: string, invitationId: string, token: string): Promise<void> {
  const response = await send(tenantUrl(slug, `invitations/${invitationId}/resend`), token, "POST");
  if (!response.ok) throw failure(response, {}, "Could not resend the invitation");
}

export async function revokeInvitation(slug: string, invitationId: string, token: string): Promise<void> {
  const response = await send(tenantUrl(slug, `invitations/${invitationId}`), token, "DELETE");
  if (!response.ok) throw failure(response, {}, "Could not revoke the invitation");
}

// Public: the person opening the link may not have an account.
export async function previewInvitation(inviteToken: string): Promise<InvitationPreview> {
  const response = await fetch(`${API_BASE}/invitations/${encodeURIComponent(inviteToken)}`);
  if (!response.ok) {
    throw failure(response, {}, "This invitation is invalid or has expired. Ask for a new one.");
  }
  return response.json();
}

export async function acceptInvitation(inviteToken: string, token: string): Promise<AcceptedInvitation> {
  const response = await send(`${API_BASE}/invitations/${encodeURIComponent(inviteToken)}/accept`, token, "POST");
  if (!response.ok) {
    throw failure(response, {
      403: "This invitation is for a different email address",
      404: "This invitation is invalid or has expired. Ask for a new one.",
    }, "Could not accept the invitation");
  }
  return response.json();
}
