import { useEffect, useState } from "react";
import { X, Download, Eye, AlertCircle, Loader2, FileText, Sparkles, ChevronDown, ChevronUp } from "lucide-react";
import { getDocumentSummary, type DocumentSummary } from "../services/aiService";
import { previewDocument, previewDocumentVersion, downloadDocument, formatFileSize } from "../services/documentService";

interface DocumentPreviewModalProps {
  slug: string;
  docId: string;
  version?: number;
  fileName: string;
  fileSize?: number;
  contentType?: string;
  initialPage?: number;
  token: string;
  onClose: () => void;
}

export function DocumentPreviewModal({
  slug,
  docId,
  version,
  fileName,
  fileSize,
  contentType,
  initialPage,
  token,
  onClose,
}: DocumentPreviewModalProps) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [resolvedType, setResolvedType] = useState<string>(contentType || "");
  const [textContent, setTextContent] = useState<string | null>(null);
  const [summary, setSummary] = useState<DocumentSummary | null>(null);
  const [summaryOpen, setSummaryOpen] = useState(true);

  // The summary describes the current version, so an old version is shown without one
  useEffect(() => {
    if (version) return;
    let active = true;
    getDocumentSummary(slug, docId, token)
      .then((data) => {
        if (active) setSummary(data.summary ? data : null);
      })
      .catch(() => {
        // The preview is still useful without it
      });
    return () => {
      active = false;
    };
  }, [slug, docId, version, token]);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  useEffect(() => {
    let active = true;
    let url: string | null = null;

    async function loadPreview() {
      try {
        setLoading(true);
        setError(null);
        const { blob, contentType: detectedType } = version
          ? await previewDocumentVersion(slug, docId, version, token)
          : await previewDocument(slug, docId, token);

        if (!active) return;

        setResolvedType(detectedType || blob.type || "");

        if (
          detectedType.includes("text") ||
          detectedType.includes("json") ||
          detectedType.includes("javascript") ||
          fileName.endsWith(".txt") ||
          fileName.endsWith(".csv") ||
          fileName.endsWith(".json")
        ) {
          const text = await blob.text();
          if (active) setTextContent(text);
        } else {
          url = URL.createObjectURL(blob);
          if (active) setPreviewUrl(url);
        }
      } catch (err) {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load document preview");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadPreview();

    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [slug, docId, version, token, fileName]);

  function handleDownload() {
    try {
      downloadDocument(slug, docId, token);
    } catch {
      // ignore
    }
  }

  const isPdf = resolvedType.includes("pdf") || fileName.toLowerCase().endsWith(".pdf");
  const isImage = resolvedType.startsWith("image/") || /\.(png|jpe?g|gif|webp|svg)$/i.test(fileName);
  const isText = textContent !== null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="preview-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6"
    >
      <div className="fixed inset-0 bg-black/60 backdrop-blur-xs" onClick={onClose} />
      <div className="relative flex flex-col w-full max-w-5xl h-[88vh] bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-gray-100 dark:border-gray-800 shrink-0">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-8 h-8 rounded-lg bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400 shrink-0">
              <Eye className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h2 id="preview-title" className="text-sm sm:text-base font-bold text-gray-900 dark:text-white truncate">
                  {fileName}
                </h2>
                {version && (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300">
                    v{version}
                  </span>
                )}
              </div>
              {fileSize != null && (
                <p className="text-xs text-gray-500 dark:text-gray-400">
                  {formatFileSize(fileSize)}
                </p>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={handleDownload}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-full border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              <Download className="w-3.5 h-3.5" />
              Download
            </button>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close preview"
              className="p-1.5 rounded-full text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {summary && !version && (
          <section
            aria-label="AI summary"
            className="shrink-0 border-b border-gray-100 bg-indigo-50/40 px-5 py-3 dark:border-gray-800 dark:bg-indigo-950/20"
          >
            <button
              type="button"
              onClick={() => setSummaryOpen((open) => !open)}
              aria-expanded={summaryOpen}
              className="flex w-full items-center gap-1.5 text-xs font-semibold text-indigo-700 dark:text-indigo-300"
            >
              <Sparkles className="h-3.5 w-3.5" />
              AI summary
              {summaryOpen ? <ChevronUp className="ml-auto h-3.5 w-3.5" /> : <ChevronDown className="ml-auto h-3.5 w-3.5" />}
            </button>
            {summaryOpen && (
              <div className="mt-2 max-h-40 overflow-y-auto text-xs leading-relaxed text-gray-700 dark:text-gray-300">
                <p>{summary.summary}</p>
                {summary.keyPoints.length > 0 && (
                  <ul className="mt-2 list-disc space-y-0.5 pl-4">
                    {summary.keyPoints.map((point) => (
                      <li key={point}>{point}</li>
                    ))}
                  </ul>
                )}
                <p className="mt-2 text-[10px] text-gray-400 dark:text-gray-500">
                  Written by AI. Check the document before relying on it.
                </p>
              </div>
            )}
          </section>
        )}

        {/* Preview Viewport */}
        <div className="flex-1 bg-gray-50/70 dark:bg-gray-950/80 p-4 overflow-auto flex items-center justify-center">
          {loading && (
            <div className="flex flex-col items-center gap-3 text-gray-500 dark:text-gray-400">
              <Loader2 className="w-8 h-8 animate-spin text-indigo-600 dark:text-indigo-400" />
              <p className="text-sm">Loading document preview...</p>
            </div>
          )}

          {error && !loading && (
            <div className="max-w-md text-center p-6 bg-white dark:bg-gray-900 rounded-xl border border-red-200 dark:border-red-900/60 shadow-xs">
              <AlertCircle className="w-8 h-8 text-red-500 mx-auto mb-2" />
              <h3 className="text-sm font-semibold text-gray-900 dark:text-white mb-1">Preview Unavailable</h3>
              <p className="text-xs text-gray-500 dark:text-gray-400 mb-4">{error}</p>
              <button
                type="button"
                onClick={handleDownload}
                className="px-4 py-2 text-xs font-medium rounded-full bg-indigo-600 text-white hover:bg-indigo-700"
              >
                Download Document Instead
              </button>
            </div>
          )}

          {!loading && !error && (
            <>
              {isPdf && previewUrl && (
                <iframe
                  src={initialPage ? `${previewUrl}#page=${initialPage}` : previewUrl}
                  title={fileName}
                  sandbox=""
                  className="w-full h-full rounded-xl border border-gray-200 dark:border-gray-800 bg-white"
                />
              )}

              {isImage && previewUrl && (
                <div className="max-w-full max-h-full flex items-center justify-center">
                  <img
                    src={previewUrl}
                    alt={fileName}
                    className="max-h-[75vh] max-w-full object-contain rounded-lg shadow-md"
                  />
                </div>
              )}

              {isText && textContent !== null && (
                <div className="w-full h-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl p-4 overflow-auto">
                  <pre className="text-xs font-mono text-gray-800 dark:text-gray-200 whitespace-pre-wrap break-words">
                    {textContent}
                  </pre>
                </div>
              )}

              {!isPdf && !isImage && !isText && (
                <div className="max-w-md text-center p-8 bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-800 shadow-sm">
                  <div className="w-12 h-12 rounded-2xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center mx-auto mb-3">
                    <FileText className="w-6 h-6" />
                  </div>
                  <h3 className="text-sm font-bold text-gray-900 dark:text-white mb-1">
                    No In-Browser Preview
                  </h3>
                  <p className="text-xs text-gray-500 dark:text-gray-400 mb-5">
                    This file format ({fileName.split(".").pop()?.toUpperCase() || "unknown"}) cannot be rendered in your browser. Download the file to view it in your desktop application.
                  </p>
                  <button
                    type="button"
                    onClick={handleDownload}
                    className="inline-flex items-center gap-2 px-5 py-2.5 text-xs font-semibold rounded-full bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors"
                  >
                    <Download className="w-4 h-4" />
                    Download {fileName}
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
