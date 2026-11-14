import { apiFetch } from "../lib/session";

export type FolderVisibility = "ALL_MEMBERS" | "ADMIN_ONLY" | "CUSTOM";

export interface Tray {
  id: string;
  tenantId?: string;
  parentFolderId?: string | null;
  name: string;
  description?: string | null;
  isTray: boolean;
  isDefault: boolean;
  isArchive?: boolean;
  visibility: FolderVisibility;
  sortOrder: number;
  createdBy?: string | null;
  createdAt?: string;
  updatedAt?: string;
  itemCount?: number;
}

export interface FolderItem {
  id: string;
  tenantId?: string;
  parentFolderId: string | null;
  name: string;
  description?: string | null;
  isTray: boolean;
  isDefault: boolean;
  isArchive?: boolean;
  visibility: FolderVisibility;
  sortOrder?: number;
  createdBy?: string | null;
  createdAt?: string;
  updatedAt?: string;
  itemCount?: number;
}

export interface FileItem {
  id: string;
  name: string;
  detail: string;
  size?: string;
  date: string;
  status?: string;
  folderId?: string;
}

export interface CreateTrayRequestDTO {
  name: string;
  description?: string;
  visibility: FolderVisibility;
}

export interface UpdateTrayDTO {
  name: string;
  description?: string;
  visibility: FolderVisibility;
  sortOrder: number;
}

export interface CreateFolderDTO {
  name: string;
  description?: string;
}

export interface BreadcrumbNode {
  id: string;
  name: string;
  isTray: boolean;
}

export const API_BASE = import.meta.env.VITE_API_URL || "/api";

/** GET /api/tenants/{slug}/trays */
export async function getTenantTrays(
  slug: string,
  token?: string | null,
): Promise<Tray[]> {
  if (!token) throw new Error("Authentication required to fetch trays");

  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays`, {
    method: "GET",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(errorData.message || `Failed to fetch trays (${response.status})`);
  }

  const data = await response.json();
  return (data as Partial<FolderItem>[]).map((item) => ({
    id: String(item.id),
    tenantId: item.tenantId,
    parentFolderId: item.parentFolderId,
    name: item.name || "Untitled Tray",
    description: item.description || null,
    isTray: Boolean(item.isTray ?? true),
    isDefault: Boolean(item.isDefault ?? false),
    isArchive: Boolean(item.isArchive ?? false),
    visibility: (item.visibility as FolderVisibility) || "ALL_MEMBERS",
    sortOrder: item.sortOrder ?? 0,
    itemCount: item.itemCount ?? 0,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  }));
}

/** POST /api/tenants/{slug}/trays */
export async function createTray(
  slug: string,
  data: CreateTrayRequestDTO,
  token?: string | null,
): Promise<Tray> {
  if (!token) throw new Error("Authentication required to create a tray");

  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      name: data.name.trim(),
      description: data.description?.trim() || null,
      visibility: data.visibility,
    }),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(errorData.message || `Failed to create tray (${response.status})`);
  }

  const created = await response.json();
  return {
    id: String(created.id),
    tenantId: created.tenantId,
    parentFolderId: created.parentFolderId,
    name: created.name,
    description: created.description,
    isTray: true,
    isDefault: Boolean(created.isDefault),
    visibility: created.visibility,
    sortOrder: created.sortOrder ?? 0,
    itemCount: 0,
    createdAt: created.createdAt,
    updatedAt: created.updatedAt,
  };
}

/** PUT /api/tenants/{slug}/trays/{trayId} */
export async function updateTray(
  slug: string,
  trayId: string,
  data: UpdateTrayDTO,
  token?: string | null,
): Promise<Tray> {
  if (!token) throw new Error("Authentication required to update a tray");

  const response = await apiFetch(
    `${API_BASE}/tenants/${encodeURIComponent(slug)}/trays/${trayId}`,
    {
      method: "PUT",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify(data),
    },
  );

  if (!response.ok) throw new Error(`Failed to update tray (${response.status})`);
  return response.json();
}

/** DELETE /api/tenants/{slug}/trays/{trayId} */
export async function deleteTray(
  slug: string,
  trayId: string,
  token?: string | null,
): Promise<boolean> {
  if (!token) throw new Error("Authentication required to delete a tray");
  await sendDelete(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays/${trayId}`, token, "tray");
  return true;
}

/**
 * GET /api/tenants/{slug}/folders/{folderId}/children
 * Returns sub-folders under this folder or tray. The `files` field is always
 * empty because documents are fetched separately via the document API.
 */
export async function getFolderChildren(
  slug: string,
  folderId: string,
  token?: string | null,
): Promise<{ folders: FolderItem[]; files: FileItem[] }> {
  const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(folderId);
  if (!token) throw new Error("Authentication required to load folder contents");
  if (!isUuid) throw new Error("Invalid folder ID");

  const response = await apiFetch(
    `${API_BASE}/tenants/${encodeURIComponent(slug)}/folders/${encodeURIComponent(folderId)}/children`,
    { method: "GET", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" } },
  );

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(errorData.message || `Failed to load folder contents (${response.status})`);
  }

  const data = await response.json() as Partial<FolderItem>[];
  const folders: FolderItem[] = data.map((item) => ({
    id: String(item.id),
    tenantId: item.tenantId,
    parentFolderId: item.parentFolderId ?? folderId,
    name: item.name || "Untitled Folder",
    description: item.description || null,
    isTray: Boolean(item.isTray ?? false),
    isDefault: Boolean(item.isDefault ?? false),
    isArchive: Boolean(item.isArchive ?? false),
    visibility: (item.visibility as FolderVisibility) || "ALL_MEMBERS",
    sortOrder: item.sortOrder ?? 0,
    itemCount: item.itemCount ?? 0,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  }));

  return { folders, files: [] };
}

