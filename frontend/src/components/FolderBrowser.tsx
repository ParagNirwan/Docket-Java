/**
 * ============================================================================
 * FOLDER BROWSER — The main file/folder browsing area
 * ============================================================================
 *
 * This component shows the contents of a tray or folder: its sub-folders
 * and documents. It supports:
 *  - Clicking into sub-folders (with breadcrumb navigation)
 *  - Uploading files via the Upload button or drag-and-drop
 *  - Showing upload progress for all files being uploaded
 *  - Creating and deleting sub-folders
 *  - A search bar to filter the displayed items
 *  - Warning the user if they try to close the tab during an upload
 * ============================================================================
 */

import { useState, useEffect, useRef, useCallback } from "react";
import {
  Folder as FolderIcon,
  FolderPlus,
  FileText,
  ChevronRight,
  Shield,
  Trash2,
  Users,
  Search,
  Upload,
  Layers,
  MoreVertical,
} from "lucide-react";
import { BreadcrumbNav } from "./BreadcrumbNav";
import { CreateFolderModal } from "./CreateFolderModal";
import { DeleteConfirmationModal } from "./DeleteConfirmationModal";
import { UploadProgressPanel } from "./UploadProgressPanel";
import type {
  Tray,
  FolderItem,
  BreadcrumbNode,
  CreateFolderDTO,
} from "../services/trayService";
import {
  getFolderChildren,
  createSubFolder,
  deleteFolder,
  getBreadcrumbs,
} from "../services/trayService";
import {
  UploadManager,
  getDocuments,
  formatFileSize,
  type UploadTask,
  type DocumentDTO,
} from "../services/documentService";

interface FolderBrowserProps {
  slug: string;
  token?: string | null;
  activeTray: Tray;
  canCreateFolder: boolean;
  canDeleteFolder: boolean;
  canDeleteTray: boolean;
  canUpload: boolean;
  onDeleteTray?: (trayId: string) => void;
}

