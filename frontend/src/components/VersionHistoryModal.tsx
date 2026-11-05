import { useEffect, useState, useRef } from "react";
import { X, History, Upload, Eye, RotateCcw, AlertCircle, Loader2 } from "lucide-react";
import {
  getDocumentVersions,
  uploadDocumentVersion,
  restoreDocumentVersion,
  formatFileSize,
  type DocumentVersionDTO,
  type DocumentDTO,
} from "../services/documentService";
import { DocumentPreviewModal } from "./DocumentPreviewModal";

interface VersionHistoryModalProps {
  slug: string;
  document: DocumentDTO;
  token: string;
  canUpdate: boolean;
  onClose: () => void;
  onVersionUpdated: () => void;
}

export function VersionHistoryModal({
  slug,
  document,
  token,
  canUpdate,
  onClose,
  onVersionUpdated,
}: VersionHistoryModalProps) {
  const [versions, setVersions] = useState<DocumentVersionDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // New version upload state
  const [uploading, setUploading] = useState(false);
  const [uploadComment, setUploadComment] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Preview sub-modal
  const [previewVersion, setPreviewVersion] = useState<number | null>(null);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape" && previewVersion === null) onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose, previewVersion]);

  async function loadVersions() {
    try {
      setLoading(true);
      setError(null);
      const data = await getDocumentVersions(slug, document.id, token);
      setVersions(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load version history");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadVersions();
  }, [slug, document.id, token]);

  async function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      setUploading(true);
      setError(null);
      await uploadDocumentVersion(slug, document.id, file, uploadComment.trim() || undefined, token);
      setUploadComment("");
      if (fileInputRef.current) fileInputRef.current.value = "";
      await loadVersions();
      onVersionUpdated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to upload new version");
    } finally {
      setUploading(false);
    }
  }

  async function handleRestore(versionNumber: number) {
    if (!window.confirm(`Restore version ${versionNumber}? This will create a new current version from it.`)) {
      return;
    }
    try {
      setError(null);
      await restoreDocumentVersion(slug, document.id, versionNumber, token);
      await loadVersions();
      onVersionUpdated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to restore version");
    }
  }

  const currentVersionNumber = document.version ?? 1;

  return (
    <>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="versions-title"
        className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
      >
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs" onClick={onClose} />
        <div className="relative w-full max-w-2xl bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden flex flex-col max-h-[88vh]">
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800 shrink-0">
            <div className="flex items-center gap-3 min-w-0">
              <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400 shrink-0">
                <History className="w-5 h-5" />
              </div>
              <div className="min-w-0">
                <h2 id="versions-title" className="text-base font-bold text-gray-900 dark:text-white truncate">
                  Version History
                </h2>
                <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
                  {document.originalFileName} (Current: v{currentVersionNumber})
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close dialog"
              className="p-1.5 rounded-full text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Upload New Version Section (if user can update) */}
          {canUpdate && (
            <div className="px-6 py-3.5 bg-gray-50/70 dark:bg-gray-800/40 border-b border-gray-100 dark:border-gray-800 shrink-0">
              <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5">
                <input
                  type="text"
                  placeholder="Optional version note (e.g. Revised section 2)"
                  value={uploadComment}
                  onChange={(e) => setUploadComment(e.target.value)}
                  disabled={uploading}
                  className="flex-1 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-3.5 py-1.5 text-xs text-gray-900 dark:text-white focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                />
                <input
                  type="file"
                  ref={fileInputRef}
                  onChange={handleFileSelect}
                  className="hidden"
                />
                <button
                  type="button"
                  disabled={uploading}
                  onClick={() => fileInputRef.current?.click()}
                  className="inline-flex items-center justify-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-full bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors shrink-0 disabled:opacity-50"
                >
                  {uploading ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      Uploading...
                    </>
                  ) : (
                    <>
                      <Upload className="w-3.5 h-3.5" />
                      Upload New Version
                    </>
                  )}
                </button>
              </div>
            </div>
          )}

          {/* Body Content */}
          <div className="p-6 space-y-4 overflow-y-auto flex-1">
            {error && (
              <div role="alert" className="flex items-center gap-2 rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 p-3 text-xs text-red-600 dark:text-red-400">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {loading ? (
              <div className="flex justify-center py-10 text-gray-400">
                <Loader2 className="w-6 h-6 animate-spin" />
              </div>
            ) : versions.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-xs text-gray-500 dark:text-gray-400">
                  This document has only its initial version (v1).
                </p>
              </div>
            ) : (
              <div className="space-y-2.5">
                {versions.map((v) => {
                  const isCurrent = v.version === currentVersionNumber;
                  return (
                    <div
                      key={v.id}
                      className={`flex flex-col sm:flex-row sm:items-center justify-between p-3.5 rounded-xl border transition-all ${
                        isCurrent
                          ? "border-indigo-200 dark:border-indigo-800/80 bg-indigo-50/30 dark:bg-indigo-950/20"
                          : "border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900/60 hover:bg-gray-50/50 dark:hover:bg-gray-800/40"
                      }`}
                    >
                      <div className="flex items-start gap-3 min-w-0 mb-2 sm:mb-0">
                        <div
                          className={`px-2 py-0.5 rounded-full text-xs font-bold shrink-0 ${
                            isCurrent
                              ? "bg-indigo-600 text-white"
                              : "bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300"
                          }`}
                        >
                          v{v.version}
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-semibold text-gray-900 dark:text-white">
                              {v.comment || (isCurrent ? "Current active version" : `Version ${v.version}`)}
                            </span>
                            {isCurrent && (
                              <span className="text-[10px] uppercase font-bold text-indigo-600 dark:text-indigo-400 tracking-wider">
                                (Head)
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-2 text-[11px] text-gray-500 dark:text-gray-400 mt-0.5">
                            <span>{new Date(v.createdAt).toLocaleString()}</span>
                            <span>•</span>
                            <span>{formatFileSize(v.fileSize)}</span>
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 self-end sm:self-center shrink-0">
                        <button
                          type="button"
                          onClick={() => setPreviewVersion(v.version)}
                          title="Preview this version"
                          className="p-1.5 rounded-lg text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                        {canUpdate && !isCurrent && (
                          <button
                            type="button"
                            onClick={() => handleRestore(v.version)}
                            title="Restore this version as head"
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-full border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                          >
                            <RotateCcw className="w-3 h-3" />
                            Restore
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>

      {previewVersion !== null && (
        <DocumentPreviewModal
          slug={slug}
          docId={document.id}
          version={previewVersion}
          fileName={document.originalFileName}
          fileSize={versions.find((v) => v.version === previewVersion)?.fileSize}
          contentType={document.contentType}
          token={token}
          onClose={() => setPreviewVersion(null)}
        />
      )}
    </>
  );
}

