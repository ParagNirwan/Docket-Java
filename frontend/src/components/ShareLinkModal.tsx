import { useEffect, useState } from "react";
import { X, Share2, Link as LinkIcon, Copy, Check, Shield, Trash2, AlertCircle, Loader2 } from "lucide-react";
import {
  createShareLink,
  listShareLinks,
  revokeShareLink,
  type ShareLinkDTO,
} from "../services/shareLinkService";
import type { DocumentDTO } from "../services/documentService";

interface ShareLinkModalProps {
  slug: string;
  document: DocumentDTO;
  token: string;
  onClose: () => void;
}

export function ShareLinkModal({ slug, document, token, onClose }: ShareLinkModalProps) {
  const [links, setLinks] = useState<ShareLinkDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Creation form state
  const [expiryDays, setExpiryDays] = useState<number | "never">(7);
  const [password, setPassword] = useState("");
  const [maxDownloads, setMaxDownloads] = useState<string>("");
  const [creating, setCreating] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  async function loadLinks() {
    try {
      setLoading(true);
      setError(null);
      const data = await listShareLinks(slug, document.id, token);
      setLinks(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load share links");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadLinks();
  }, [slug, document.id, token]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    try {
      setCreating(true);
      setError(null);

      let expiresAt: string | null = null;
      if (expiryDays !== "never") {
        const d = new Date();
        d.setDate(d.getDate() + Number(expiryDays));
        expiresAt = d.toISOString();
      }

      const limit = maxDownloads.trim() ? parseInt(maxDownloads, 10) : null;

      const created = await createShareLink(
        slug,
        document.id,
        {
          expiresAt,
          password: password.trim() || null,
          maxDownloads: limit && limit > 0 ? limit : null,
        },
        token,
      );

      // Copy automatically
      if (created.shareUrl) {
        await navigator.clipboard.writeText(created.shareUrl);
        setCopiedId(created.id);
        setTimeout(() => setCopiedId(null), 3000);
      }

      setPassword("");
      setMaxDownloads("");
      await loadLinks();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create share link");
    } finally {
      setCreating(false);
    }
  }

  async function handleRevoke(linkId: string) {
    if (!window.confirm("Are you sure you want to revoke this link? Anyone with this URL will lose access.")) {
      return;
    }
    try {
      setError(null);
      await revokeShareLink(slug, document.id, linkId, token);
      await loadLinks();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to revoke share link");
    }
  }

  async function copyToClipboard(url: string, id: string) {
    try {
      await navigator.clipboard.writeText(url);
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 3000);
    } catch {
      // fallback
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="share-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
    >
      <div className="fixed inset-0 bg-black/50 backdrop-blur-xs" onClick={onClose} />
      <div className="relative w-full max-w-lg bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800 shrink-0">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400 shrink-0">
              <Share2 className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <h2 id="share-title" className="text-base font-bold text-gray-900 dark:text-white truncate">
                External Share Links
              </h2>
              <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
                {document.originalFileName}
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

        {/* Content */}
        <div className="p-6 space-y-5 overflow-y-auto flex-1">
          {error && (
            <div role="alert" className="flex items-center gap-2 rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 p-3 text-xs text-red-600 dark:text-red-400">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Create Form */}
          <form onSubmit={handleCreate} className="space-y-3.5 p-4 rounded-xl border border-gray-200 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-800/30">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
              Create New Public Link
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Expiration
                </label>
                <select
                  value={expiryDays}
                  onChange={(e) => setExpiryDays(e.target.value === "never" ? "never" : Number(e.target.value))}
                  className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-xs text-gray-900 dark:text-white focus:border-indigo-500 focus:outline-none"
                >
                  <option value={1}>1 day</option>
                  <option value={7}>7 days</option>
                  <option value={30}>30 days</option>
                  <option value="never">No expiration</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
                  Max Downloads (optional)
                </label>
                <input
                  type="number"
                  min="1"
                  placeholder="Unlimited"
                  value={maxDownloads}
                  onChange={(e) => setMaxDownloads(e.target.value)}
                  className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-xs text-gray-900 dark:text-white focus:border-indigo-500 focus:outline-none"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
                Password Protection (optional)
              </label>
              <input
                type="password"
                placeholder="Leave blank for no password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-xs text-gray-900 dark:text-white focus:border-indigo-500 focus:outline-none"
              />
            </div>

            <div className="flex justify-end pt-1">
              <button
                type="submit"
                disabled={creating}
                className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-full bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors disabled:opacity-50"
              >
                {creating ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <LinkIcon className="w-3.5 h-3.5" />}
                Generate Share Link
              </button>
            </div>
          </form>

          {/* Active Links List */}
          <div className="space-y-2">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
              Existing Share Links ({links.filter((l) => !l.revoked).length})
            </h3>

            {loading ? (
              <div className="flex justify-center py-6 text-gray-400">
                <Loader2 className="w-5 h-5 animate-spin" />
              </div>
            ) : links.length === 0 ? (
              <p className="text-xs text-gray-500 dark:text-gray-400 py-3 text-center">
                No active external links generated for this document yet.
              </p>
            ) : (
              <div className="space-y-2">
                {links.map((link) => {
                  const shareUrl = `${window.location.origin}/share/${link.token || link.id}`;
                  const isCopied = copiedId === link.id;

                  return (
                    <div
                      key={link.id}
                      className={`p-3 rounded-xl border flex flex-col gap-2 ${
                        link.revoked
                          ? "border-gray-200 dark:border-gray-800 opacity-60 bg-gray-50/50 dark:bg-gray-900/30"
                          : "border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900"
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2 min-w-0">
                          {link.hasPassword ? (
                            <span className="p-1 rounded-md bg-amber-50 dark:bg-amber-950/60 text-amber-600 dark:text-amber-400" title="Password Protected">
                              <Shield className="w-3.5 h-3.5" />
                            </span>
                          ) : (
                            <span className="p-1 rounded-md bg-gray-100 dark:bg-gray-800 text-gray-500">
                              <LinkIcon className="w-3.5 h-3.5" />
                            </span>
                          )}
                          <span className="text-xs font-mono text-gray-700 dark:text-gray-300 truncate">
                            {shareUrl}
                          </span>
                        </div>

                        {!link.revoked && (
                          <div className="flex items-center gap-1 shrink-0">
                            <button
                              type="button"
                              onClick={() => copyToClipboard(shareUrl, link.id)}
                              className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-full border border-gray-200 dark:border-gray-700 hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-700 dark:text-gray-300 transition-colors"
                            >
                              {isCopied ? <Check className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3" />}
                              {isCopied ? "Copied!" : "Copy"}
                            </button>
                            <button
                              type="button"
                              onClick={() => handleRevoke(link.id)}
                              title="Revoke link"
                              className="p-1.5 rounded-full text-red-500 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-950/30 transition-colors"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        )}
                      </div>

                      <div className="flex items-center gap-3 text-[11px] text-gray-500 dark:text-gray-400">
                        {link.revoked ? (
                          <span className="text-red-500 font-semibold">Revoked</span>
                        ) : (
                          <>
                            <span>Downloads: {link.downloadCount}{link.maxDownloads ? ` / ${link.maxDownloads}` : ""}</span>
                            <span>•</span>
                            <span>
                              {link.expiresAt
                                ? `Expires ${new Date(link.expiresAt).toLocaleDateString()}`
                                : "Never expires"}
                            </span>
                          </>
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
    </div>
  );
}

