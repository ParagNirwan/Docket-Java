import { sendTenantRequest as send, tenantUrl } from "./enterpriseService";

// Keep in step with the backend's ApiScope enum.
export const API_SCOPES = [
  { code: "documents:read", label: "Read documents", description: "Fetch documents and their metadata" },
  { code: "documents:write", label: "Write documents", description: "Upload and update documents" },
] as const;

// Keep in step with the backend's WebhookEvent enum.
export const WEBHOOK_EVENTS = [
  { code: "document.uploaded", label: "Document uploaded", description: "A new document or a new version" },
  { code: "document.updated", label: "Document updated", description: "Renamed, moved, retagged or metadata changed" },
  { code: "document.deleted", label: "Document deleted", description: "Moved to trash, purged or auto-deleted" },
  { code: "document.restored", label: "Document restored", description: "Restored from trash or unarchived" },
  { code: "member.joined", label: "Member joined", description: "Someone joined through a verified domain" },
  { code: "member.removed", label: "Member removed", description: "Removed, or left the organization" },
  { code: "member.role_changed", label: "Member role changed", description: "Someone's role was changed" },
] as const;

export interface ApiKey {
  id: string;
  name: string;
  prefix: string;
  scopes: string[];
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
}

export interface ServiceAccount {
  id: string;
  name: string;
  clientId: string;
  scopes: string[];
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
}

export interface WebhookEndpoint {
  id: string;
  url: string;
  events: string[];
  active: boolean;
  createdAt: string;
  // Only present in the response that created the endpoint.
  signingSecret: string | null;
}

export function listApiKeys(slug: string, token: string) {
  return send<ApiKey[]>(tenantUrl(slug, "/api-keys"), token, {}, "Couldn't load API keys");
}

export function createApiKey(slug: string, name: string, scopes: string[], token: string) {
  return send<{ key: ApiKey; value: string }>(tenantUrl(slug, "/api-keys"), token,
    { method: "POST", body: JSON.stringify({ name, scopes }) }, "Couldn't create the API key");
}

export function revokeApiKey(slug: string, id: string, token: string) {
  return send<ApiKey>(tenantUrl(slug, `/api-keys/${encodeURIComponent(id)}`), token,
    { method: "DELETE" }, "Couldn't revoke the API key");
}

export function listServiceAccounts(slug: string, token: string) {
  return send<ServiceAccount[]>(tenantUrl(slug, "/service-accounts"), token, {}, "Couldn't load service accounts");
}

export function createServiceAccount(slug: string, name: string, scopes: string[], token: string) {
  return send<{ serviceAccount: ServiceAccount; clientSecret: string }>(tenantUrl(slug, "/service-accounts"), token,
    { method: "POST", body: JSON.stringify({ name, scopes }) }, "Couldn't create the service account");
}

export function revokeServiceAccount(slug: string, id: string, token: string) {
  return send<ServiceAccount>(tenantUrl(slug, `/service-accounts/${encodeURIComponent(id)}`), token,
    { method: "DELETE" }, "Couldn't revoke the service account");
}

export function listWebhooks(slug: string, token: string) {
  return send<WebhookEndpoint[]>(tenantUrl(slug, "/webhooks"), token, {}, "Couldn't load webhooks");
}

export function createWebhook(slug: string, url: string, events: string[], token: string) {
  return send<WebhookEndpoint>(tenantUrl(slug, "/webhooks"), token,
    { method: "POST", body: JSON.stringify({ url, events }) }, "Couldn't create the webhook");
}

export function setWebhookActive(slug: string, id: string, active: boolean, token: string) {
  return send<WebhookEndpoint>(tenantUrl(slug, `/webhooks/${encodeURIComponent(id)}?active=${active}`), token,
    { method: "PATCH" }, "Couldn't update the webhook");
}

export function deleteWebhook(slug: string, id: string, token: string) {
  return send<void>(tenantUrl(slug, `/webhooks/${encodeURIComponent(id)}`), token,
    { method: "DELETE" }, "Couldn't delete the webhook");
}
