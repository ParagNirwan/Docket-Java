import { apiFetch } from "../lib/session";
import { API_BASE } from "./documentService";

export interface TagDTO {
  id: string;
  name: string;
  color?: string | null;
}

export async function getTags(slug: string, token: string): Promise<TagDTO[]> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/tags`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to load tags (${response.status})`);
  }
  return response.json();
}

export async function createTag(slug: string, name: string, color: string | null | undefined, token: string): Promise<TagDTO> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/tags`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ name, color }),
  });
  if (!response.ok) {
    if (response.status === 409) {
      throw new Error("A tag with this name already exists");
    }
    throw new Error(`Failed to create tag (${response.status})`);
  }
  return response.json();
}

export async function deleteTag(slug: string, tagId: string, token: string): Promise<void> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/tags/${tagId}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to delete tag (${response.status})`);
  }
}

export async function addTagToDocument(slug: string, docId: string, tagId: string, token: string): Promise<TagDTO[]> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/tags`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ tagId }),
  });
  if (!response.ok) {
    throw new Error(`Failed to add tag to document (${response.status})`);
  }
  return response.json();
}

export async function removeTagFromDocument(slug: string, docId: string, tagId: string, token: string): Promise<TagDTO[]> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/tags/${tagId}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to remove tag from document (${response.status})`);
  }
  return response.json();
}

export async function setDocumentTags(slug: string, docId: string, tagIds: string[], token: string): Promise<TagDTO[]> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/tags`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ tagIds }),
  });
  if (!response.ok) {
    throw new Error(`Failed to set document tags (${response.status})`);
  }
  return response.json();
}

export async function updateDocumentMetadata(slug: string, docId: string, metadata: Record<string, unknown>, token: string): Promise<Record<string, unknown>> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/metadata`, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ metadata }),
  });
  if (!response.ok) {
    throw new Error(`Failed to update metadata (${response.status})`);
  }
  return response.json();
}

