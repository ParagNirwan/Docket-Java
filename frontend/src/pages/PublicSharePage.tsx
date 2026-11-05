import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { FileDown, Lock, Download, AlertCircle, Clock, Loader2, ArrowLeft } from "lucide-react";
import { getPublicShareInfo, downloadSharedDocument, type PublicShareInfoDTO } from "../services/shareLinkService";
import { formatFileSize } from "../services/documentService";

export function PublicSharePage() {
  const { token } = useParams<{ token: string }>();
  const [info, setInfo] = useState<PublicShareInfoDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [downloading, setDownloading] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadInfo() {
      if (!token) return;
      try {
        setLoading(true);
        setError(null);
        const data = await getPublicShareInfo(token);
        setInfo(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load shared document");
      } finally {
        setLoading(false);
      }
    }
    void loadInfo();
  }, [token]);

  async function handleDownload(e: React.FormEvent) {
    e.preventDefault();
    if (!token) return;

    try {
      setDownloading(true);
      setError(null);
      const { blob, filename } = await downloadSharedDocument(token, password || undefined);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename || info?.documentName || "document";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Download failed");
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="flex min-h-[calc(100svh-4rem)] flex-1 items-center justify-center px-4 py-12 sm:px-6 lg:px-8">
      <div className="w-full max-w-md">
        <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white p-7 shadow-xs dark:border-gray-800 dark:bg-gray-900 sm:p-8">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-12 text-gray-400 gap-3">
              <Loader2 className="w-8 h-8 animate-spin text-indigo-600 dark:text-indigo-400" />
              <p className="text-xs">Loading shared document...</p>
            </div>
          ) : error && !info ? (
            <div className="text-center py-6 space-y-3">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-red-50 text-red-600 dark:bg-red-950/60 dark:text-red-400">
                <AlertCircle className="h-6 w-6" />
              </div>
              <h2 className="text-lg font-bold text-gray-900 dark:text-white">Link Unavailable</h2>
              <p className="text-xs text-gray-500 dark:text-gray-400">{error}</p>
              <div className="pt-2">
                <Link
                  to="/login"
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:underline"
                >
                  <ArrowLeft className="w-3.5 h-3.5" />
                  Go to Docket Login
                </Link>
              </div>
            </div>
          ) : info ? (
            <form onSubmit={handleDownload} className="space-y-6">
              {/* Header Icon & Title */}
              <div className="text-center">
                <div className="mx-auto mb-3.5 flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
                  <FileDown className="h-6 w-6" strokeWidth={1.8} />
                </div>
                <h1 className="text-xl font-bold tracking-tight text-gray-900 dark:text-white break-words">
                  {info.documentName}
                </h1>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                  {formatFileSize(info.fileSize)} • Shared via Docket
                </p>
              </div>

              {error && (
                <div role="alert" className="rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 p-3 text-xs text-red-600 dark:text-red-400">
                  {error}
                </div>
              )}

              {info.expired || info.limitReached ? (
                <div className="rounded-xl border border-amber-200 dark:border-amber-900/60 bg-amber-50 dark:bg-amber-950/30 p-4 text-center text-xs text-amber-700 dark:text-amber-400 space-y-1">
                  <Clock className="w-5 h-5 mx-auto text-amber-500" />
                  <p className="font-semibold">This link is no longer active</p>
                  <p className="text-[11px] text-amber-600/90 dark:text-amber-400/80">
                    It has either passed its expiration date or reached the maximum download limit set by the sender.
                  </p>
                </div>
              ) : (
                <>
                  {info.requiresPassword && (
                    <div className="space-y-1.5">
                      <label
                        htmlFor="share-pw"
                        className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300"
                      >
                        Password Required
                      </label>
                      <div className="relative">
                        <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-gray-400">
                          <Lock className="h-4 w-4" />
                        </div>
                        <input
                          id="share-pw"
                          type="password"
                          required
                          autoFocus
                          placeholder="Enter link password"
                          value={password}
                          onChange={(e) => setPassword(e.target.value)}
                          className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 pl-10 pr-3.5 py-2.5 text-sm text-gray-900 dark:text-white focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                        />
                      </div>
                    </div>
                  )}

                  <button
                    type="submit"
                    disabled={downloading || (info.requiresPassword && !password)}
                    className="w-full inline-flex items-center justify-center gap-2 rounded-full bg-indigo-600 hover:bg-indigo-700 py-2.5 text-sm font-semibold text-white shadow-xs transition-colors disabled:opacity-50"
                  >
                    {downloading ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        Downloading...
                      </>
                    ) : (
                      <>
                        <Download className="w-4 h-4" />
                        Download File
                      </>
                    )}
                  </button>
                </>
              )}

              <div className="text-center pt-2 border-t border-gray-100 dark:border-gray-800">
                <Link
                  to="/login"
                  className="text-xs text-gray-500 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400"
                >
                  Sign in to Docket
                </Link>
              </div>
            </form>
          ) : null}
        </div>
      </div>
    </div>
  );
}

