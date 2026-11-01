/**
 * ============================================================================
 * FOLDER BROWSER — The main file/folder browsing area
 * ============================================================================
 *
 * This component shows the contents of a tray or folder: its sub-folders
 * and documents. It supports:
 *  - Clicking into sub-folders (with breadcrumb navigation)
 *  - Switching between List, Thumbnail, and Cards views
 *  - Uploading files via the Upload button or drag-and-drop
 *  - Showing upload progress for all files being uploaded
 *  - Creating and deleting sub-folders and documents
 *  - Downloading documents directly
 *  - A search bar to filter the displayed items
 *  - Warning the user if they try to close the tab during an upload
 * ============================================================================
 */

import { useState, useEffect, useRef, useCallback } from "react";
import {
  Folder as FolderIcon,
  FolderPlus,
  ChevronLeft,
  ChevronRight,
  Shield,
  Trash2,
  Users,
  Search,
  Upload,
  Layers,
  MoreVertical,
  Download,
  Calendar,
  User,
  Sparkles,
  Pencil,
  FolderInput,
  Copy,
  Archive,
  ArchiveRestore,
  Gavel,
  ShieldAlert,
} from "lucide-react";
import { BreadcrumbNav } from "./BreadcrumbNav";
import { CreateFolderModal } from "./CreateFolderModal";
import { DeleteConfirmationModal } from "./DeleteConfirmationModal";
import { FolderPickerModal } from "./FolderPickerModal";
import { ArchiveModal, LegalHoldModal, OverrideLockModal } from "./DocumentLockModals";
import { ItemActionsMenu, type ItemAction } from "./ItemActionsMenu";
import { LockBadge } from "./LockBadge";
import { RenameModal } from "./RenameModal";
import { UploadProgressPanel } from "./UploadProgressPanel";
import { ViewModeSwitcher, type ViewMode } from "./ViewModeSwitcher";
import { DocumentThumbnail } from "./DocumentThumbnail";
import { FolderThumbnail } from "./FolderThumbnail";
import { AiStatusBadge } from "./AiStatusBadge";
import { getFileTypeInfo } from "../lib/fileTypeUtils";
import { notify } from "../lib/notifications";
import { canBeOverridden, isLocked, isReadOnly, lockDescription } from "../lib/retention";
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
  updateFolder,
} from "../services/trayService";
import {
  UploadManager,
  getDocuments,
  downloadDocument,
  deleteDocument,
  updateDocument,
  copyDocument,
  formatFileSize,
  type UploadTask,
  type DocumentDTO,
} from "../services/documentService";
import { archiveDocument, createLegalHold, unarchiveDocument } from "../services/retentionService";
import { useAuth } from "../context/AuthContext";

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function getUploaderDisplayName(doc: DocumentDTO, currentUserEmail?: string): string {
  if (doc.uploadedByName && doc.uploadedByName.trim() && !UUID_REGEX.test(doc.uploadedByName.trim())) {
    return doc.uploadedByName.trim();
  }
  if (doc.uploadedBy && !UUID_REGEX.test(doc.uploadedBy.trim())) {
    return doc.uploadedBy.trim();
  }
  if (currentUserEmail) {
    const prefix = currentUserEmail.split("@")[0];
    return prefix.charAt(0).toUpperCase() + prefix.slice(1);
  }
  return "Member";
}

interface FolderBrowserProps {
  slug: string;
  token?: string | null;
  activeTray: Tray;
  canCreateFolder: boolean;
  canDeleteFolder: boolean;
  canDeleteTray: boolean;
  canUpload: boolean;
  canUpdateDocuments?: boolean;
  canCopyDocuments?: boolean;
  canUpdateFolders?: boolean;
  // Falls back to canDeleteFolder for callers that don't separate the two.
  canDeleteDocuments?: boolean;
  canOverrideLocks?: boolean;
  canApplyLegalHold?: boolean;
  // Where things can be moved or copied to
  trays?: Tray[];
  // Open a folder inside the tray straight away, such as from a search result
  initialFolderId?: string;
  onDeleteTray?: (trayId: string) => void;
  onAskAi?: (docId: string, docName: string) => void;
}

// A folder or document that's being renamed, moved, copied or dragged.
interface ItemRef {
  type: "folder" | "document";
  id: string;
  name: string;
}

// Marks a drag as one of ours, as opposed to files dragged in from the computer.
const ITEM_DRAG_TYPE = "application/x-docket-item";

