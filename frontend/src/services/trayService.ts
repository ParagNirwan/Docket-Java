/**
 * ============================================================================
 * TRAY & FOLDER API SERVICE (LEARNING GUIDE & INTEGRATION STUB)
 * ============================================================================
 * 
 * This file contains the API client functions and educational guides for
 * communicating with the Spring Boot backend endpoints for Trays and Folders.
 * 
 * 💡 LEARNING TIP:
 * The functions below are currently set up to simulate responses using an
 * in-memory state store so you can interact with the frontend immediately.
 * 
 * Each function contains a commented-out real `fetch(...)` implementation
 * with explanations of:
 *   1. HTTP Method & URL pattern
 *   2. Headers (e.g. Bearer token, Content-Type)
 *   3. Request body (matching backend DTOs)
 *   4. Response handling & status codes
 * 
 * When you are ready to test against your live backend, simply uncomment
 * the fetch calls inside the functions!
 * ============================================================================
 */

export type FolderVisibility = "ALL_MEMBERS" | "ADMIN_ONLY" | "CUSTOM";

export interface Tray {
  id: string;
  tenantId?: string;
  parentFolderId?: string | null;
  name: string;
  description?: string | null;
  isTray: boolean;
  isDefault: boolean;
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

// ----------------------------------------------------------------------------
// INITIAL MOCK DATA (mirrors default provisioning from Issue #3)
// ----------------------------------------------------------------------------
const DEFAULT_TRAYS: Tray[] = [
  {
    id: "general",
    name: "General",
    description: "General documents and records",
    isTray: true,
    isDefault: true,
    visibility: "ALL_MEMBERS",
    sortOrder: 1,
    itemCount: 4,
    createdAt: new Date().toISOString(),
  },
  {
    id: "trash",
    name: "Trash",
    description: "Deleted documents kept for retention",
    isTray: true,
    isDefault: true,
    visibility: "ADMIN_ONLY",
    sortOrder: 2,
    itemCount: 0,
    createdAt: new Date().toISOString(),
  },
];

// Initial sub-folders for demonstration
let mockFolders: FolderItem[] = [
  {
    id: "folder-contracts",
    parentFolderId: "general",
    name: "Contracts & Agreements",
    description: "Active vendor and client contracts",
    isTray: false,
    isDefault: false,
    visibility: "ALL_MEMBERS",
    sortOrder: 1,
    itemCount: 2,
    createdAt: new Date().toISOString(),
  },
  {
    id: "folder-compliance",
    parentFolderId: "general",
    name: "Compliance & Audits",
    description: "Annual filings and audit receipts",
    isTray: false,
    isDefault: false,
    visibility: "ALL_MEMBERS",
    sortOrder: 2,
    itemCount: 1,
    createdAt: new Date().toISOString(),
  },
  {
    id: "folder-q3",
    parentFolderId: "folder-contracts",
    name: "Q3 Vendor Agreements",
    description: "Third quarter signed NDAs and MSAs",
    isTray: false,
    isDefault: false,
    visibility: "ALL_MEMBERS",
    sortOrder: 1,
    itemCount: 1,
    createdAt: new Date().toISOString(),
  },
];

// Initial mock files
let mockFiles: FileItem[] = [
  {
    id: "file-1",
    name: "Board resolution — Q3 approval.pdf",
    detail: "Corporate governance · PDF",
    size: "1.4 MB",
    date: "Today",
    status: "Needs review",
    folderId: "general",
  },
  {
    id: "file-2",
    name: "Master services agreement.docx",
    detail: "Commercial · DOCX",
    size: "420 KB",
    date: "Yesterday",
    status: "Needs review",
    folderId: "folder-contracts",
  },
  {
    id: "file-3",
    name: "Annual filing receipt.pdf",
    detail: "Company records · PDF",
    size: "890 KB",
    date: "Oct 10",
    status: "In progress",
    folderId: "folder-compliance",
  },
  {
    id: "file-4",
    name: "Vendor Statement of Work - Cloud Migration.pdf",
    detail: "Vendor contract · PDF",
    size: "2.1 MB",
    date: "Oct 12",
    status: "Approved",
    folderId: "folder-q3",
  },
];

let mockTrays: Tray[] = [...DEFAULT_TRAYS];

// ----------------------------------------------------------------------------
// API FUNCTIONS
// ----------------------------------------------------------------------------

/**
 * 1. FETCH TRAYS
 * ---------------------------------------------------------------------------
 * Backend Endpoint: GET /api/tenants/{slug}/trays
 * Controller: TenantController.java -> getTenantTrays()
 * Required Permission: TENANT_READ ("tenant.read")
 *
 * HOW TO MAKE THIS API CALL IN PRODUCTION:
 * ```ts
 * const response = await fetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays`, {
 *   method: "GET",
 *   headers: {
 *     Authorization: `Bearer ${token}`,
 *     "Content-Type": "application/json",
 *   },
 * });
 * if (!response.ok) {
 *   throw new Error(`Failed to fetch trays (${response.status})`);
 * }
 * return await response.json();
 * ```
 */
export async function getTenantTrays(
  slug: string,
  token?: string | null,
): Promise<Tray[]> {
  // If user is authenticated, call the live backend endpoint
  if (token) {
    try {
      const response = await fetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays`, {
        method: "GET",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      });

      if (response.ok) {
        const data = await response.json();
        if (Array.isArray(data) && data.length > 0) {
          // Successfully loaded from backend
          return data.map((item: Partial<FolderItem>) => ({
            id: String(item.id),
            tenantId: item.tenantId,
            parentFolderId: item.parentFolderId,
            name: item.name || "Untitled Tray",
            description: item.description || null,
            isTray: Boolean(item.isTray ?? true),
            isDefault: Boolean(item.isDefault ?? false),
            visibility: (item.visibility as FolderVisibility) || "ALL_MEMBERS",
            sortOrder: item.sortOrder ?? 0,
            itemCount: item.itemCount ?? 0,
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
          }));
        }
      }
    } catch (err) {
      // Backend not reached or offline, fallback to in-memory state
      console.warn("Could not load trays from backend API, using fallback data:", err);
    }
  }

  // Fallback to in-memory/default trays
  return [...mockTrays];
}

/**
 * 2. CREATE TRAY
 * ---------------------------------------------------------------------------
 * Backend Endpoint: POST /api/tenants/{slug}/trays
 * Controller: TenantController.java -> addTray()
 * Required Permission: TRAYS_CREATE ("trays.create")
 * Request Body: CreateTrayRequestDTO { name, description, visibility }
 *
 * HOW TO MAKE THIS API CALL IN PRODUCTION:
 * ```ts
 * const response = await fetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays`, {
 *   method: "POST",
 *   headers: {
 *     Authorization: `Bearer ${token}`,
 *     "Content-Type": "application/json",
 *   },
 *   body: JSON.stringify(data),
 * });
 * if (!response.ok) {
 *   const errorData = await response.json().catch(() => ({}));
 *   throw new Error(errorData.message || `Failed to create tray (${response.status})`);
 * }
 * return await response.json();
 * ```
 */
export async function createTray(
  slug: string,
  data: CreateTrayRequestDTO,
  token?: string | null,
): Promise<Tray> {
  if (token) {
    try {
      const response = await fetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name: data.name.trim(),
          description: data.description?.trim() || null,
          visibility: data.visibility,
        }),
      });

      if (response.ok) {
        const created = await response.json();
        const tray: Tray = {
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
        mockTrays.push(tray);
        return tray;
      }
    } catch (err) {
      console.warn("Backend createTray call failed, using local simulation:", err);
    }
  }

  // Local fallback
  const newTray: Tray = {
    id: `tray-${Date.now()}`,
    name: data.name.trim(),
    description: data.description?.trim() || null,
    isTray: true,
    isDefault: false,
    visibility: data.visibility,
    sortOrder: mockTrays.length + 1,
    itemCount: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  mockTrays.push(newTray);
  return newTray;
}

/**
 * 3. UPDATE TRAY
 * ---------------------------------------------------------------------------
 * Backend Endpoint: PUT /api/tenants/{slug}/trays/{trayId}
 * Controller: TenantController.java -> updateTray()
 * Required Permission: TRAYS_UPDATE ("trays.update")
 * Request Body: UpdateTrayDTO { name, description, visibility, sortOrder }
 * Note: Default trays (General, Review, Trash) cannot be modified.
 *
 * HOW TO MAKE THIS API CALL IN PRODUCTION:
 * ```ts
 * const response = await fetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays/${trayId}`, {
 *   method: "PUT",
 *   headers: {
 *     Authorization: `Bearer ${token}`,
 *     "Content-Type": "application/json",
 *   },
 *   body: JSON.stringify(data),
 * });
 * if (!response.ok) {
 *   throw new Error(`Failed to update tray (${response.status})`);
 * }
 * return await response.json();
 * ```
 */
export async function updateTray(
  _slug: string,
  trayId: string,
  data: UpdateTrayDTO,
  _token?: string | null,
): Promise<Tray> {
  // --- PRODUCTION API CALL (Uncomment when connecting to backend) ---
  /*
  const response = await fetch(`${API_BASE}/tenants/${encodeURIComponent(_slug)}/trays/${trayId}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${_token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  });
  if (!response.ok) {
    throw new Error(`Failed to update tray (${response.status})`);
  }
  return await response.json();
  */

  // --- LEARNING FALLBACK (Simulated in-memory) ---
  const existingIndex = mockTrays.findIndex((t) => t.id === trayId);
  if (existingIndex === -1) throw new Error("Tray not found");
  if (mockTrays[existingIndex].isDefault) {
    throw new Error("Default trays cannot be modified");
  }

  const updatedTray: Tray = {
    ...mockTrays[existingIndex],
    name: data.name.trim(),
    description: data.description?.trim() || null,
    visibility: data.visibility,
    sortOrder: data.sortOrder,
    updatedAt: new Date().toISOString(),
  };

  mockTrays[existingIndex] = updatedTray;
  return updatedTray;
}

/**
 * 4. DELETE TRAY
 * ---------------------------------------------------------------------------
 * Backend Endpoint: DELETE /api/tenants/{slug}/trays/{trayId}
 * Required Permission: TRAYS_DELETE ("trays.delete")
 * Note: Default trays cannot be deleted.
 *
 * HOW TO MAKE THIS API CALL IN PRODUCTION:
 * ```ts
 * const response = await fetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/trays/${trayId}`, {
 *   method: "DELETE",
 *   headers: {
 *     Authorization: `Bearer ${token}`,
 *   },
 * });
 * if (!response.ok) {
 *   throw new Error(`Failed to delete tray (${response.status})`);
 * }
 * return true;
 * ```
 */
export async function deleteTray(
  _slug: string,
  trayId: string,
  _token?: string | null,
): Promise<boolean> {
  // --- PRODUCTION API CALL (Uncomment when connecting to backend) ---
  /*
  const response = await fetch(`${API_BASE}/tenants/${encodeURIComponent(_slug)}/trays/${trayId}`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${_token}`,
    },
  });
  if (!response.ok) {
    throw new Error(`Failed to delete tray (${response.status})`);
  }
  return true;
  */

  // --- LEARNING FALLBACK (Simulated in-memory) ---
  const existing = mockTrays.find((t) => t.id === trayId);
  if (!existing) throw new Error("Tray not found");
  if (existing.isDefault) throw new Error("Default trays cannot be deleted");

  mockTrays = mockTrays.filter((t) => t.id !== trayId);
  // Also delete associated sub-folders
  mockFolders = mockFolders.filter((f) => f.parentFolderId !== trayId);
  return true;
}

/**
 * 5. GET FOLDER / TRAY CHILDREN
 * ---------------------------------------------------------------------------
 * Backend Endpoint: GET /api/tenants/{slug}/folders/{folderId}/children
 * Controller: FolderController.java -> getChildren()
 * Required Permission: DOCUMENTS_READ ("documents.read")
 * Returns sub-folders and documents residing under this folder or Tray.
 *
 * HOW TO MAKE THIS API CALL IN PRODUCTION:
 * ```ts
 * const response = await fetch(
 *   `${API_BASE}/tenants/${encodeURIComponent(slug)}/folders/${folderId}/children`,
 *   {
 *     method: "GET",
 *     headers: {
 *       Authorization: `Bearer ${token}`,
 *       "Content-Type": "application/json",
 *     },
 *   }
 * );
 * if (!response.ok) {
 *   throw new Error(`Failed to load folder contents (${response.status})`);
 * }
 * return await response.json();
 * ```
 */
export async function getFolderChildren(
  slug: string,
  folderId: string,
  token?: string | null,
): Promise<{ folders: FolderItem[]; files: FileItem[] }> {
  // Check if folderId is a UUID before calling the backend (backend expects UUID)
  const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(folderId);

  if (token && isUuid) {
    try {
      const response = await fetch(
        `${API_BASE}/tenants/${encodeURIComponent(slug)}/folders/${encodeURIComponent(folderId)}/children`,
        {
          method: "GET",
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json",
          },
        }
      );

      if (response.ok) {
        const data = await response.json();
        if (Array.isArray(data)) {
          const folders: FolderItem[] = data.map((item: Partial<FolderItem>) => ({
            id: String(item.id),
            tenantId: item.tenantId,
            parentFolderId: item.parentFolderId ?? folderId,
            name: item.name || "Untitled Folder",
            description: item.description || null,
            isTray: Boolean(item.isTray ?? false),
            isDefault: Boolean(item.isDefault ?? false),
            visibility: (item.visibility as FolderVisibility) || "ALL_MEMBERS",
            sortOrder: item.sortOrder ?? 0,
            itemCount: item.itemCount ?? 0,
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
          }));

          // Attach any mock files associated with this folder for rich document view
          const files = mockFiles.filter((f) => f.folderId === folderId);
          return { folders, files };
        }
      }
    } catch (err) {
      console.warn("Could not load folder children from backend API, using fallback data:", err);
    }
  }

  // Fallback to in-memory/default data
  const folders = mockFolders.filter((f) => f.parentFolderId === folderId);
  const files = mockFiles.filter((f) => f.folderId === folderId);
  return { folders, files };
}

/**
 * 6. CREATE SUB-FOLDER
 * ---------------------------------------------------------------------------
 * Backend Endpoint: POST /api/tenants/{slug}/folders/{folderId}/children
 * Required Permission: FOLDERS_CREATE ("folders.create")
 * Request Body: CreateFolderDTO { name, description }
 *
 * HOW TO MAKE THIS API CALL IN PRODUCTION:
 * ```ts
 * const response = await fetch(
 *   `${API_BASE}/tenants/${encodeURIComponent(slug)}/folders/${parentFolderId}/children`,
 *   {
 *     method: "POST",
 *     headers: {
 *       Authorization: `Bearer ${token}`,
 *       "Content-Type": "application/json",
 *     },
 *     body: JSON.stringify(data),
 *   }
 * );
 * if (!response.ok) {
 *   throw new Error(`Failed to create sub-folder (${response.status})`);
 * }
 * return await response.json();
 * ```
 */
export async function createSubFolder(
  _slug: string,
  parentFolderId: string,
  data: CreateFolderDTO,
  _token?: string | null,
): Promise<FolderItem> {
  // --- PRODUCTION API CALL (Uncomment when connecting to backend) ---
  /*
  const response = await fetch(
    `${API_BASE}/tenants/${encodeURIComponent(_slug)}/folders/${parentFolderId}/children`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${_token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(data),
    }
  );
  if (!response.ok) {
    throw new Error(`Failed to create sub-folder (${response.status})`);
  }
  return await response.json();
  */

  // --- LEARNING FALLBACK (Simulated in-memory) ---
  // Find parent to inherit visibility
  const parentTray = mockTrays.find((t) => t.id === parentFolderId);
  const parentFolder = mockFolders.find((f) => f.id === parentFolderId);
  const inheritedVisibility = parentTray?.visibility || parentFolder?.visibility || "ALL_MEMBERS";

  const newFolder: FolderItem = {
    id: `folder-${Date.now()}`,
    parentFolderId,
    name: data.name.trim(),
    description: data.description?.trim() || null,
    isTray: false,
    isDefault: false,
    visibility: inheritedVisibility,
    itemCount: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };

  mockFolders.push(newFolder);
  return newFolder;
}

/**
 * 7. DELETE SUB-FOLDER
 * ---------------------------------------------------------------------------
 * Backend Endpoint: DELETE /api/tenants/{slug}/folders/{folderId}
 * Required Permission: FOLDERS_DELETE ("folders.delete")
 *
 * HOW TO MAKE THIS API CALL IN PRODUCTION:
 * ```ts
 * const response = await fetch(
 *   `${API_BASE}/tenants/${encodeURIComponent(slug)}/folders/${folderId}`,
 *   {
 *     method: "DELETE",
 *     headers: {
 *       Authorization: `Bearer ${token}`,
 *     },
 *   }
 * );
 * if (!response.ok) {
 *   throw new Error(`Failed to delete folder (${response.status})`);
 * }
 * return true;
 * ```
 */
export async function deleteFolder(
  _slug: string,
  folderId: string,
  _token?: string | null,
): Promise<boolean> {
  // --- PRODUCTION API CALL (Uncomment when connecting to backend) ---
  /*
  const response = await fetch(
    `${API_BASE}/tenants/${encodeURIComponent(_slug)}/folders/${folderId}`,
    {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${_token}`,
      },
    }
  );
  if (!response.ok) {
    throw new Error(`Failed to delete folder (${response.status})`);
  }
  return true;
  */

  // --- LEARNING FALLBACK (Simulated in-memory) ---
  mockFolders = mockFolders.filter((f) => f.id !== folderId && f.parentFolderId !== folderId);
  mockFiles = mockFiles.filter((f) => f.folderId !== folderId);
  return true;
}

/**
 * 8. GET BREADCRUMB ANCESTOR CHAIN
 * ---------------------------------------------------------------------------
 * Backend Endpoint: GET /api/tenants/{slug}/folders/{folderId}/breadcrumb
 * Returns the ancestor chain from the root Tray down to the current folder.
 */
export async function getBreadcrumbs(
  _slug: string,
  currentFolderOrTrayId: string,
  _token?: string | null,
): Promise<BreadcrumbNode[]> {
  // If the target is a Tray:
  const tray = mockTrays.find((t) => t.id === currentFolderOrTrayId);
  if (tray) {
    return [{ id: tray.id, name: tray.name, isTray: true }];
  }

  // If the target is a sub-folder:
  const chain: BreadcrumbNode[] = [];
  let currentId: string | null = currentFolderOrTrayId;

  while (currentId) {
    const folder = mockFolders.find((f) => f.id === currentId);
    if (folder) {
      chain.unshift({ id: folder.id, name: folder.name, isTray: false });
      currentId = folder.parentFolderId;
    } else {
      const parentTray = mockTrays.find((t) => t.id === currentId);
      if (parentTray) {
        chain.unshift({ id: parentTray.id, name: parentTray.name, isTray: true });
      }
      break;
    }
  }

  return chain;
}
