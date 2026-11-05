import { apiFetch } from "../lib/session";
import { API_BASE } from "./documentService";

export interface ShareLinkDTO {
  id: string;
  documentId: string;
  token?: string | null;
  shareUrl?: string | null;
  expiresAt?: string | null;
  hasPassword?: boolean;
  maxDownloads?: number | null;
  downloadCount: number;
  revoked: boolean;
  createdAt: string;
}

export interface PublicShareInfoDTO {
  documentName: string;
  fileSize: number;
  contentType: string;
  requiresPassword: boolean;
  expired: boolean;
  limitReached: boolean;
  expiresAt?: string | null;
}

export interface CreateShareLinkDTO {
  expiresAt?: string | null;
  password?: string | null;
  maxDownloads?: number | null;
}

export async function createShareLink(
  slug: string,
  docId: string,
  params: CreateShareLinkDTO,
  token: string,
): Promise<ShareLinkDTO> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/share-links`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(params),
  });
  if (!response.ok) {
    throw new Error(`Failed to create share link (${response.status})`);
  }
  return response.json();
}

export async function listShareLinks(
  slug: string,
  docId: string,
  token: string,
): Promise<ShareLinkDTO[]> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/share-links`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to list share links (${response.status})`);
  }
  return response.json();
}

export async function revokeShareLink(
  slug: string,
  docId: string,
  linkId: string,
  token: string,
): Promise<void> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/share-links/${linkId}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to revoke share link (${response.status})`);
  }
}

// Public (unauthenticated) methods
export async function getPublicShareInfo(rawToken: string): Promise<PublicShareInfoDTO> {
  const response = await fetch(`${API_BASE}/public/share/${encodeURIComponent(rawToken)}`);
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error("Share link not found or has been revoked");
    }
    if (response.status === 410) {
      throw new Error("This share link has expired or reached its download limit");
    }
    throw new Error(`Failed to load shared document info (${response.status})`);
  }
  return response.json();
}

export async function downloadSharedDocument(rawToken: string, password?: string): Promise<{ blob: Blob; filename: string }> {
  const response = await fetch(`${API_BASE}/public/share/${encodeURIComponent(rawToken)}/download`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ password: password ?? null }),
  });
  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("Incorrect password for this share link");
    }
    if (response.status === 410) {
      throw new Error("This share link has expired or reached its download limit");
    }
    if (response.status === 429) {
      throw new Error("Too many download attempts. Please wait a minute and try again.");
    }
    throw new Error(`Failed to download shared file (${response.status})`);
  }
  const contentDisposition = response.headers.get("content-disposition");
  let filename = "download";
  if (contentDisposition) {
    const match = /filename\*?=['"]?(?:UTF-8'')?([^'";]+)['"]?/i.exec(contentDisposition);
    if (match?.[1]) {
      filename = decodeURIComponent(match[1]);
    }
  }
  const blob = await response.blob();
  return { blob, filename };
}

