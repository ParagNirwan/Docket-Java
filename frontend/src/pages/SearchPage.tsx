import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, ChevronLeft, ChevronRight, Download, FolderOpen, Search } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { getFileTypeInfo } from "../lib/fileTypeUtils";
import { downloadDocument, formatFileSize } from "../services/documentService";
import {
  MIN_SEARCH_LENGTH,
  parseSnippet,
  SEARCH_TYPES,
  searchDocuments,
  type SearchPage as SearchResults,
  type SearchResult,
} from "../services/searchService";
import { getTenantTrays, type Tray } from "../services/trayService";

const INPUT_CLASS = "mt-1 w-full rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-900 dark:text-white";

// Keyword search results. Everything about the search lives in the URL, so that a
// search can be reloaded, shared or returned to with the back button.
export function SearchPage() {
  const { slug = "" } = useParams();
  const { token } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();

  const q = params.get("q") ?? "";
  const type = params.get("type") ?? "";
  const tray = params.get("tray") ?? "";
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  const page = Number(params.get("page")) || 0;

  const [draft, setDraft] = useState(q);
  const [trays, setTrays] = useState<Tray[]>([]);
  const [results, setResults] = useState<SearchResults | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  // The header's search box can change the query while this page is open.
  const [lastQ, setLastQ] = useState(q);
  if (q !== lastQ) {
    setLastQ(q);
    setDraft(q);
  }

  useEffect(() => {
    let cancelled = false;
    getTenantTrays(slug, token)
      .then((loaded) => {
        if (!cancelled) setTrays(loaded.filter((t) => t.name !== "Trash"));
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [slug, token]);

  const canSearch = q.trim().length >= MIN_SEARCH_LENGTH;

  useEffect(() => {
    if (!canSearch || !token) return;
    let cancelled = false;
    setLoading(true);
    setError("");
    searchDocuments(slug, { q, type, tray, from, to }, page, token)
      .then((loaded) => {
        if (!cancelled) setResults(loaded);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Could not run the search");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [slug, token, q, type, tray, from, to, page, canSearch]);

  // Changing anything about the search starts again from the first page.
  function updateSearch(changes: Record<string, string>) {
    const next = new URLSearchParams(params);
    next.delete("page");
    Object.entries(changes).forEach(([key, value]) => {
      if (value) next.set(key, value);
      else next.delete(key);
    });
    setParams(next);
  }

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(params);
    next.set("page", String(nextPage));
    setParams(next);
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    updateSearch({ q: draft.trim() });
  }

  function openFolder(result: SearchResult) {
    navigate(`/workspace/${encodeURIComponent(slug)}`, {
      state: { openTrayId: result.trayId, openFolderId: result.folderId },
    });
  }

  return (
    <div className="mx-auto w-full max-w-[1100px] px-5 py-7 sm:px-8 sm:py-9">
      <Link to={`/workspace/${encodeURIComponent(slug)}`} className="mb-4 inline-flex items-center gap-1.5 text-xs font-medium text-gray-500 transition-colors hover:text-gray-900 dark:text-gray-400 dark:hover:text-white">
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to workspace
      </Link>
      <h1 className="mb-5 text-3xl font-semibold tracking-tight text-gray-900 dark:text-white">Search</h1>

      <form onSubmit={handleSubmit} role="search" className="flex gap-2">
        <div className="flex flex-1 items-center gap-2 rounded-xl border border-gray-200 bg-white px-3 py-2 dark:border-gray-700 dark:bg-gray-900">
          <Search className="h-4 w-4 shrink-0 text-gray-400" />
          <input
            type="search"
            aria-label="Search documents"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="File names and the text inside documents"
            className="w-full bg-transparent text-sm text-gray-900 placeholder-gray-400 outline-none dark:text-white"
          />
        </div>
        <button type="submit" className="rounded-xl bg-indigo-600 px-4 py-2 text-xs font-semibold text-white shadow-xs transition-colors hover:bg-indigo-700">
          Search
        </button>
      </form>

      <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
          Type
          <select value={type} onChange={(e) => updateSearch({ type: e.target.value })} className={INPUT_CLASS}>
            <option value="">Any type</option>
            {SEARCH_TYPES.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
        <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
          Tray
          <select value={tray} onChange={(e) => updateSearch({ tray: e.target.value })} className={INPUT_CLASS}>
            <option value="">All trays</option>
            {trays.map((option) => (
              <option key={option.id} value={option.id}>{option.name}</option>
            ))}
          </select>
        </label>
        <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
          Added from
          <input type="date" value={from} onChange={(e) => updateSearch({ from: e.target.value })} className={INPUT_CLASS} />
        </label>
        <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
          Added to
          <input type="date" value={to} onChange={(e) => updateSearch({ to: e.target.value })} className={INPUT_CLASS} />
        </label>
      </div>

      <div className="mt-6">
        {!canSearch ? (
          <p className="text-sm text-gray-500 dark:text-gray-400">Type at least {MIN_SEARCH_LENGTH} characters to search.</p>
        ) : error ? (
          <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
            {error}
          </div>
        ) : loading && !results ? (
          <p role="status" className="text-sm text-gray-500 dark:text-gray-400">Searching...</p>
        ) : results && results.totalElements === 0 ? (
          <p className="text-sm text-gray-500 dark:text-gray-400">Nothing matches "{q}".</p>
        ) : results ? (
          <>
            <p className="mb-3 text-xs text-gray-500 dark:text-gray-400">
              {results.totalElements} {results.totalElements === 1 ? "document" : "documents"}
            </p>
            <ul className="space-y-3">
              {results.content.map((result) => (
                <ResultRow
                  key={result.documentId}
                  result={result}
                  onOpenFolder={() => openFolder(result)}
                  onDownload={() => token && downloadDocument(slug, result.documentId, token)}
                />
              ))}
            </ul>
            {results.totalPages > 1 && (
              <div className="mt-5 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
                <button type="button" onClick={() => goToPage(page - 1)} disabled={page === 0} className="inline-flex items-center gap-1 rounded-lg border border-gray-200 px-2.5 py-1.5 font-medium disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-700">
                  <ChevronLeft className="h-3.5 w-3.5" />
                  Previous
                </button>
                <span>Page {page + 1} of {results.totalPages}</span>
                <button type="button" onClick={() => goToPage(page + 1)} disabled={page >= results.totalPages - 1} className="inline-flex items-center gap-1 rounded-lg border border-gray-200 px-2.5 py-1.5 font-medium disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-700">
                  Next
                  <ChevronRight className="h-3.5 w-3.5" />
                </button>
              </div>
            )}
          </>
        ) : null}
      </div>
    </div>
  );
}

function ResultRow({ result, onOpenFolder, onDownload }: { result: SearchResult; onOpenFolder: () => void; onDownload: () => void }) {
  const typeInfo = getFileTypeInfo(result.fileName, result.contentType);
  const Icon = typeInfo.icon;
  const location = result.folderId === result.trayId ? result.trayName : `${result.trayName} / ${result.folderName}`;

  return (
    <li className="flex flex-col gap-3 rounded-2xl border border-gray-200 bg-white p-4 shadow-xs dark:border-gray-800 dark:bg-gray-900 sm:flex-row sm:items-start">
      <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${typeInfo.bgClass} ${typeInfo.colorClass}`}>
        <Icon className="h-5 w-5" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-gray-900 dark:text-white" title={result.fileName}>{result.fileName}</p>
        <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {location} · {formatFileSize(result.fileSize)} · {new Date(result.createdAt).toLocaleDateString()}
          {result.uploadedByName && ` · ${result.uploadedByName}`}
        </p>
        {result.snippet && (
          <p className="mt-2 text-xs leading-relaxed text-gray-600 dark:text-gray-300">
            {parseSnippet(result.snippet).map((part, index) => part.match ? (
              <mark key={index} className="rounded-sm bg-amber-100 px-0.5 text-gray-900 dark:bg-amber-500/30 dark:text-amber-100">{part.text}</mark>
            ) : (
              <span key={index}>{part.text}</span>
            ))}
            {result.page !== null && <span className="ml-1.5 text-gray-400">(page {result.page})</span>}
          </p>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-1.5">
        <button type="button" onClick={onOpenFolder} className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800">
          <FolderOpen className="h-3.5 w-3.5" />
          Open folder
        </button>
        <button type="button" onClick={onDownload} aria-label={`Download ${result.fileName}`} title="Download" className="rounded-lg p-1.5 text-gray-500 transition-colors hover:bg-gray-100 hover:text-indigo-600 dark:hover:bg-gray-800">
          <Download className="h-4 w-4" />
        </button>
      </div>
    </li>
  );
}
