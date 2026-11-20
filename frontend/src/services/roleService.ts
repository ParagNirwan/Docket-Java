import { sendTenantRequest as send, tenantUrl } from "./enterpriseService";

export interface CustomRole {
  id: string;
  name: string;
  description: string | null;
  permissions: string[];
  memberCount: number;
}

export interface CustomRoleList {
  roles: CustomRole[];
  // Every permission a custom role may contain (owner-only ones are excluded server-side).
  grantablePermissions: string[];
}

export function listCustomRoles(slug: string, token: string) {
  return send<CustomRoleList>(tenantUrl(slug, "/roles"), token, {}, "Couldn't load roles");
}

export function createCustomRole(slug: string, role: { name: string; description?: string; permissions: string[] }, token: string) {
  return send<CustomRole>(tenantUrl(slug, "/roles"), token,
    { method: "POST", body: JSON.stringify(role) }, "Couldn't create the role");
}

export function updateCustomRole(slug: string, id: string, role: { name: string; description?: string; permissions: string[] }, token: string) {
  return send<CustomRole>(tenantUrl(slug, `/roles/${encodeURIComponent(id)}`), token,
    { method: "PUT", body: JSON.stringify(role) }, "Couldn't update the role");
}

export function deleteCustomRole(slug: string, id: string, token: string) {
  return send<void>(tenantUrl(slug, `/roles/${encodeURIComponent(id)}`), token,
    { method: "DELETE" }, "Couldn't delete the role");
}

export function assignCustomRole(slug: string, membershipId: string, customRoleId: string | null, token: string) {
  return send<void>(tenantUrl(slug, `/members/${encodeURIComponent(membershipId)}/custom-role`), token,
    { method: "PUT", body: JSON.stringify({ customRoleId }) }, "Couldn't assign the role");
}

// "retention.manage" -> "Retention: manage"
export function permissionLabel(code: string): string {
  const [area, action = ""] = code.split(".");
  const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);
  return `${cap(area)}: ${action.replaceAll("_", " ")}`;
}