/** POST /api/tenants/{slug}/folders/{parentFolderId}/children */
export async function createSubFolder(
  slug: string,
  parentFolderId: string,
  data: CreateFolderDTO,
  token?: string | null,
): Promise<FolderItem> {
  if (!token) throw new Error("Authentication required to create a folder");

  const response = await apiFetch(
    `${API_BASE}/tenants/${encodeURIComponent(slug)}/folders/${encodeURIComponent(parentFolderId)}/children`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        name: data.name.trim(),
        description: data.description?.trim() || null,
      }),
    },
  );

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(errorData.message || `Failed to create folder (${response.status})`);
  }

  const created = await response.json();
  return {
    id: String(created.id),
    tenantId: created.tenantId,
    parentFolderId: created.parentFolderId ?? parentFolderId,
    name: created.name,
    description: created.description,
    isTray: false,
    isDefault: Boolean(created.isDefault),
    visibility: created.visibility,
    sortOrder: created.sortOrder ?? 0,
    itemCount: 0,
    createdAt: created.createdAt,
    updatedAt: created.updatedAt,
  };
}

/** DELETE /api/tenants/{slug}/folders/{folderId} */
export async function deleteFolder(
  slug: string,
  folderId: string,
  token?: string | null,
): Promise<boolean> {
  if (!token) throw new Error("Authentication required to delete a folder");
  await sendDelete(`${API_BASE}/tenants/${encodeURIComponent(slug)}/folders/${folderId}`, token, "folder");
  return true;
}

// Documents inside go to the Trash. The API answers with a status code and no text,
// so the wording lives here.
async function sendDelete(url: string, token: string, noun: "tray" | "folder"): Promise<void> {
  const response = await apiFetch(url, { method: "DELETE", headers: { Authorization: `Bearer ${token}` } });
  if (response.ok) return;
  const messages: Record<number, string> = {
    403: `You don't have permission to delete this ${noun}, or it still has documents in it`,
    404: `That ${noun} is no longer available`,
    409: `This ${noun} has a Compliance policy, so it can't be deleted`,
    423: `This ${noun} contains locked documents, so it can't be deleted`,
  };
  throw new Error(messages[response.status] ?? `Failed to delete ${noun} (${response.status})`);
}

/**
 * RENAME OR MOVE A FOLDER
 * ---------------------------------------------------------------------------
 * Backend Endpoint: PATCH /api/tenants/{slug}/folders/{folderId}
 * Required Permission: FOLDERS_UPDATE ("folders.update")
 * The API answers with a status code and no text, so the wording lives here.
 */
export async function updateFolder(
  slug: string,
  folderId: string,
  changes: { name?: string; parentFolderId?: string },
  token: string,
): Promise<FolderItem> {
  const response = await apiFetch(
    `${API_BASE}/tenants/${encodeURIComponent(slug)}/folders/${encodeURIComponent(folderId)}`,
    {
      method: "PATCH",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify(changes),
    },
  );

  if (!response.ok) {
    const messages: Record<number, string> = {
      400: "A folder can't be moved into itself",
      403: "You don't have permission to change this folder",
      404: "That folder is no longer available",
      409: "A folder with this name already exists here",
    };
    throw new Error(messages[response.status] ?? `Failed to update folder (${response.status})`);
  }
  return response.json();
}

/**
 * GET /api/tenants/{slug}/folders/{folderId}/breadcrumb
 * Returns the ancestor chain from the root Tray down to the current folder.
 */
export async function getBreadcrumbs(
  slug: string,
  currentFolderOrTrayId: string,
  token?: string | null,
): Promise<BreadcrumbNode[]> {
  const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(currentFolderOrTrayId);
  if (!token) throw new Error("Authentication required to load breadcrumbs");
  if (!isUuid) throw new Error("Invalid folder or tray ID");

  const response = await apiFetch(
    `${API_BASE}/tenants/${encodeURIComponent(slug)}/folders/${encodeURIComponent(currentFolderOrTrayId)}/breadcrumb`,
    { headers: { Authorization: `Bearer ${token}` } },
  );

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(errorData.message || `Failed to load the folder path (${response.status})`);
  }
  return response.json();
}

// ─── PHASE 6: CUSTOM TRAY PERMISSIONS & QUOTAS ──────────────────────────

export interface TrayPermissionsDTO {
  userIds: string[];
}

export interface TenantUsageDTO {
  storageBytesUsed: number;
  storageQuotaBytes: number | null;
  documentCount: number;
}

export async function getTrayPermissions(
  slug: string,
  trayId: string,
  token: string,
): Promise<TrayPermissionsDTO> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays/${trayId}/permissions`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to load tray permissions (${response.status})`);
  }
  return response.json();
}

export async function updateTrayPermissions(
  slug: string,
  trayId: string,
  userIds: string[],
  token: string,
): Promise<TrayPermissionsDTO> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays/${trayId}/permissions`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ userIds }),
  });
  if (!response.ok) {
    throw new Error(`Failed to update tray permissions (${response.status})`);
  }
  return response.json();
}

export async function getTenantUsage(
  slug: string,
  token: string,
): Promise<TenantUsageDTO> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/usage`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`Failed to load workspace usage (${response.status})`);
  }
  return response.json();
}

export async function updateTenantQuota(
  slug: string,
  quotaBytes: number | null,
  token: string,
): Promise<TenantUsageDTO> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/quota`, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ quotaBytes }),
  });
  if (!response.ok) {
    throw new Error(`Failed to update storage quota (${response.status})`);
  }
  return response.json();
}
