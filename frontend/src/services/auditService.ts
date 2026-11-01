import { API_BASE } from "./documentService";
import { apiFetch } from "../lib/session";

export interface AuditEvent {
  id: string;
  actorName: string | null;
  actorEmail: string | null;
  actorType: string;
  action: string;
  targetType: string | null;
  targetId: string | null;
  metadata: Record<string, unknown> | null;
  ip: string | null;
  createdAt: string;
}

export interface AuditPage {
  content: AuditEvent[];
  number: number;
  totalPages: number;
  totalElements: number;
}

export interface AuditFilters {
  actor: string;
  action: string;
  // Dates as yyyy-mm-dd, in the user's time zone
  from: string;
  to: string;
}

// The tenant-scoped actions the backend can currently record.
export const AUDIT_ACTIONS = [
  "DOCUMENT_UPLOADED",
  "DOCUMENT_VIEWED",
  "DOCUMENT_DOWNLOADED",
  "DOCUMENT_DELETED",
  "DOCUMENT_RENAMED",
  "DOCUMENT_MOVED",
  "DOCUMENT_COPIED",
  "FOLDER_RENAMED",
  "FOLDER_MOVED",
  "TRAY_RENAMED",
  "MEMBER_INVITED",
  "INVITATION_RESENT",
  "INVITATION_REVOKED",
  "INVITATION_ACCEPTED",
  "MEMBER_ROLE_CHANGED",
  "MEMBER_REMOVED",
  "MEMBER_LEFT",
  "OWNERSHIP_TRANSFERRED",
  "AUDIT_LOG_EXPORTED",
];

function filterParams(filters: AuditFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.actor.trim()) params.set("actor", filters.actor.trim());
  if (filters.action) params.set("action", filters.action);
  if (filters.from) params.set("from", new Date(`${filters.from}T00:00:00`).toISOString());
  if (filters.to) params.set("to", new Date(`${filters.to}T23:59:59.999`).toISOString());
  return params;
}

export async function getAuditEvents(slug: string, filters: AuditFilters, page: number, token: string): Promise<AuditPage> {
  const params = filterParams(filters);
  params.set("page", String(page));
  params.set("size", "50");
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/audit-events?${params}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw new Error("Could not load the activity log");
  return response.json();
}

export async function downloadAuditCsv(slug: string, filters: AuditFilters, token: string): Promise<void> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/audit-events/export?${filterParams(filters)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw new Error("Could not export the activity log");

  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = "audit-log.csv";
  link.click();
  URL.revokeObjectURL(url);
}
