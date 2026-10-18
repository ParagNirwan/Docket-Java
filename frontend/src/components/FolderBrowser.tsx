import { useState, useEffect } from "react";
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
import type {
  Tray,
  FolderItem,
  FileItem,
  BreadcrumbNode,
  CreateFolderDTO,
} from "../services/trayService";
import {
  getFolderChildren,
  createSubFolder,
  deleteFolder,
  getBreadcrumbs,
} from "../services/trayService";

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
  // Current active folder id. At start, it's the tray's id (root).
  const [currentFolderId, setCurrentFolderId] = useState<string>(activeTray.id);
  const [breadcrumbs, setBreadcrumbs] = useState<BreadcrumbNode[]>([
    { id: activeTray.id, name: activeTray.name, isTray: true },
  ]);
  const [folders, setFolders] = useState<FolderItem[]>([]);
  const [files, setFiles] = useState<FileItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [isFolderModalOpen, setIsFolderModalOpen] = useState(false);

  // Protected deletion state
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<{
    id: string;
    name: string;
    type: "tray" | "folder";
  } | null>(null);

  // Close dropdown menu when clicking outside
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

  // Load children whenever currentFolderId or slug changes
  useEffect(() => {
    let isCancelled = false;

    async function loadContents() {
      try {
        setLoading(true);

        /**
         * 💡 LEARNING NOTE: API Call to fetch children
         * Backend: GET /api/tenants/{slug}/folders/{folderId}/children
         * Controller: FolderController.java -> getChildren()
         * Permission: DOCUMENTS_READ ("documents.read")
         */
        const [childrenResult, breadcrumbsResult] = await Promise.all([
          getFolderChildren(slug, currentFolderId, token),
          getBreadcrumbs(slug, currentFolderId, token),
        ]);

        if (!isCancelled) {
          setFolders(childrenResult.folders);
          setFiles(childrenResult.files);
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

  // Handle navigating into a sub-folder
  function handleFolderClick(folder: FolderItem) {
    setCurrentFolderId(folder.id);
  }

  // Handle navigating via breadcrumbs
  function handleBreadcrumbNavigate(node: BreadcrumbNode) {
    setCurrentFolderId(node.id);
  }

  // Handle creating a sub-folder
  async function handleCreateFolderSubmit(data: CreateFolderDTO) {
    /**
     * 💡 LEARNING NOTE: API Call to create subfolder
     * Backend: POST /api/tenants/{slug}/folders/{parentFolderId}/children
     * Permission: FOLDERS_CREATE ("folders.create")
     * Request body: { name, description }
     */
    const newFolder = await createSubFolder(slug, currentFolderId, data, token);
    setFolders((prev) => [...prev, newFolder]);
  }

  // Handle confirmed deletion from modal
  async function handleConfirmDelete() {
    if (!pendingDelete) return;

    if (pendingDelete.type === "tray") {
      if (onDeleteTray) {
        onDeleteTray(pendingDelete.id);
      }
    } else {
      // Deleting a folder
      await deleteFolder(slug, pendingDelete.id, token);
      setFolders((prev) => prev.filter((f) => f.id !== pendingDelete.id));

      // If the deleted folder is the one currently opened, navigate up to parent
      if (pendingDelete.id === currentFolderId) {
        const parentNode = breadcrumbs[breadcrumbs.length - 2] || breadcrumbs[0];
        setCurrentFolderId(parentNode.id);
      }
    }
  }

  // Filtered by search
  const filteredFolders = folders.filter((f) =>
    f.name.toLowerCase().includes(searchQuery.toLowerCase())
  );
  const filteredFiles = files.filter((f) =>
    f.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const isTrash = activeTray.id === "trash" || activeTray.name.toLowerCase() === "trash";
  const isAtRoot = currentFolderId === activeTray.id;
  const currentFolderName = breadcrumbs[breadcrumbs.length - 1]?.name || activeTray.name;

  return (
    <div className="space-y-6">
      {/* Top Header Card */}
      <section className="overflow-hidden rounded-2xl border border-gray-200 bg-white p-6 shadow-xs dark:border-gray-800 dark:bg-gray-900">
        {/* Breadcrumb Bar */}
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

          {/* Action buttons — hidden entirely in Trash tray */}
          {!isTrash && (
            <div className="flex items-center flex-wrap gap-2.5">
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

              {canUpload && (
                <button
                  type="button"
                  className="inline-flex items-center gap-1.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2 text-xs font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors"
                >
                  <Upload className="h-3.5 w-3.5" />
                  <span>Upload</span>
                </button>
              )}

              {/* Custom Tray Protected Options Menu */}
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

              {/* Sub-folder Protected Options Menu */}
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

        {/* Search bar inside browser */}
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

      {/* Main Content: Folders and Files */}
      {loading ? (
        <div className="flex min-h-[200px] items-center justify-center rounded-2xl border border-gray-200 bg-white p-8 text-sm text-gray-500 dark:border-gray-800 dark:bg-gray-900">
          <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mr-3" />
          Loading contents...
        </div>
      ) : filteredFolders.length === 0 && filteredFiles.length === 0 ? (
        /* Empty State */
        <section className="flex min-h-[300px] flex-col items-center justify-center rounded-2xl border border-dashed border-gray-300 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-900">
          <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
            <FolderIcon className="h-7 w-7" strokeWidth={1.5} />
          </div>
          <h3 className="text-base font-semibold text-gray-900 dark:text-white">
            This folder is empty
          </h3>
          <p className="mt-1 max-w-sm text-xs text-gray-500 dark:text-gray-400">
            Organize documents into sub-folders or upload new files here.
          </p>
          {canCreateFolder && !isTrash && (
            <button
              type="button"
              onClick={() => setIsFolderModalOpen(true)}
              className="mt-5 inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-indigo-700 transition-colors"
            >
              <FolderPlus className="h-4 w-4" />
              <span>Create First Sub-folder</span>
            </button>
          )}
        </section>
      ) : (
        <div className="space-y-6">
          {/* Sub-folders Section */}
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
                      {/* Protected 3-dot options menu */}
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

          {/* Files Section */}
          {filteredFiles.length > 0 && (
            <section className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-xs dark:border-gray-800 dark:bg-gray-900">
              <div className="border-b border-gray-100 px-5 py-3.5 dark:border-gray-800">
                <h3 className="text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  Documents ({filteredFiles.length})
                </h3>
              </div>
              <div className="divide-y divide-gray-100 dark:divide-gray-800">
                {filteredFiles.map((file) => (
                  <div
                    key={file.id}
                    className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 px-5 py-3.5 hover:bg-gray-50/80 dark:hover:bg-gray-800/40 transition-colors"
                  >
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
                        <FileText className="h-4 w-4" />
                      </div>
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                          {file.name}
                        </p>
                        <p className="text-[11px] text-gray-400 dark:text-gray-500">
                          {file.detail} {file.size ? `· ${file.size}` : ""}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-4 pl-12 sm:pl-0">
                      {file.status && (
                        <span
                          className={`rounded-full px-2.5 py-0.5 text-[10px] font-semibold ${
                            file.status === "Needs review"
                              ? "bg-amber-50 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300"
                              : "bg-blue-50 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300"
                          }`}
                        >
                          {file.status}
                        </span>
                      )}
                      <span className="text-xs text-gray-400 dark:text-gray-500">
                        {file.date}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      {/* Create Folder Modal */}
      <CreateFolderModal
        isOpen={isFolderModalOpen}
        parentName={currentFolderName}
        onClose={() => setIsFolderModalOpen(false)}
        onSubmit={handleCreateFolderSubmit}
      />

      {/* Proper Delete Confirmation Modal (Replaces native browser alertbox) */}
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