export function FolderBrowser({
  slug,
  token,
  activeTray,
  canCreateFolder,
  canDeleteFolder,
  canDeleteTray,
  canUpload,
  canUpdateDocuments = false,
  canCopyDocuments = false,
  canUpdateFolders = false,
  canDeleteDocuments = canDeleteFolder,
  canOverrideLocks = false,
  canApplyLegalHold = false,
  trays = [],
  initialFolderId,
  onDeleteTray,
  onAskAi,
}: FolderBrowserProps) {
  const { user } = useAuth();

  // ── State ───────────────────────────────────────────────────────────

  // Current folder being viewed (starts at the tray root)
  const [currentFolderId, setCurrentFolderId] = useState<string>(initialFolderId ?? activeTray.id);

  // The breadcrumb trail showing where we are in the folder hierarchy
  const [breadcrumbs, setBreadcrumbs] = useState<BreadcrumbNode[]>([
    { id: activeTray.id, name: activeTray.name, isTray: true },
  ]);

  const currentFolderName = breadcrumbs[breadcrumbs.length - 1]?.name || activeTray.name;

  // Sub-folders inside the current folder
  const [folders, setFolders] = useState<FolderItem[]>([]);

  // Documents (files) inside the current folder — loaded from the API
  const [documents, setDocuments] = useState<DocumentDTO[]>([]);

  // ── Pagination State ────────────────────────────────────────────────
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [pageSize, setPageSize] = useState<number>(50);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);

  // Loading state while we fetch folder contents
  const [loading, setLoading] = useState<boolean>(true);

  // Search bar text
  const [searchQuery, setSearchQuery] = useState("");

  // View mode: list, thumbnail, or cards view (persists across sessions)
  const [viewMode, setViewMode] = useState<ViewMode>(() => {
    try {
      const saved = localStorage.getItem("docket_folder_view_mode");
      if (saved === "list" || saved === "thumbnail" || saved === "cards") {
        return saved;
      }
    } catch {
      // Ignore localStorage issues
    }
    return "cards";
  });

  function handleViewModeChange(mode: ViewMode) {
    setViewMode(mode);
    try {
      localStorage.setItem("docket_folder_view_mode", mode);
    } catch {
      // Ignore localStorage errors
    }
  }

  // Modal for creating new sub-folders
  const [isFolderModalOpen, setIsFolderModalOpen] = useState(false);

  // Dropdown menu tracking (for options)
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);

  // Delete confirmation state
  const [pendingDelete, setPendingDelete] = useState<{
    id: string;
    name: string;
    type: "tray" | "folder" | "document";
  } | null>(null);

  // What's being renamed, or moved or copied with the folder picker
  const [renaming, setRenaming] = useState<ItemRef | null>(null);
  const [picking, setPicking] = useState<{ mode: "move" | "copy"; item: ItemRef } | null>(null);

  // A document being archived, or deleted past a Governance lock
  const [archiving, setArchiving] = useState<DocumentDTO | null>(null);
  const [overriding, setOverriding] = useState<DocumentDTO | null>(null);
  const [holding, setHolding] = useState<ItemRef | null>(null);

  // The folder an item is currently being dragged over
  const [dropTargetId, setDropTargetId] = useState<string | null>(null);

  // Upload state — the list of files being uploaded and their progress
  const [uploadTasks, setUploadTasks] = useState<UploadTask[]>([]);

  // Whether the user is dragging a file over the drop zone
  const [isDragging, setIsDragging] = useState(false);

  // A reference to the hidden file input element (for the Upload button)
  const fileInputRef = useRef<HTMLInputElement>(null);

  // A reference to the UploadManager that handles the upload queue
  const uploadManagerRef = useRef<UploadManager | null>(null);

  // ── Upload Manager Setup ──────────────────────────────────────────

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

  useEffect(() => {
    let isCancelled = false;

    async function loadContents() {
      try {
        setLoading(true);

        const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(currentFolderId);

        // Load sub-folders and breadcrumbs
        const [childrenResult, breadcrumbsResult] = await Promise.all([
          getFolderChildren(slug, currentFolderId, token),
          getBreadcrumbs(slug, currentFolderId, token),
        ]);

        // Load documents from the document API (if UUID)
        let loadedDocuments: DocumentDTO[] = [];
        let totalCount = 0;
        let pagesCount = 1;

        if (token && isUuid) {
          try {
            const pageData = await getDocuments(slug, currentFolderId, token, currentPage, pageSize);
            loadedDocuments = pageData.content;
            totalCount = pageData.totalElements;
            pagesCount = pageData.totalPages;
          } catch {
            console.warn("Could not load documents for this folder");
          }
        }

        // Fallback for mock/offline items
        if (loadedDocuments.length === 0 && childrenResult.files && childrenResult.files.length > 0) {
          const allMockDocs: DocumentDTO[] = childrenResult.files.map((f) => ({
            id: f.id,
            originalFileName: f.name,
            contentType: f.detail?.toLowerCase().includes("pdf")
              ? "application/pdf"
              : f.detail?.toLowerCase().includes("doc")
              ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              : "application/octet-stream",
            uploadedBy: "Admin",
            uploadedByName: "Admin",
            fileSize: f.size?.includes("MB")
              ? Math.round(parseFloat(f.size) * 1024 * 1024)
              : Math.round(parseFloat(f.size || "500") * 1024),
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }));

          totalCount = allMockDocs.length;
          pagesCount = Math.max(1, Math.ceil(totalCount / pageSize));
          const start = currentPage * pageSize;
          loadedDocuments = allMockDocs.slice(start, start + pageSize);
        }

        if (!isCancelled) {
          setFolders(childrenResult.folders);
          setDocuments(loadedDocuments);
          setBreadcrumbs(breadcrumbsResult);
          setTotalElements(totalCount);
          setTotalPages(pagesCount);
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
  }, [slug, currentFolderId, token, activeTray.id, currentPage, pageSize]);

  // ── Reload documents after uploads finish ─────────────────────────

  const prevActiveRef = useRef(false);
  useEffect(() => {
    const hasActive = uploadTasks.some(
      (t) => t.status === "uploading" || t.status === "queued"
    );
    const hadActive = prevActiveRef.current;
    prevActiveRef.current = hasActive;

    if (hadActive && !hasActive && token) {
      const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(currentFolderId);
      if (isUuid) {
        getDocuments(slug, currentFolderId, token, currentPage, pageSize)
          .then((pageData) => {
            setDocuments(pageData.content);
            setTotalElements(pageData.totalElements);
            setTotalPages(pageData.totalPages);
          })
          .catch(() => {});
      }
    }
  }, [uploadTasks, slug, currentFolderId, token, currentPage, pageSize]);

  // ── Event Handlers ────────────────────────────────────────────────

  function handleFolderClick(folder: FolderItem) {
    setCurrentFolderId(folder.id);
    setCurrentPage(0);
  }

  function handleBreadcrumbNavigate(node: BreadcrumbNode) {
    setCurrentFolderId(node.id);
    setCurrentPage(0);
  }

  function handlePageSizeChange(newSize: number) {
    setPageSize(newSize);
    setCurrentPage(0);
  }

  function handlePageChange(newPage: number) {
    if (newPage >= 0 && newPage < totalPages) {
      setCurrentPage(newPage);
    }
  }

  function getPageNumbers(): (number | string)[] {
    if (totalPages <= 7) {
      return Array.from({ length: totalPages }, (_, i) => i);
    }
    if (currentPage < 4) {
      return [0, 1, 2, 3, 4, "ellipsis-end", totalPages - 1];
    }
    if (currentPage > totalPages - 5) {
      return [0, "ellipsis-start", totalPages - 5, totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1];
    }
    return [0, "ellipsis-start", currentPage - 1, currentPage, currentPage + 1, "ellipsis-end", totalPages - 1];
  }

  // ── Upload Notifications ──────────────────────────────────────────
  const notifiedTasksRef = useRef<Set<string>>(new Set());
  useEffect(() => {
    uploadTasks.forEach((task) => {
      if (task.status === "done" && !notifiedTasksRef.current.has(`${task.id}-done`)) {
        notifiedTasksRef.current.add(`${task.id}-done`);
        notify.uploadComplete(task.file.name, currentFolderName);
      } else if (task.status === "failed" && !notifiedTasksRef.current.has(`${task.id}-failed`)) {
        notifiedTasksRef.current.add(`${task.id}-failed`);
        notify.uploadFailed(task.file.name, task.error);
      }
    });
  }, [uploadTasks, currentFolderName]);

  async function handleCreateFolderSubmit(data: CreateFolderDTO) {
    try {
      const newFolder = await createSubFolder(slug, currentFolderId, data, token);
      setFolders((prev) => [...prev, newFolder]);
      notify.success("Folder created", `"${newFolder.name}" has been created`);
    } catch (err) {
      notify.error("Failed to create folder", err instanceof Error ? err.message : "An error occurred");
      throw err;
    }
  }

  async function handleConfirmDelete() {
    if (!pendingDelete) return;

    try {
      if (pendingDelete.type === "tray") {
        if (onDeleteTray) {
          onDeleteTray(pendingDelete.id);
          notify.success("Tray deleted", `"${pendingDelete.name}" was deleted`);
        }
      } else if (pendingDelete.type === "document") {
        if (token) {
          await deleteDocument(slug, pendingDelete.id, token);
        }
        setDocuments((prev) => prev.filter((d) => d.id !== pendingDelete.id));
        setTotalElements((prev) => Math.max(0, prev - 1));
        notify.success("Document deleted", `"${pendingDelete.name}" was removed`);
      } else {
        await deleteFolder(slug, pendingDelete.id, token);
        setFolders((prev) => prev.filter((f) => f.id !== pendingDelete.id));

        if (pendingDelete.id === currentFolderId) {
          const parentNode = breadcrumbs[breadcrumbs.length - 2] || breadcrumbs[0];
          setCurrentFolderId(parentNode.id);
        }
        notify.success("Folder deleted", `"${pendingDelete.name}" was deleted`);
      }
    } catch (err) {
      notify.error(`Failed to delete ${pendingDelete.type}`, err instanceof Error ? err.message : "An error occurred");
    }
  }

  function handleDownloadDocument(doc: DocumentDTO) {
    if (token) {
      notify.info("Downloading file", `Preparing "${doc.originalFileName}"`);
      downloadDocument(slug, doc.id, token);
    }
  }

  function handleDeleteDocumentClick(doc: DocumentDTO) {
    setActiveMenuId(null);
    setPendingDelete({
      id: doc.id,
      name: doc.originalFileName,
      type: "document",
    });
  }

  // ── Rename, Move and Copy ─────────────────────────────────────────

  async function handleRename(item: ItemRef, name: string) {
    if (name === item.name || !token) return;
    if (item.type === "document") {
      const updated = await updateDocument(slug, item.id, { name }, token);
      setDocuments((prev) => prev.map((d) => (d.id === item.id ? { ...d, originalFileName: updated.originalFileName } : d)));
    } else {
      const updated = await updateFolder(slug, item.id, { name }, token);
      setFolders((prev) => prev.map((f) => (f.id === item.id ? { ...f, name: updated.name } : f)));
    }
    notify.success("Renamed", `"${item.name}" is now "${name}"`);
  }

  // Everything moved here leaves the current view, wherever it was dropped.
  async function moveItem(item: ItemRef, folderId: string) {
    if (!token) return;
    if (item.type === "document") {
      const updated = await updateDocument(slug, item.id, { folderId }, token);
      setDocuments((prev) => prev.filter((d) => d.id !== item.id));
      setTotalElements((prev) => Math.max(0, prev - 1));
      // A retention policy on the destination locks it straight away, so say so.
      const lock = lockDescription(updated);
      if (lock) {
        notify.warning(`"${item.name}" was moved`, lock);
        return;
      }
    } else {
      await updateFolder(slug, item.id, { parentFolderId: folderId }, token);
      setFolders((prev) => prev.filter((f) => f.id !== item.id));
    }
    notify.success("Moved", `"${item.name}" was moved`);
  }

  async function copyItem(item: ItemRef, folderId: string) {
    if (!token) return;
    const copy = await copyDocument(slug, item.id, folderId, token);
    if (folderId === currentFolderId) {
      setDocuments((prev) => [...prev, copy]);
      setTotalElements((prev) => prev + 1);
    }
    notify.success("Copied", `Saved as "${copy.originalFileName}"`);
  }

  function folderActions(folder: FolderItem): ItemAction[] {
    const item: ItemRef = { type: "folder", id: folder.id, name: folder.name };
    return [
      ...(canUpdateFolders ? [
        { label: "Rename", icon: Pencil, onSelect: () => setRenaming(item) },
        { label: "Move to...", icon: FolderInput, onSelect: () => setPicking({ mode: "move", item }) },
      ] : []),
      ...(canApplyLegalHold ? [
        { label: "Legal hold...", icon: Gavel, onSelect: () => setHolding(item) },
      ] : []),
      ...(canDeleteFolder ? [
        { label: "Delete", icon: Trash2, danger: true, onSelect: () => setPendingDelete({ id: folder.id, name: folder.name, type: "folder" }) },
      ] : []),
    ];
  }

  async function handleArchive(doc: DocumentDTO) {
    if (!token) return;
    await archiveDocument(slug, doc.id, token);
    setDocuments((prev) => prev.filter((d) => d.id !== doc.id));
    setTotalElements((prev) => Math.max(0, prev - 1));
    notify.success("Archived", `"${doc.originalFileName}" was moved to the Archive`);
  }

  async function handleUnarchive(doc: DocumentDTO) {
    if (!token) return;
    try {
      const updated = await unarchiveDocument(slug, doc.id, token);
      if (updated.folderId !== currentFolderId) {
        setDocuments((prev) => prev.filter((d) => d.id !== doc.id));
        setTotalElements((prev) => Math.max(0, prev - 1));
      }
      notify.success("Unarchived", `"${updated.originalFileName}" is no longer read-only`);
    } catch (err) {
      notify.error("Could not unarchive this", err instanceof Error ? err.message : undefined);
    }
  }

  async function handleOverrideDelete(doc: DocumentDTO, reason: string) {
    if (!token) return;
    await deleteDocument(slug, doc.id, token, { reason });
    setDocuments((prev) => prev.filter((d) => d.id !== doc.id));
    setTotalElements((prev) => Math.max(0, prev - 1));
    notify.success("Lock overridden", `"${doc.originalFileName}" went to the Trash`);
  }

  async function handleApplyHold(item: ItemRef, name: string, reason: string) {
    if (!token) return;
    const targets = item.type === "document" ? { documentIds: [item.id] } : { folderIds: [item.id] };
    const hold = await createLegalHold(slug, name, reason, targets, token);
    if (item.type === "document") {
      setDocuments((prev) => prev.map((d) => (d.id === item.id ? { ...d, legalHold: true } : d)));
    }
    notify.success("Legal hold applied", `${hold.documentCount} ${hold.documentCount === 1 ? "document" : "documents"} covered`);
  }

  function documentActions(doc: DocumentDTO): ItemAction[] {
    const item: ItemRef = { type: "document", id: doc.id, name: doc.originalFileName };
    return [
      ...(canUpdateDocuments && !isReadOnly(doc) ? [
        { label: "Rename", icon: Pencil, onSelect: () => setRenaming(item) },
        { label: "Move to...", icon: FolderInput, onSelect: () => setPicking({ mode: "move", item }) },
        { label: "Archive...", icon: Archive, onSelect: () => setArchiving(doc) },
      ] : []),
      ...(canUpdateDocuments && doc.archivedAt && !isLocked(doc) ? [
        { label: "Unarchive", icon: ArchiveRestore, onSelect: () => handleUnarchive(doc) },
      ] : []),
      ...(canCopyDocuments ? [
        { label: "Copy to...", icon: Copy, onSelect: () => setPicking({ mode: "copy", item }) },
      ] : []),
      ...(canApplyLegalHold ? [
        { label: "Legal hold...", icon: Gavel, onSelect: () => setHolding(item) },
      ] : []),
      ...(canOverrideLocks && canBeOverridden(doc) ? [
        { label: "Delete anyway...", icon: ShieldAlert, danger: true, onSelect: () => setOverriding(doc) },
      ] : []),
    ];
  }

  // ── Dragging Folders and Documents Into Folders ───────────────────

  function dragProps(item: ItemRef, allowed: boolean) {
    if (!allowed) return {};
    return {
      draggable: true,
      onDragStart: (e: React.DragEvent) => {
        e.dataTransfer.setData(ITEM_DRAG_TYPE, JSON.stringify(item));
        e.dataTransfer.effectAllowed = "move";
      },
    };
  }

  // Drops carry on up to the page's own drop handler, which resets the file-upload overlay.
  function dropProps(folderId: string) {
    if (!canUpdateDocuments && !canUpdateFolders) return {};
    return {
      onDragOver: (e: React.DragEvent) => {
        if (e.dataTransfer.types.includes(ITEM_DRAG_TYPE)) {
          setDropTargetId(folderId);
        }
      },
      onDragLeave: () => setDropTargetId((current) => (current === folderId ? null : current)),
      onDrop: (e: React.DragEvent) => {
        setDropTargetId(null);
        const data = e.dataTransfer.getData(ITEM_DRAG_TYPE);
        if (!data) return;
        const item = JSON.parse(data) as ItemRef;
        if (item.id === folderId) return;
        moveItem(item, folderId).catch((err: unknown) => {
          notify.error("Could not move this", err instanceof Error ? err.message : undefined);
        });
      },
    };
  }

  const dropHighlight = (folderId: string) =>
    dropTargetId === folderId ? "ring-2 ring-indigo-400" : "";

  // ── Upload Handlers ───────────────────────────────────────────────

  function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    uploadManagerRef.current?.addFiles(Array.from(files), currentFolderId);
    e.target.value = "";
  }

  // ── Drag and Drop ─────────────────────────────────────────────────

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

  const filteredFolders = folders.filter((f) =>
    f.name.toLowerCase().includes(searchQuery.toLowerCase())
  );
  const filteredDocuments = documents.filter((d) =>
    d.originalFileName.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // ── Derived State ─────────────────────────────────────────────────

  const isTrash = activeTray.id === "trash" || activeTray.name.toLowerCase() === "trash";
  const isAtRoot = currentFolderId === activeTray.id;

  // ── Render ────────────────────────────────────────────────────────

  return (
    <div
      className="space-y-6 pb-2 sm:pb-3 flex-1 flex flex-col"
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={handleFileSelect}
        accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.png,.jpg,.jpeg,.gif,.svg,.webp,.zip,.rar,.7z"
      />

      {/* ── Drag & Drop Overlay ──────────────────────────────── */}
      {isDragging && canUpload && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-indigo-600/10 backdrop-blur-xs">
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
            dropProps={(node) => dropProps(node.id)}
            dropTargetId={dropTargetId}
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
              <div className="flex items-center gap-2.5 flex-wrap">
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

              {/* Upload button */}
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

              {/* Tray options menu */}
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

              {/* Folder options menu */}
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

        {/* ── Search & View Mode Switcher Row ────────────────── */}
        <div className="mt-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          {/* Search bar */}
          <div className="flex-1 flex items-center gap-2 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/60 dark:bg-gray-800/40 px-3 py-2 text-xs text-gray-500 dark:text-gray-400">
            <Search className="h-4 w-4 text-gray-400 shrink-0" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search folders and documents..."
              className="w-full bg-transparent border-none outline-none text-xs text-gray-900 dark:text-white placeholder-gray-400"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 text-xs px-1"
                title="Clear search"
                aria-label="Clear search"
              >
                ✕
              </button>
            )}
          </div>

          {/* View Mode Switcher */}
          <ViewModeSwitcher viewMode={viewMode} onChange={handleViewModeChange} />
        </div>
      </section>

      {/* ── Main Content: Folders and Documents ──────────────── */}
      {loading ? (
        <div className="flex min-h-[200px] items-center justify-center rounded-2xl border border-gray-200 bg-white p-8 text-sm text-gray-500 dark:border-gray-800 dark:bg-gray-900">
          <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mr-3" />
          Loading contents...
        </div>
      ) : filteredFolders.length === 0 && filteredDocuments.length === 0 ? (
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
        <div className="space-y-7">
          {/* ─────────────────── FOLDERS SECTION ─────────────────── */}
          {filteredFolders.length > 0 && (
            <section>
              <div className="mb-3.5 flex items-center justify-between">
                <h3 className="text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  Folders ({filteredFolders.length})
                </h3>
              </div>

              {/* 1. Folders — CARDS VIEW */}
              {viewMode === "cards" && (
                <div className="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-3">
                  {filteredFolders.map((folder) => (
                    <div
                      key={folder.id}
                      className={`group flex flex-col justify-between rounded-2xl border border-gray-200 bg-white p-4 shadow-xs hover:border-indigo-300 hover:shadow-md dark:border-gray-800 dark:bg-gray-900 dark:hover:border-indigo-800 transition-all cursor-pointer relative ${dropHighlight(folder.id)}`}
                      onClick={() => handleFolderClick(folder)}
                      {...dragProps({ type: "folder", id: folder.id, name: folder.name }, canUpdateFolders)}
                      {...dropProps(folder.id)}
                    >
                      <div>
                        <div className="flex items-start justify-between gap-3 mb-2.5">
                          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400 group-hover:scale-105 transition-transform">
                            <FolderIcon className="h-5 w-5" />
                          </div>
                          <div className="flex items-center gap-1.5">
                            {folder.visibility === "ADMIN_ONLY" ? (
                              <span className="flex items-center gap-1 rounded-full bg-amber-50 dark:bg-amber-950/40 px-2 py-0.5 text-[10px] font-semibold text-amber-700 dark:text-amber-300">
                                <Shield className="h-2.5 w-2.5" /> Admin
                              </span>
                            ) : (
                              <span className="flex items-center gap-1 rounded-full bg-emerald-50 dark:bg-emerald-950/40 px-2 py-0.5 text-[10px] font-semibold text-emerald-700 dark:text-emerald-300">
                                <Users className="h-2.5 w-2.5" /> Members
                              </span>
                            )}
                            <ItemActionsMenu itemName={folder.name} actions={folderActions(folder)} />
                          </div>
                        </div>

                        <p className="font-semibold text-sm text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors line-clamp-1">
                          {folder.name}
                        </p>
                        <p className="mt-1 text-xs text-gray-500 dark:text-gray-400 line-clamp-2">
                          {folder.description || "Sub-folder for document organization."}
                        </p>
                      </div>

                      <div className="mt-4 pt-3 border-t border-gray-100 dark:border-gray-800/80 flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
                        <span className="inline-flex items-center gap-1 text-[11px] font-medium text-gray-600 dark:text-gray-300">
                          <Layers className="h-3 w-3 text-gray-400" />
                          {folder.itemCount ?? 0} {folder.itemCount === 1 ? "item" : "items"}
                        </span>
                        <span className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 dark:text-indigo-400 group-hover:translate-x-0.5 transition-transform">
                          Open <ChevronRight className="h-3.5 w-3.5" />
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* 2. Folders — THUMBNAIL VIEW */}
              {viewMode === "thumbnail" && (
                <div className="grid gap-4 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
                  {filteredFolders.map((folder) => (
                    <div
                      key={folder.id}
                      className={`group flex flex-col rounded-2xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 p-2.5 shadow-2xs hover:border-amber-400 hover:shadow-md dark:hover:border-amber-600 transition-all cursor-pointer relative ${dropHighlight(folder.id)}`}
                      onClick={() => handleFolderClick(folder)}
                      {...dragProps({ type: "folder", id: folder.id, name: folder.name }, canUpdateFolders)}
                      {...dropProps(folder.id)}
                    >
                      <FolderThumbnail
                        name={folder.name}
                        itemCount={folder.itemCount}
                        className="h-28 w-full"
                      />
                      <div className="mt-2.5 px-1 min-w-0">
                        <p
                          className="truncate text-xs font-semibold text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors"
                          title={folder.name}
                        >
                          {folder.name}
                        </p>
                        <div className="mt-1 flex items-center justify-between text-[11px] text-gray-400 dark:text-gray-500">
                          <span>{folder.itemCount ?? 0} items</span>
                          <ItemActionsMenu itemName={folder.name} actions={folderActions(folder)} openUp />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* 3. Folders — LIST VIEW */}
              {viewMode === "list" && (
                <div className="overflow-hidden rounded-2xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-2xs">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs">
                      <thead className="border-b border-gray-100 dark:border-gray-800 bg-gray-50/70 dark:bg-gray-800/40 text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                        <tr>
                          <th className="py-3 px-4">Name</th>
                          <th className="py-3 px-4 hidden md:table-cell">Description</th>
                          <th className="py-3 px-4 hidden sm:table-cell">Items</th>
                          <th className="py-3 px-4 hidden lg:table-cell">Visibility</th>
                          <th className="py-3 px-4 hidden sm:table-cell">Created</th>
                          <th className="py-3 px-4 text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                        {filteredFolders.map((folder) => (
                          <tr
                            key={folder.id}
                            className={`hover:bg-gray-50/80 dark:hover:bg-gray-800/40 transition-colors cursor-pointer group ${dropHighlight(folder.id)}`}
                            onClick={() => handleFolderClick(folder)}
                            {...dragProps({ type: "folder", id: folder.id, name: folder.name }, canUpdateFolders)}
                            {...dropProps(folder.id)}
                          >
                            <td className="py-3 px-4">
                              <div className="flex items-center gap-3">
                                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400 group-hover:scale-105 transition-transform">
                                  <FolderIcon className="h-4 w-4" />
                                </div>
                                <span className="font-semibold text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors truncate max-w-xs">
                                  {folder.name}
                                </span>
                              </div>
                            </td>
                            <td className="py-3 px-4 hidden md:table-cell text-gray-500 dark:text-gray-400 truncate max-w-xs">
                              {folder.description || "—"}
                            </td>
                            <td className="py-3 px-4 hidden sm:table-cell text-gray-600 dark:text-gray-300 font-medium">
                              {folder.itemCount ?? 0}
                            </td>
                            <td className="py-3 px-4 hidden lg:table-cell">
                              {folder.visibility === "ADMIN_ONLY" ? (
                                <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 dark:bg-amber-950/40 px-2 py-0.5 text-[10px] font-semibold text-amber-700 dark:text-amber-300">
                                  <Shield className="h-2.5 w-2.5" /> Admin Only
                                </span>
                              ) : (
                                <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 dark:bg-emerald-950/40 px-2 py-0.5 text-[10px] font-semibold text-emerald-700 dark:text-emerald-300">
                                  <Users className="h-2.5 w-2.5" /> All Members
                                </span>
                              )}
                            </td>
                            <td className="py-3 px-4 hidden sm:table-cell text-gray-400 dark:text-gray-500">
                              {folder.createdAt
                                ? new Date(folder.createdAt).toLocaleDateString()
                                : "—"}
                            </td>
                            <td
                              className="py-3 px-4 text-right"
                              onClick={(e) => e.stopPropagation()}
                            >
                              <div className="flex items-center justify-end gap-1">
                                <button
                                  type="button"
                                  onClick={() => handleFolderClick(folder)}
                                  className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-indigo-600 hover:bg-indigo-50 dark:hover:bg-indigo-950/40 transition-colors font-medium text-xs"
                                >
                                  <span>Open</span>
                                  <ChevronRight className="h-3.5 w-3.5" />
                                </button>
                                <ItemActionsMenu itemName={folder.name} actions={folderActions(folder)} />
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </section>
          )}

          {/* ─────────────────── DOCUMENTS SECTION ─────────────────── */}
          {filteredDocuments.length > 0 && (
            <section className="flex flex-col flex-1">
              <div className="mb-3.5 flex items-center justify-between">
                <h3 className="text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  Documents ({totalElements || filteredDocuments.length})
                </h3>
              </div>

              {/* Documents View Container */}
              <div className="flex-1 pb-4">
                {/* 1. Documents — CARDS VIEW */}
                {viewMode === "cards" && (
                <div className="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-3">
                  {filteredDocuments.map((doc) => {
                    const typeInfo = getFileTypeInfo(doc.originalFileName, doc.contentType);
                    const DocIcon = typeInfo.icon;
                    return (
                      <div
                        key={doc.id}
                        className="group flex flex-col justify-between rounded-2xl border border-gray-200 bg-white p-4 shadow-xs hover:border-indigo-300 hover:shadow-md dark:border-gray-800 dark:bg-gray-900 dark:hover:border-indigo-800 transition-all relative"
                        {...dragProps({ type: "document", id: doc.id, name: doc.originalFileName }, canUpdateDocuments && !isReadOnly(doc))}
                      >
                        <div>
                          {/* Header */}
                          <div className="flex items-start justify-between gap-3 mb-2.5">
                            <div
                              className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${typeInfo.bgClass} ${typeInfo.colorClass} group-hover:scale-105 transition-transform`}
                            >
                              <DocIcon className="h-5 w-5" />
                            </div>
                            <div className="flex items-center gap-1.5">
                              <LockBadge doc={doc} />
                              <AiStatusBadge
                                slug={slug}
                                docId={doc.id}
                                token={token || null}
                                initialStatus={doc.indexingStatus}
                              />
                              <span
                                className={`rounded-md px-2 py-0.5 text-[10px] font-bold tracking-wider ${typeInfo.badgeClass}`}
                              >
                                {typeInfo.extension}
                              </span>
                            </div>
                          </div>

                          <p
                            className="font-semibold text-sm text-gray-900 dark:text-white truncate"
                            title={doc.originalFileName}
                          >
                            {doc.originalFileName}
                          </p>
                          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                            {typeInfo.label} · {formatFileSize(doc.fileSize)}
                          </p>

                          <div className="mt-3 grid grid-cols-2 gap-2 text-[11px] text-gray-400 dark:text-gray-500 border-t border-gray-100 dark:border-gray-800/80 pt-2.5">
                            <div className="flex items-center gap-1.5 truncate">
                              <Calendar className="h-3 w-3 text-gray-400 shrink-0" />
                              <span className="truncate">
                                {new Date(doc.createdAt).toLocaleDateString()}
                              </span>
                            </div>
                            <div className="flex items-center gap-1.5 truncate">
                              <User className="h-3 w-3 text-gray-400 shrink-0" />
                              <span className="truncate">{getUploaderDisplayName(doc, user?.email)}</span>
                            </div>
                          </div>
                        </div>

                        {/* Action buttons */}
                        <div className="mt-4 pt-3 border-t border-gray-100 dark:border-gray-800/80 flex items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() => handleDownloadDocument(doc)}
                              className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 px-3 py-1.5 text-xs font-semibold text-gray-700 dark:text-gray-300 hover:bg-indigo-50 hover:text-indigo-600 hover:border-indigo-300 dark:hover:bg-indigo-950/60 dark:hover:text-indigo-300 transition-colors"
                            >
                              <Download className="h-3.5 w-3.5" />
                              <span>Download</span>
                            </button>
                            {onAskAi && (
                              <button
                                type="button"
                                onClick={() => onAskAi(doc.id, doc.originalFileName)}
                                disabled={doc.indexingStatus !== "COMPLETED"}
                                title={doc.indexingStatus === "COMPLETED" ? "Ask AI about this document" : doc.indexingStatus === "PENDING" || doc.indexingStatus === "PROCESSING" ? "Still indexing..." : "Not supported for this file type"}
                                className="inline-flex items-center gap-1.5 rounded-lg border border-indigo-200 dark:border-indigo-800 bg-indigo-50 dark:bg-indigo-950/40 px-3 py-1.5 text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:bg-indigo-100 hover:border-indigo-300 dark:hover:bg-indigo-950/60 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                              >
                                <Sparkles className="h-3.5 w-3.5" />
                                <span>Ask AI</span>
                              </button>
                            )}
                          </div>
                          <div className="flex items-center gap-1">
                            <ItemActionsMenu itemName={doc.originalFileName} actions={documentActions(doc)} openUp />
                            {canDeleteDocuments && (
                              <button
                                type="button"
                                onClick={() => handleDeleteDocumentClick(doc)}
                                disabled={isLocked(doc)}
                                className="p-1.5 rounded-lg text-gray-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-gray-400"
                                title={isLocked(doc) ? lockDescription(doc) ?? undefined : "Delete document"}
                                aria-label={`Delete ${doc.originalFileName}`}
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </button>
                            )}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* 2. Documents — THUMBNAIL VIEW */}
              {viewMode === "thumbnail" && (
                <div className="grid gap-4 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
                  {filteredDocuments.map((doc) => {
                    const typeInfo = getFileTypeInfo(doc.originalFileName, doc.contentType);
                    return (
                      <div
                        key={doc.id}
                        className="group flex flex-col rounded-2xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 p-2.5 shadow-2xs hover:border-indigo-300 hover:shadow-md dark:hover:border-indigo-700 transition-all relative"
                        {...dragProps({ type: "document", id: doc.id, name: doc.originalFileName }, canUpdateDocuments && !isReadOnly(doc))}
                      >
                        <div className="relative">
                          <DocumentThumbnail
                            filename={doc.originalFileName}
                            contentType={doc.contentType}
                            className="h-28 w-full"
                          />
                          {onAskAi && doc.indexingStatus === "COMPLETED" && (
                            <button
                              type="button"
                              onClick={() => onAskAi(doc.id, doc.originalFileName)}
                              className="absolute top-1.5 right-1.5 flex h-6 w-6 items-center justify-center rounded-lg bg-indigo-600/90 text-white opacity-0 group-hover:opacity-100 transition-opacity shadow-sm hover:bg-indigo-700"
                              title="Ask AI"
                              aria-label={`Ask AI about ${doc.originalFileName}`}
                            >
                              <Sparkles className="h-3 w-3" />
                            </button>
                          )}
                        </div>
                        <div className="mt-2.5 px-1 min-w-0">
                          <p
                            className="truncate text-xs font-semibold text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors"
                            title={doc.originalFileName}
                          >
                            {doc.originalFileName}
                          </p>
                          <div className="mt-1 flex items-center justify-between text-[11px] text-gray-400 dark:text-gray-500">
                            <LockBadge doc={doc} />
                            <AiStatusBadge
                              slug={slug}
                              docId={doc.id}
                              token={token || null}
                              initialStatus={doc.indexingStatus}
                            />
                            <span
                              className={`px-1 rounded text-[9px] font-bold ${typeInfo.badgeClass}`}
                            >
                              {typeInfo.extension}
                            </span>
                            <ItemActionsMenu itemName={doc.originalFileName} actions={documentActions(doc)} openUp />
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* 3. Documents — LIST VIEW */}
              {viewMode === "list" && (
                <div className="overflow-hidden rounded-2xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-2xs">
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-xs">
                      <thead className="border-b border-gray-100 dark:border-gray-800 bg-gray-50/70 dark:bg-gray-800/40 text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                        <tr>
                          <th className="py-3 px-4">Name</th>
                          <th className="py-3 px-4 hidden md:table-cell">Type</th>
                          <th className="py-3 px-4">Size</th>
                          <th className="py-3 px-4 hidden lg:table-cell">Uploaded By</th>
                          <th className="py-3 px-4 hidden sm:table-cell">Date</th>
                          <th className="py-3 px-4 hidden md:table-cell">AI</th>
                          <th className="py-3 px-4 text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                        {filteredDocuments.map((doc) => {
                          const typeInfo = getFileTypeInfo(doc.originalFileName, doc.contentType);
                          const DocIcon = typeInfo.icon;
                          return (
                            <tr
                              key={doc.id}
                              className="hover:bg-gray-50/80 dark:hover:bg-gray-800/40 transition-colors group"
                              {...dragProps({ type: "document", id: doc.id, name: doc.originalFileName }, canUpdateDocuments && !isReadOnly(doc))}
                            >
                              <td className="py-3 px-4">
                                <div className="flex items-center gap-3">
                                  <div
                                    className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${typeInfo.bgClass} ${typeInfo.colorClass} group-hover:scale-105 transition-transform`}
                                  >
                                    <DocIcon className="h-4 w-4" />
                                  </div>
                                  <LockBadge doc={doc} />
                                  <div className="min-w-0">
                                    <p
                                      className="font-semibold text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors truncate max-w-xs"
                                      title={doc.originalFileName}
                                    >
                                      {doc.originalFileName}
                                    </p>
                                    <p className="text-[10px] text-gray-400 dark:text-gray-500 sm:hidden">
                                      {formatFileSize(doc.fileSize)} ·{" "}
                                      {new Date(doc.createdAt).toLocaleDateString()}
                                    </p>
                                  </div>
                                </div>
                              </td>
                              <td className="py-3 px-4 hidden md:table-cell">
                                <span
                                  className={`inline-block px-2 py-0.5 rounded text-[10px] font-semibold ${typeInfo.badgeClass}`}
                                >
                                  {typeInfo.label}
                                </span>
                              </td>
                              <td className="py-3 px-4 text-gray-600 dark:text-gray-300 font-medium">
                                {formatFileSize(doc.fileSize)}
                              </td>
                              <td className="py-3 px-4 hidden lg:table-cell text-gray-500 dark:text-gray-400 truncate max-w-[120px]">
                                {getUploaderDisplayName(doc, user?.email)}
                              </td>
                              <td className="py-3 px-4 hidden sm:table-cell text-gray-400 dark:text-gray-500">
                                {new Date(doc.createdAt).toLocaleDateString()}
                              </td>
                              <td className="py-3 px-4 hidden md:table-cell">
                                <AiStatusBadge
                                  slug={slug}
                                  docId={doc.id}
                                  token={token || null}
                                  initialStatus={doc.indexingStatus}
                                />
                              </td>
                              <td className="py-3 px-4 text-right">
                                <div className="flex items-center justify-end gap-1.5">
                                  {onAskAi && (
                                    <button
                                      type="button"
                                      onClick={() => onAskAi(doc.id, doc.originalFileName)}
                                      disabled={doc.indexingStatus !== "COMPLETED"}
                                      className="p-1.5 rounded-lg text-indigo-500 hover:text-indigo-700 hover:bg-indigo-50 dark:hover:bg-indigo-950/40 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                                      title={doc.indexingStatus === "COMPLETED" ? "Ask AI" : "Not ready"}
                                      aria-label={`Ask AI about ${doc.originalFileName}`}
                                    >
                                      <Sparkles className="h-4 w-4" />
                                    </button>
                                  )}
                                  <button
                                    type="button"
                                    onClick={() => handleDownloadDocument(doc)}
                                    className="p-1.5 rounded-lg text-gray-500 hover:text-indigo-600 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                                    title="Download file"
                                    aria-label={`Download ${doc.originalFileName}`}
                                  >
                                    <Download className="h-4 w-4" />
                                  </button>
                                  <ItemActionsMenu itemName={doc.originalFileName} actions={documentActions(doc)} />
                                  {canDeleteDocuments && (
                                    <button
                                      type="button"
                                      onClick={() => handleDeleteDocumentClick(doc)}
                                      disabled={isLocked(doc)}
                                      className="p-1.5 rounded-lg text-gray-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-gray-400"
                                      title={isLocked(doc) ? lockDescription(doc) ?? undefined : "Delete document"}
                                      aria-label={`Delete ${doc.originalFileName}`}
                                    >
                                      <Trash2 className="h-4 w-4" />
                                    </button>
                                  )}
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
              </div>

              {/* ── Document Pagination & Page Size Footer ── */}
              <div className="sticky bottom-3 sm:bottom-4 z-30 mt-auto flex flex-col sm:flex-row items-center justify-between gap-3 sm:gap-4 rounded-2xl border border-gray-200/90 dark:border-gray-800/90 bg-white/95 dark:bg-gray-900/95 backdrop-blur-md px-5 py-3 sm:py-3.5 shadow-lg dark:shadow-gray-950/40">
                {/* Left: Files to load option (50-200) & Status */}
                <div className="flex flex-wrap items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
                  <label className="flex items-center gap-2">
                    <span>Files to load:</span>
                    <select
                      value={pageSize}
                      onChange={(e) => handlePageSizeChange(Number(e.target.value))}
                      className="rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 px-2.5 py-1 text-xs font-semibold text-gray-700 dark:text-gray-200 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 cursor-pointer"
                      aria-label="How many files to load"
                    >
                      <option value={50}>50</option>
                      <option value={100}>100</option>
                      <option value={150}>150</option>
                      <option value={200}>200</option>
                    </select>
                  </label>
                  <span className="hidden sm:inline text-gray-300 dark:text-gray-700">|</span>
                  <span>
                    Showing{" "}
                    <span className="font-semibold text-gray-900 dark:text-white">
                      {totalElements === 0 && filteredDocuments.length === 0
                        ? 0
                        : currentPage * pageSize + 1}
                    </span>
                    –
                    <span className="font-semibold text-gray-900 dark:text-white">
                      {Math.min(
                        (currentPage + 1) * pageSize,
                        totalElements || filteredDocuments.length
                      )}
                    </span>{" "}
                    of{" "}
                    <span className="font-semibold text-gray-900 dark:text-white">
                      {totalElements || filteredDocuments.length}
                    </span>{" "}
                    {(totalElements || filteredDocuments.length) === 1 ? "file" : "files"}
                  </span>
                </div>

                {/* Right: Pagination buttons */}
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => handlePageChange(currentPage - 1)}
                    disabled={currentPage === 0}
                    className="inline-flex items-center gap-1 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-2.5 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-750 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                    aria-label="Previous page"
                  >
                    <ChevronLeft className="h-3.5 w-3.5" />
                    <span className="hidden sm:inline">Previous</span>
                  </button>

                  {/* Page numbers */}
                  <div className="flex items-center gap-1">
                    {getPageNumbers().map((p, idx) =>
                      typeof p === "number" ? (
                        <button
                          key={p}
                          type="button"
                          onClick={() => handlePageChange(p)}
                          className={`min-w-8 h-8 rounded-lg text-xs font-semibold transition-colors ${
                            currentPage === p
                              ? "bg-indigo-600 text-white shadow-2xs"
                              : "text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800"
                          }`}
                          aria-current={currentPage === p ? "page" : undefined}
                          aria-label={`Page ${p + 1}`}
                        >
                          {p + 1}
                        </button>
                      ) : (
                        <span key={`ellipsis-${idx}`} className="px-1 text-gray-400 select-none">
                          …
                        </span>
                      )
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={() => handlePageChange(currentPage + 1)}
                    disabled={currentPage >= totalPages - 1}
                    className="inline-flex items-center gap-1 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-2.5 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-750 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                    aria-label="Next page"
                  >
                    <span className="hidden sm:inline">Next</span>
                    <ChevronRight className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>
            </section>
          )}
        </div>
      )}

      {/* ── Upload Progress Panel ────────────────────────────── */}
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

      {archiving && token && (
        <ArchiveModal
          slug={slug}
          token={token}
          docId={archiving.id}
          name={archiving.originalFileName}
          onClose={() => setArchiving(null)}
          onConfirm={() => handleArchive(archiving)}
        />
      )}

      {overriding && (
        <OverrideLockModal
          name={overriding.originalFileName}
          action="delete"
          retainUntil={overriding.retainUntil}
          onClose={() => setOverriding(null)}
          onConfirm={(reason) => handleOverrideDelete(overriding, reason)}
        />
      )}

      {holding && (
        <LegalHoldModal
          subject={holding.type === "document" ? `"${holding.name}"` : `Everything in "${holding.name}"`}
          onClose={() => setHolding(null)}
          onConfirm={(name, reason) => handleApplyHold(holding, name, reason)}
        />
      )}

      {renaming && (
        <RenameModal
          itemType={renaming.type}
          currentName={renaming.name}
          onClose={() => setRenaming(null)}
          onSubmit={(name) => handleRename(renaming, name)}
        />
      )}

      {picking && (
        <FolderPickerModal
          slug={slug}
          token={token}
          mode={picking.mode}
          itemName={picking.item.name}
          excludeFolderId={picking.item.type === "folder" ? picking.item.id : undefined}
          trays={trays.length > 0 ? trays : [activeTray]}
          onClose={() => setPicking(null)}
          onPick={async (folderId) => {
            const document = documents.find((d) => d.id === picking.item.id);
            // Going to the Archive can lock a document for years, so it gets the same
            // confirmation as the Archive action rather than happening on one click.
            if (picking.mode === "move" && document && trays.some((tray) => tray.id === folderId && tray.isArchive)) {
              setArchiving(document);
              return;
            }
            await (picking.mode === "move" ? moveItem(picking.item, folderId) : copyItem(picking.item, folderId));
          }}
        />
      )}
    </div>
  );
}