export function FolderBrowser({
  slug,
  token,
  activeTray,
  canCreateFolder,
  canDeleteFolder,
  canDeleteTray,
  canUpload,
  onDeleteTray,
}: FolderBrowserProps) {
  // ── State ───────────────────────────────────────────────────────────

  // Current folder being viewed (starts at the tray root)
  const [currentFolderId, setCurrentFolderId] = useState<string>(activeTray.id);

  // The breadcrumb trail showing where we are in the folder hierarchy
  const [breadcrumbs, setBreadcrumbs] = useState<BreadcrumbNode[]>([
    { id: activeTray.id, name: activeTray.name, isTray: true },
  ]);

  // Sub-folders inside the current folder
  const [folders, setFolders] = useState<FolderItem[]>([]);

  // Documents (files) inside the current folder — loaded from the API
  const [documents, setDocuments] = useState<DocumentDTO[]>([]);

  // Loading state while we fetch folder contents
  const [loading, setLoading] = useState<boolean>(true);

  // Search bar text
  const [searchQuery, setSearchQuery] = useState("");

  // Modal for creating new sub-folders
  const [isFolderModalOpen, setIsFolderModalOpen] = useState(false);

  // Dropdown menu tracking (for delete options)
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);

  // Delete confirmation state
  const [pendingDelete, setPendingDelete] = useState<{
    id: string;
    name: string;
    type: "tray" | "folder";
  } | null>(null);

  // Upload state — the list of files being uploaded and their progress
  const [uploadTasks, setUploadTasks] = useState<UploadTask[]>([]);

  // Whether the user is dragging a file over the drop zone
  const [isDragging, setIsDragging] = useState(false);

  // A reference to the hidden file input element (for the Upload button)
  const fileInputRef = useRef<HTMLInputElement>(null);

  // A reference to the UploadManager that handles the upload queue
  const uploadManagerRef = useRef<UploadManager | null>(null);

  // ── Upload Manager Setup ──────────────────────────────────────────

  // Create the upload manager once (or recreate it if the slug/token changes).
  // The manager lives across re-renders so uploads don't get interrupted.
  useEffect(() => {
    if (token) {
      uploadManagerRef.current = new UploadManager(
        slug,
        token,
        (tasks) => setUploadTasks(tasks),
      );
    }
  }, [slug, token]);

  // ── Warn Before Closing Tab During Upload ─────────────────────────

  // If the user tries to close the browser tab or navigate away while
  // files are still uploading, show a warning dialog.
  useEffect(() => {
    function handleBeforeUnload(e: BeforeUnloadEvent) {
      if (uploadManagerRef.current?.hasActiveUploads()) {
        e.preventDefault();
      }
    }
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, []);

  // ── Close Dropdown When Clicking Outside ──────────────────────────

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (!(e.target as HTMLElement).closest(".dropdown-menu-container")) {
        setActiveMenuId(null);
      }
    }
    if (activeMenuId) {
      document.addEventListener("click", handleClickOutside);
      return () => document.removeEventListener("click", handleClickOutside);
    }
  }, [activeMenuId]);

  // ── Load Folder Contents ──────────────────────────────────────────

  // Whenever we navigate to a different folder, load its sub-folders
  // and documents from the server.
  useEffect(() => {
    let isCancelled = false;

    async function loadContents() {
      try {
        setLoading(true);

        // Check if this is a real UUID (from the database) or a mock ID
        const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(currentFolderId);

        // Load sub-folders and breadcrumbs (from trayService)
        const [childrenResult, breadcrumbsResult] = await Promise.all([
          getFolderChildren(slug, currentFolderId, token),
          getBreadcrumbs(slug, currentFolderId, token),
        ]);

        // Load documents from the document API (only if we have a real folder UUID)
        let loadedDocuments: DocumentDTO[] = [];
        if (token && isUuid) {
          try {
            const page = await getDocuments(slug, currentFolderId, token);
            loadedDocuments = page.content;
          } catch {
            // If document loading fails, we still show the folders
            console.warn("Could not load documents for this folder");
          }
        }

        if (!isCancelled) {
          setFolders(childrenResult.folders);
          setDocuments(loadedDocuments);
          setBreadcrumbs(breadcrumbsResult);
        }
      } catch (err) {
        console.error("Failed to load folder children", err);
      } finally {
        if (!isCancelled) {
          setLoading(false);
        }
      }
    }

    loadContents();
    return () => {
      isCancelled = true;
    };
  }, [slug, currentFolderId, token, activeTray.id]);

  // ── Reload documents after uploads finish ─────────────────────────

  // When all uploads in the current batch finish, refresh the document
  // list so newly uploaded files appear immediately.
  const prevActiveRef = useRef(false);
  useEffect(() => {
    const hasActive = uploadTasks.some(
      (t) => t.status === "uploading" || t.status === "queued"
    );
    const hadActive = prevActiveRef.current;
    prevActiveRef.current = hasActive;

    // If uploads just finished (were active, now aren't), reload documents
    if (hadActive && !hasActive && token) {
      const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(currentFolderId);
      if (isUuid) {
        getDocuments(slug, currentFolderId, token).then((page) => {
          setDocuments(page.content);
        }).catch(() => {});
      }
    }
  }, [uploadTasks, slug, currentFolderId, token]);

  // ── Event Handlers ────────────────────────────────────────────────

  // Navigate into a sub-folder when clicked
  function handleFolderClick(folder: FolderItem) {
    setCurrentFolderId(folder.id);
  }

  // Navigate via breadcrumb trail
  function handleBreadcrumbNavigate(node: BreadcrumbNode) {
    setCurrentFolderId(node.id);
  }

  // Create a new sub-folder inside the current folder
  async function handleCreateFolderSubmit(data: CreateFolderDTO) {
    const newFolder = await createSubFolder(slug, currentFolderId, data, token);
    setFolders((prev) => [...prev, newFolder]);
  }

  // Handle the delete confirmation
  async function handleConfirmDelete() {
    if (!pendingDelete) return;

    if (pendingDelete.type === "tray") {
      if (onDeleteTray) {
        onDeleteTray(pendingDelete.id);
      }
    } else {
      await deleteFolder(slug, pendingDelete.id, token);
      setFolders((prev) => prev.filter((f) => f.id !== pendingDelete.id));

      if (pendingDelete.id === currentFolderId) {
        const parentNode = breadcrumbs[breadcrumbs.length - 2] || breadcrumbs[0];
        setCurrentFolderId(parentNode.id);
      }
    }
  }

  // ── Upload Handlers ───────────────────────────────────────────────

  // Called when the user picks files via the file picker
  function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    uploadManagerRef.current?.addFiles(Array.from(files), currentFolderId);

    // Reset the file input so the user can pick the same file again
    e.target.value = "";
  }

  // ── Drag and Drop ─────────────────────────────────────────────────

  // These four handlers work together to create the drag-and-drop experience.
  // When a user drags files over the browser area, we show a visual highlight.
  // When they drop the files, we start uploading them.

  // Counter tracks nested drag enter/leave events (children trigger them too)
  const dragCounterRef = useRef(0);

  const handleDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current++;
    if (e.dataTransfer.types.includes("Files")) {
      setIsDragging(true);
    }
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounterRef.current--;
    if (dragCounterRef.current === 0) {
      setIsDragging(false);
    }
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragging(false);
      dragCounterRef.current = 0;

      const files = e.dataTransfer.files;
      if (files.length > 0 && canUpload) {
        uploadManagerRef.current?.addFiles(Array.from(files), currentFolderId);
      }
    },
    [canUpload, currentFolderId],
  );

  // ── Filtered Items ────────────────────────────────────────────────

  // Filter folders and documents by the search query
  const filteredFolders = folders.filter((f) =>
    f.name.toLowerCase().includes(searchQuery.toLowerCase())
  );
  const filteredDocuments = documents.filter((d) =>
    d.originalFileName.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // ── Derived State ─────────────────────────────────────────────────

  const isTrash = activeTray.id === "trash" || activeTray.name.toLowerCase() === "trash";
  const isAtRoot = currentFolderId === activeTray.id;
  const currentFolderName = breadcrumbs[breadcrumbs.length - 1]?.name || activeTray.name;

  // ── Render ────────────────────────────────────────────────────────

  return (
    <div
      className="space-y-6"
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      {/* Hidden file input — triggered by the Upload button */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={handleFileSelect}
        accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.png,.jpg,.jpeg,.gif,.svg,.webp,.zip,.rar,.7z"
      />

      {/* ── Drag & Drop Overlay ──────────────────────────────── */}
      {/* Shows a big highlighted area when the user drags files over */}
      {isDragging && canUpload && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-indigo-600/10 backdrop-blur-sm">
          <div className="rounded-2xl border-2 border-dashed border-indigo-400 bg-white/90 px-12 py-10 text-center shadow-2xl dark:bg-gray-900/90">
            <Upload className="mx-auto mb-3 h-12 w-12 text-indigo-600" />
            <p className="text-lg font-semibold text-gray-900 dark:text-white">
              Drop files to upload
            </p>
            <p className="mt-1 text-sm text-gray-500">
              Files will be uploaded to "{currentFolderName}"
            </p>
          </div>
        </div>
      )}

      {/* ── Top Header Card ──────────────────────────────────── */}
      <section className="overflow-hidden rounded-2xl border border-gray-200 bg-white p-6 shadow-xs dark:border-gray-800 dark:bg-gray-900">
        {/* Breadcrumb navigation bar */}
        <div className="mb-4 pb-3 border-b border-gray-100 dark:border-gray-800">
          <BreadcrumbNav
            items={breadcrumbs}
            onNavigate={handleBreadcrumbNavigate}
          />
        </div>

        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-4">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
              {isAtRoot ? (
                <Layers className="h-6 w-6" />
              ) : (
                <FolderIcon className="h-6 w-6" />
              )}
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h2 className="text-xl font-bold tracking-tight text-gray-900 dark:text-white">
                  {currentFolderName}
                </h2>
                {activeTray.isDefault ? (
                  <span className="rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-[10px] font-semibold text-gray-600 dark:text-gray-300">
                    Default Tray
                  </span>
                ) : (
                  <span className="rounded-full bg-indigo-50 dark:bg-indigo-950/50 px-2 py-0.5 text-[10px] font-semibold text-indigo-600 dark:text-indigo-300">
                    Custom Tray
                  </span>
                )}
                {activeTray.visibility === "ADMIN_ONLY" ? (
                  <span className="flex items-center gap-1 rounded-full bg-amber-50 dark:bg-amber-950/40 px-2 py-0.5 text-[10px] font-semibold text-amber-700 dark:text-amber-300">
                    <Shield className="h-3 w-3" /> Admin Only
                  </span>
                ) : (
                  <span className="flex items-center gap-1 rounded-full bg-emerald-50 dark:bg-emerald-950/40 px-2 py-0.5 text-[10px] font-semibold text-emerald-700 dark:text-emerald-300">
                    <Users className="h-3 w-3" /> All Members
                  </span>
                )}
              </div>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {isAtRoot
                  ? activeTray.description || "Top-level tray for document organization."
                  : `Folder inside ${activeTray.name} tray`}
              </p>
            </div>
          </div>

          {/* ── Action Buttons ────────────────────────────────── */}
          {/* Hidden in the Trash tray (you can't upload to trash) */}
          {!isTrash && (
            <div className="flex items-center flex-wrap gap-2.5">
              {/* New Folder button */}
              {canCreateFolder && (
                <button
                  type="button"
                  onClick={() => setIsFolderModalOpen(true)}
                  className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-3.5 py-2 text-xs font-semibold text-white shadow-xs hover:bg-indigo-700 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
                >
                  <FolderPlus className="h-4 w-4" />
                  <span>New Folder</span>
                </button>
              )}

              {/* Upload button — clicking it opens the hidden file picker */}
              {canUpload && (
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-xs font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors"
                >
                  <Upload className="h-3.5 w-3.5" />
                  <span>Upload</span>
                </button>
              )}

              {/* Tray options menu (delete custom tray) */}
              {isAtRoot && !activeTray.isDefault && canDeleteTray && onDeleteTray && (
                <div className="dropdown-menu-container relative">
                  <button
                    type="button"
                    onClick={() =>
                      setActiveMenuId(activeMenuId === "tray-options" ? null : "tray-options")
                    }
                    className="inline-flex items-center gap-1.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors"
                    title="More tray options"
                    aria-label="More tray options"
                  >
                    <MoreVertical className="h-3.5 w-3.5" />
                    <span>Options</span>
                  </button>
                  {activeMenuId === "tray-options" && (
                    <div className="absolute right-0 top-full mt-1.5 w-48 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-xl p-1 z-30">
                      <button
                        type="button"
                        onClick={() => {
                          setActiveMenuId(null);
                          setPendingDelete({
                            id: activeTray.id,
                            name: activeTray.name,
                            type: "tray",
                          });
                        }}
                        className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-xs font-medium text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                        <span>Delete Custom Tray</span>
                      </button>
                    </div>
                  )}
                </div>
              )}

              {/* Folder options menu (delete current folder) */}
              {!isAtRoot && canDeleteFolder && (
                <div className="dropdown-menu-container relative">
                  <button
                    type="button"
                    onClick={() =>
                      setActiveMenuId(
                        activeMenuId === "folder-header-options" ? null : "folder-header-options"
                      )
                    }
                    className="inline-flex items-center gap-1.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors"
                    title="Folder options"
                    aria-label="Folder options"
                  >
                    <MoreVertical className="h-3.5 w-3.5" />
                    <span>Options</span>
                  </button>
                  {activeMenuId === "folder-header-options" && (
                    <div className="absolute right-0 top-full mt-1.5 w-44 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-xl p-1 z-30">
                      <button
                        type="button"
                        onClick={() => {
                          setActiveMenuId(null);
                          setPendingDelete({
                            id: currentFolderId,
                            name: currentFolderName,
                            type: "folder",
                          });
                        }}
                        className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-xs font-medium text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                        <span>Delete this folder</span>
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Search bar */}
        <div className="mt-5 flex items-center gap-2 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/60 dark:bg-gray-800/40 px-3 py-2 text-xs text-gray-500 dark:text-gray-400">
          <Search className="h-4 w-4 text-gray-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search folders and documents..."
            className="w-full bg-transparent border-none outline-none text-xs text-gray-900 dark:text-white placeholder-gray-400"
          />
        </div>
      </section>

      {/* ── Main Content: Folders and Documents ──────────────── */}
      {loading ? (
        <div className="flex min-h-[200px] items-center justify-center rounded-2xl border border-gray-200 bg-white p-8 text-sm text-gray-500 dark:border-gray-800 dark:bg-gray-900">
          <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mr-3" />
          Loading contents...
        </div>
      ) : filteredFolders.length === 0 && filteredDocuments.length === 0 ? (
        /* Empty state — shown when a folder has no sub-folders or documents */
        <section className="flex min-h-[300px] flex-col items-center justify-center rounded-2xl border border-dashed border-gray-300 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-900">
          <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
            <FolderIcon className="h-7 w-7" strokeWidth={1.5} />
          </div>
          <h3 className="text-base font-semibold text-gray-900 dark:text-white">
            This folder is empty
          </h3>
          <p className="mt-1 max-w-sm text-xs text-gray-500 dark:text-gray-400">
            {canUpload
              ? "Drag and drop files here, or use the Upload button above."
              : "Organize documents into sub-folders or upload new files here."}
          </p>
          <div className="mt-5 flex items-center gap-3">
            {canCreateFolder && !isTrash && (
              <button
                type="button"
                onClick={() => setIsFolderModalOpen(true)}
                className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-indigo-700 transition-colors"
              >
                <FolderPlus className="h-4 w-4" />
                <span>Create First Sub-folder</span>
              </button>
            )}
            {canUpload && !isTrash && (
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="inline-flex items-center gap-2 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-2 text-xs font-semibold text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors"
              >
                <Upload className="h-4 w-4" />
                <span>Upload Files</span>
              </button>
            )}
          </div>
        </section>
      ) : (
        <div className="space-y-6">
          {/* Sub-folders list */}
          {filteredFolders.length > 0 && (
            <section>
              <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                Folders ({filteredFolders.length})
              </h3>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {filteredFolders.map((folder) => (
                  <div
                    key={folder.id}
                    className="group flex items-center justify-between rounded-xl border border-gray-200 bg-white p-3.5 shadow-xs hover:border-indigo-300 hover:shadow-sm dark:border-gray-800 dark:bg-gray-900 dark:hover:border-indigo-800 transition-all cursor-pointer relative"
                    onClick={() => handleFolderClick(folder)}
                  >
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400 group-hover:scale-105 transition-transform">
                        <FolderIcon className="h-5 w-5" />
                      </div>
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                          {folder.name}
                        </p>
                        <p className="text-[11px] text-gray-400 dark:text-gray-500 truncate">
                          {folder.description || "Sub-folder"}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-1">
                      {canDeleteFolder && (
                        <div
                          className="dropdown-menu-container relative"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <button
                            type="button"
                            onClick={() =>
                              setActiveMenuId(
                                activeMenuId === `folder-${folder.id}`
                                  ? null
                                  : `folder-${folder.id}`
                              )
                            }
                            className="p-1.5 rounded-lg text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                            title="Folder options"
                            aria-label={`Options for ${folder.name}`}
                          >
                            <MoreVertical className="h-4 w-4" />
                          </button>
                          {activeMenuId === `folder-${folder.id}` && (
                            <div className="absolute right-0 top-full mt-1 w-36 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-xl p-1 z-30">
                              <button
                                type="button"
                                onClick={() => {
                                  setActiveMenuId(null);
                                  setPendingDelete({
                                    id: folder.id,
                                    name: folder.name,
                                    type: "folder",
                                  });
                                }}
                                className="flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                                <span>Delete folder</span>
                              </button>
                            </div>
                          )}
                        </div>
                      )}
                      <ChevronRight className="h-4 w-4 text-gray-400 group-hover:translate-x-0.5 group-hover:text-indigo-600 transition-all" />
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* Documents list — now showing real data from the API */}
          {filteredDocuments.length > 0 && (
            <section className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-xs dark:border-gray-800 dark:bg-gray-900">
              <div className="border-b border-gray-100 px-5 py-3.5 dark:border-gray-800">
                <h3 className="text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  Documents ({filteredDocuments.length})
                </h3>
              </div>
              <div className="divide-y divide-gray-100 dark:divide-gray-800">
                {filteredDocuments.map((doc) => (
                  <div
                    key={doc.id}
                    className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 px-5 py-3.5 hover:bg-gray-50/80 dark:hover:bg-gray-800/40 transition-colors"
                  >
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
                        <FileText className="h-4 w-4" />
                      </div>
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                          {doc.originalFileName}
                        </p>
                        <p className="text-[11px] text-gray-400 dark:text-gray-500">
                          {doc.contentType} · {formatFileSize(doc.fileSize)}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-4 pl-12 sm:pl-0">
                      <span className="text-xs text-gray-400 dark:text-gray-500">
                        {new Date(doc.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      {/* ── Upload Progress Panel ────────────────────────────── */}
      {/* Floating panel at bottom-right showing upload status */}
      <UploadProgressPanel
        tasks={uploadTasks}
        onCancel={(id) => uploadManagerRef.current?.cancelTask(id)}
        onCancelAll={() => uploadManagerRef.current?.cancelAll()}
        onRetry={(id) => uploadManagerRef.current?.retryTask(id)}
        onClearFinished={() => uploadManagerRef.current?.clearFinished()}
      />

      {/* ── Modals ───────────────────────────────────────────── */}
      <CreateFolderModal
        isOpen={isFolderModalOpen}
        parentName={currentFolderName}
        onClose={() => setIsFolderModalOpen(false)}
        onSubmit={handleCreateFolderSubmit}
      />

      <DeleteConfirmationModal
        isOpen={pendingDelete !== null}
        itemName={pendingDelete?.name || ""}
        itemType={pendingDelete?.type || "folder"}
        onClose={() => setPendingDelete(null)}
        onConfirm={handleConfirmDelete}
      />
    </div>
  );
}
