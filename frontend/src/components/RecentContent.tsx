import { useState, useEffect } from "react";
import { FileText, Download, Clock, User, Trash2, History } from "lucide-react";
import { apiFetch } from "../lib/session";
import { useAuth } from "../context/useAuth";
import { notify } from "../lib/notifications";
import { getFileTypeInfo } from "../lib/fileTypeUtils";
import { formatFileSize } from "../services/documentService";
import type { DocumentDTO } from "../services/documentService";
import { listRecentDocuments } from "../services/documentService";
import { DocumentPreviewModal } from "./DocumentPreviewModal";
import { VersionHistoryModal } from "./VersionHistoryModal";
import { ManageTagsModal } from "./ManageTagsModal";
import { ShareLinkModal } from "./ShareLinkModal";
import { DeleteConfirmationModal } from "./DeleteConfirmationModal";
import { LockBadge } from "./LockBadge";
import { AiStatusBadge } from "./AiStatusBadge";
import { isLocked, lockDescription } from "../lib/retention";

interface RecentContentProps {
  slug: string;
  token: string;
  onAskAi?: (docId: string, docName: string) => void;
}

export function RecentContent({ slug, token, onAskAi }: RecentContentProps) {
  const { user } = useAuth();

  const [documents, setDocuments] = useState<DocumentDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modals
  const [previewDoc, setPreviewDoc] = useState<DocumentDTO | null>(null);
  const [versioningDoc, setVersioningDoc] = useState<DocumentDTO | null>(null);
  const [taggingDoc, setTaggingDoc] = useState<DocumentDTO | null>(null);
  const [sharingDoc, setSharingDoc] = useState<DocumentDTO | null>(null);
  const [pendingDelete, setPendingDelete] = useState<{ id: string; name: string } | null>(null);

  async function loadRecent() {
    try {
      setLoading(true);
      setError(null);
      const data = await listRecentDocuments(slug, token, 20);
      setDocuments(data);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load recent documents";
      setError(message);
      console.error("Failed to load recent documents", err);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadRecent();
  }, [slug, token]);

  function handleDownloadDocument(doc: DocumentDTO) {
    const url = `${import.meta.env.VITE_API_URL || "/api"}/tenants/${encodeURIComponent(slug)}/documents/${doc.id}/download`;
    apiFetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then((response) => {
        if (!response.ok) throw new Error("Download failed");
        return response.blob();
      })
      .then((blob) => {
        const blobUrl = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = blobUrl;
        link.download = doc.originalFileName;
        link.click();
        setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
      });
  }

  function handleDeleteDocumentClick(doc: DocumentDTO) {
    setPendingDelete({ id: doc.id, name: doc.originalFileName });
  }

  async function handleConfirmDelete() {
    if (!pendingDelete) return;
    try {
      const url = `${import.meta.env.VITE_API_URL || "/api"}/tenants/${encodeURIComponent(slug)}/documents/${pendingDelete.id}`;
      const response = await apiFetch(url, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!response.ok) throw new Error(`Failed to delete document (${response.status})`);
      setDocuments((prev) => prev.filter((d) => d.id !== pendingDelete.id));
      notify.success("Document deleted", `"${pendingDelete.name}" was removed`);
    } catch (err) {
      notify.error("Failed to delete document", err instanceof Error ? err.message : "An error occurred");
    }
  }

  function getUploaderDisplayName(doc: DocumentDTO): string {
    const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (doc.uploadedByName && doc.uploadedByName.trim() && !UUID_REGEX.test(doc.uploadedByName.trim())) {
      return doc.uploadedByName.trim();
    }
    if (doc.uploadedBy && !UUID_REGEX.test(doc.uploadedBy.trim())) {
      return doc.uploadedBy.trim();
    }
    if (user?.email) {
      const prefix = user.email.split("@")[0];
      return prefix.charAt(0).toUpperCase() + prefix.slice(1);
    }
    return "Member";
  }

  if (loading) {
    return (
      <div className="flex min-h-[300px] items-center justify-center rounded-2xl border border-gray-200 bg-white p-8 text-sm text-gray-500 dark:border-gray-800 dark:bg-gray-900">
        <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mr-3" />
        Loading recent documents...
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex min-h-[300px] flex-col items-center justify-center rounded-2xl border border-red-200 bg-red-50 p-8 text-center dark:border-red-900/50 dark:bg-red-950/20">
        <p className="text-sm font-medium text-red-700 dark:text-red-400">{error}</p>
        <button
          type="button"
          onClick={loadRecent}
          className="mt-3 rounded-lg bg-red-100 px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-200 dark:bg-red-900/40 dark:text-red-300 dark:hover:bg-red-900/60"
        >
          Try again
        </button>
      </div>
    );
  }

  if (documents.length === 0) {
    return (
      <section className="flex min-h-[300px] flex-col items-center justify-center rounded-2xl border border-dashed border-gray-300 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-900">
        <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-50 text-blue-600 dark:bg-blue-950/60 dark:text-blue-400">
          <History className="h-7 w-7" strokeWidth={1.5} />
        </div>
        <h3 className="text-base font-semibold text-gray-900 dark:text-white">No recent documents</h3>
        <p className="mt-1 max-w-sm text-xs text-gray-500 dark:text-gray-400">
          Documents you view or download will appear here for quick access.
        </p>
      </section>
    );
  }

  return (
    <div className="space-y-6">
      <section>
        <div className="mb-3.5 flex items-center justify-between">
          <h3 className="text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
            Recent Documents ({documents.length})
          </h3>
        </div>

        <div className="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-3">
          {documents.map((doc) => {
            const typeInfo = getFileTypeInfo(doc.originalFileName, doc.contentType);
            const DocIcon = typeInfo.icon;
            return (
              <div
                key={doc.id}
                className="group flex flex-col justify-between rounded-2xl border border-gray-200 bg-white p-4 shadow-xs hover:border-indigo-300 hover:shadow-md dark:border-gray-800 dark:bg-gray-900 dark:hover:border-indigo-800 transition-all relative"
              >
                <div>
                  {/* Header */}
                  <div className="flex items-start justify-between gap-3 mb-2.5">
                    <div className="flex items-center gap-2">
                      <div
                        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${typeInfo.bgClass} ${typeInfo.colorClass} group-hover:scale-105 transition-transform`}
                      >
                        <DocIcon className="h-5 w-5" />
                      </div>
                    </div>
                    <div className="flex flex-wrap items-center justify-end gap-1.5">
                      {doc.scanStatus === "PENDING" && (
                        <span className="rounded-md px-1.5 py-0.5 text-[9px] font-bold bg-amber-50 text-amber-600 dark:bg-amber-950/60 dark:text-amber-400">
                          Scanning
                        </span>
                      )}
                      {doc.scanStatus === "INFECTED" && (
                        <span className="rounded-md px-1.5 py-0.5 text-[9px] font-bold bg-red-50 text-red-600 dark:bg-red-950/60 dark:text-red-400">
                          Infected
                        </span>
                      )}
                      <button
                        type="button"
                        onClick={() => setVersioningDoc(doc)}
                        title="Version history"
                        className="rounded-md px-1.5 py-0.5 text-[10px] font-bold bg-gray-100 hover:bg-indigo-100 dark:bg-gray-800 dark:hover:bg-indigo-950/60 text-gray-700 dark:text-gray-300 hover:text-indigo-600 transition-colors"
                      >
                        v{doc.version || 1}
                      </button>
                      <LockBadge doc={doc} />
                      <AiStatusBadge
                        slug={slug}
                        docId={doc.id}
                        token={token}
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
                    onClick={() => setPreviewDoc(doc)}
                    className="font-semibold text-sm text-gray-900 dark:text-white truncate cursor-pointer hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
                    title={doc.originalFileName}
                  >
                    {doc.originalFileName}
                  </p>
                  <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                    {typeInfo.label} · {formatFileSize(doc.fileSize)}
                  </p>

                  {doc.tags && doc.tags.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1">
                      {doc.tags.map((t) => (
                        <span
                          key={t.id}
                          className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold border"
                          style={{ borderColor: t.color || "#6b7280", color: t.color || "#6b7280" }}
                        >
                          <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: t.color || "#6b7280" }} />
                          {t.name}
                        </span>
                      ))}
                    </div>
                  )}

                  <div className="mt-3 grid grid-cols-2 gap-2 text-[11px] text-gray-400 dark:text-gray-500 border-t border-gray-100 dark:border-gray-800/80 pt-2.5">
                    <div className="flex items-center gap-1.5 truncate">
                      <Clock className="h-3 w-3 text-gray-400 shrink-0" />
                      <span className="truncate">
                        {new Date(doc.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 truncate">
                      <User className="h-3 w-3 text-gray-400 shrink-0" />
                      <span className="truncate">{getUploaderDisplayName(doc)}</span>
                    </div>
                  </div>
                </div>

                {/* Action buttons */}
                <div className="mt-4 pt-3 border-t border-gray-100 dark:border-gray-800/80 flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setPreviewDoc(doc)}
                      className="inline-flex items-center gap-1.5 rounded-full border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 px-3 py-1.5 text-xs font-semibold text-gray-700 dark:text-gray-300 hover:bg-indigo-50 hover:text-indigo-600 hover:border-indigo-300 dark:hover:bg-indigo-950/60 dark:hover:text-indigo-300 transition-colors"
                      title="Preview document"
                    >
                      <FileText className="h-3.5 w-3.5" />
                      <span>Preview</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDownloadDocument(doc)}
                      className="inline-flex items-center gap-1.5 rounded-full border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 px-3 py-1.5 text-xs font-semibold text-gray-700 dark:text-gray-300 hover:bg-indigo-50 hover:text-indigo-600 hover:border-indigo-300 dark:hover:bg-indigo-950/60 dark:hover:text-indigo-300 transition-colors"
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
                        className="inline-flex items-center gap-1.5 rounded-full border border-indigo-200 dark:border-indigo-800 bg-indigo-50 dark:bg-indigo-950/40 px-3 py-1.5 text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:bg-indigo-100 hover:border-indigo-300 dark:hover:bg-indigo-950/60 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        <History className="h-3.5 w-3.5" />
                        <span>Ask AI</span>
                      </button>
                    )}
                  </div>
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => handleDeleteDocumentClick(doc)}
                      disabled={isLocked(doc)}
                      className="p-1.5 rounded-full text-gray-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-gray-400"
                      title={isLocked(doc) ? lockDescription(doc) ?? undefined : "Delete document"}
                      aria-label={`Delete ${doc.originalFileName}`}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      {/* Modals */}
      {previewDoc && token && (
        <DocumentPreviewModal
          slug={slug}
          docId={previewDoc.id}
          fileName={previewDoc.originalFileName}
          fileSize={previewDoc.fileSize}
          contentType={previewDoc.contentType}
          token={token}
          onClose={() => setPreviewDoc(null)}
        />
      )}

      {versioningDoc && token && (
        <VersionHistoryModal
          slug={slug}
          document={versioningDoc}
          token={token}
          canUpdate={true}
          onClose={() => setVersioningDoc(null)}
          onVersionUpdated={loadRecent}
        />
      )}

      {taggingDoc && token && (
        <ManageTagsModal
          slug={slug}
          document={taggingDoc}
          token={token}
          onClose={() => setTaggingDoc(null)}
          onTagsUpdated={loadRecent}
        />
      )}

      {sharingDoc && token && (
        <ShareLinkModal
          slug={slug}
          document={sharingDoc}
          token={token}
          onClose={() => setSharingDoc(null)}
        />
      )}

      {pendingDelete && (
        <DeleteConfirmationModal
          isOpen={true}
          itemName={pendingDelete.name}
          itemType="document"
          onClose={() => setPendingDelete(null)}
          onConfirm={handleConfirmDelete}
        />
      )}
    </div>
  );
}