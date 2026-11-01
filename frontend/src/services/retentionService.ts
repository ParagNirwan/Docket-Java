import { API_BASE, DocumentLockedError, type DocumentDTO } from "./documentService";
import { apiFetch } from "../lib/session";
import type { LockMode } from "../lib/retention";

export type ClockStart = "UPLOADED_AT" | "ARCHIVED_AT";
export type OnExpiry = "NOTIFY" | "AUTO_DELETE" | "NONE";

export interface RetentionPolicy {
  id: string;
  folderId: string;
  folderName: string | null;
  retentionDays: number;
  lockMode: LockMode;
  clockStart: ClockStart;
  appliesToSubfolders: boolean;
  onExpiry: OnExpiry;
  lockedDocuments: number;
  createdAt: string;
  updatedAt: string;
}

export type RetentionPolicyInput = Pick<
  RetentionPolicy,
  "folderId" | "retentionDays" | "lockMode" | "clockStart" | "appliesToSubfolders" | "onExpiry"
>;

export interface RetentionSummary {
  lockedDocuments: number;
  expiringSoonCount: number;
  expiringSoon: { id: string; originalFileName: string; folderId: string; retainUntil: string; lockMode: LockMode }[];
  trashRetentionDays: number;
}

export interface LegalHold {
  id: string;
  name: string;
  reason: string;
  createdByName: string | null;
  createdAt: string;
  releasedAt: string | null;
  documentCount: number;
}

export interface LegalHoldTargets {
  documentIds?: string[];
  folderIds?: string[];
  searchQuery?: string;
}

export interface TrashDocument {
  id: string;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  deletedAt: string;
  deletedByName: string | null;
  originalFolderName: string | null;
  purgeAfter: string | null;
  retainUntil: string | null;
  lockMode: LockMode | null;
  legalHold: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface ArchivePreview {
  folderId: string;
  folderName: string;
  retainUntil: string | null;
  lockMode: LockMode | null;
}

function url(slug: string, path: string): string {
  return `${API_BASE}/tenants/${encodeURIComponent(slug)}/${path}`;
}

function send(target: string, token: string, method = "GET", body?: unknown): Promise<Response> {
  return apiFetch(target, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

// The API answers most errors with a status code and no text, so the wording lives here.
async function ok(response: Response, messages: Record<number, string>, fallback: string): Promise<Response> {
  if (response.ok) return response;
  if (response.status === 423) throw await DocumentLockedError.from(response);
  throw new Error(messages[response.status] ?? fallback);
}

// ── Policies ────────────────────────────────────────────────────────────

const POLICY_ERRORS = {
  403: "You don't have permission to change retention policies",
  404: "That tray is no longer available",
  409: "A Compliance policy can only be extended, or this tray already has a policy",
};

export async function listPolicies(slug: string, token: string): Promise<RetentionPolicy[]> {
  const response = await ok(await send(url(slug, "retention-policies"), token), {}, "Could not load retention policies");
  return response.json();
}

export async function getRetentionSummary(slug: string, token: string): Promise<RetentionSummary> {
  const response = await ok(await send(url(slug, "retention-policies/summary"), token), {}, "Could not load the retention summary");
  return response.json();
}

export async function createPolicy(slug: string, input: RetentionPolicyInput, token: string): Promise<RetentionPolicy> {
  const response = await ok(await send(url(slug, "retention-policies"), token, "POST", input), POLICY_ERRORS, "Could not create the policy");
  return response.json();
}

export async function updatePolicy(slug: string, id: string, input: RetentionPolicyInput, token: string): Promise<RetentionPolicy> {
  const response = await ok(await send(url(slug, `retention-policies/${id}`), token, "PUT", input), POLICY_ERRORS, "Could not update the policy");
  return response.json();
}

export async function deletePolicy(slug: string, id: string, token: string): Promise<void> {
  await ok(await send(url(slug, `retention-policies/${id}`), token, "DELETE"), { ...POLICY_ERRORS, 409: "A Compliance policy can't be removed" }, "Could not remove the policy");
}

export async function updateTrashRetention(slug: string, days: number, token: string): Promise<void> {
  await ok(
    await send(url(slug, "trash-settings"), token, "PUT", { trashRetentionDays: days }),
    { 400: "Choose between 1 and 3650 days", 403: "You don't have permission to change this" },
    "Could not save the trash period",
  );
}

// ── Legal holds ─────────────────────────────────────────────────────────

export async function listLegalHolds(slug: string, token: string): Promise<LegalHold[]> {
  const response = await ok(await send(url(slug, "legal-holds"), token), {}, "Could not load legal holds");
  return response.json();
}

export async function createLegalHold(slug: string, name: string, reason: string, targets: LegalHoldTargets, token: string): Promise<LegalHold> {
  const response = await ok(
    await send(url(slug, "legal-holds"), token, "POST", { name, reason, targets }),
    { 400: "That search matches too many documents. Narrow it down", 403: "You don't have permission to apply legal holds", 422: "No documents matched" },
    "Could not apply the legal hold",
  );
  return response.json();
}

export async function releaseLegalHold(slug: string, id: string, token: string): Promise<void> {
  await ok(
    await send(url(slug, `legal-holds/${id}/release`), token, "POST"),
    { 403: "Only the Owner can release a legal hold", 409: "This legal hold has already been released" },
    "Could not release the legal hold",
  );
}

export async function listHoldDocuments(slug: string, id: string, token: string): Promise<Page<DocumentDTO>> {
  const response = await ok(await send(url(slug, `legal-holds/${id}/documents?size=100`), token), {}, "Could not load the documents");
  return response.json();
}

// ── Trash ───────────────────────────────────────────────────────────────

export async function listTrash(slug: string, page: number, token: string): Promise<Page<TrashDocument>> {
  const response = await ok(await send(url(slug, `trash?page=${page}&size=50`), token), { 403: "You don't have permission to see the Trash" }, "Could not load the Trash");
  return response.json();
}

export async function restoreDocument(slug: string, docId: string, token: string): Promise<void> {
  await ok(await send(url(slug, `documents/${docId}/restore`), token, "POST"), { 404: "That document is no longer in the Trash" }, "Could not restore this document");
}

export async function purgeDocument(slug: string, docId: string, token: string): Promise<void> {
  await ok(await send(url(slug, `documents/${docId}/purge`), token, "DELETE"), { 404: "That document is no longer in the Trash" }, "Could not delete this document");
}

export async function emptyTrash(slug: string, token: string): Promise<{ purged: number; skipped: number }> {
  const response = await ok(await send(url(slug, "trash"), token, "DELETE"), {}, "Could not empty the Trash");
  return response.json();
}

// ── Archive ─────────────────────────────────────────────────────────────

export async function getArchivePreview(slug: string, docId: string, token: string): Promise<ArchivePreview> {
  const response = await ok(await send(url(slug, `documents/${docId}/archive-preview`), token), {}, "Could not work out the lock date");
  return response.json();
}

export async function archiveDocument(slug: string, docId: string, token: string): Promise<DocumentDTO> {
  const response = await ok(await send(url(slug, `documents/${docId}/archive`), token, "POST"), { 409: "This document is already archived" }, "Could not archive this document");
  return response.json();
}

export async function unarchiveDocument(slug: string, docId: string, token: string): Promise<DocumentDTO> {
  const response = await ok(await send(url(slug, `documents/${docId}/unarchive`), token, "POST"), { 409: "This document isn't archived" }, "Could not unarchive this document");
  return response.json();
}
