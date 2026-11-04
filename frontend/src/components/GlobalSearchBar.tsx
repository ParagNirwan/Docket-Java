import { useEffect, useRef, useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Search } from "lucide-react";
import { MIN_SEARCH_LENGTH } from "../services/searchService";

const IS_MAC = typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.userAgent);

// The search box in the header. Ctrl+K or ⌘K jumps to it from anywhere in the workspace.
export function GlobalSearchBar({ slug }: { slug: string }) {
  const [query, setQuery] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const searchPath = `/workspace/${encodeURIComponent(slug)}/search`;

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        inputRef.current?.focus();
        inputRef.current?.select();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const trimmed = query.trim();
    if (trimmed.length < MIN_SEARCH_LENGTH) return;
    // On the results page, keep the filters already chosen.
    const params = new URLSearchParams(location.pathname === searchPath ? location.search : "");
    params.delete("page");
    params.set("q", trimmed);
    navigate(`${searchPath}?${params}`);
    inputRef.current?.blur();
  }

  return (
    <form onSubmit={handleSubmit} role="search" className="hidden items-center gap-2 rounded-full border border-transparent bg-gray-100 px-4 py-2 transition-colors hover:bg-gray-200/70 focus-within:border-blue-500 focus-within:bg-white dark:bg-gray-800 dark:hover:bg-gray-700/70 dark:focus-within:bg-gray-900 md:flex">
      <Search className="h-4 w-4 shrink-0 text-gray-500 dark:text-gray-400" />
      <input
        ref={inputRef}
        type="search"
        aria-label="Search this workspace"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search documents"
        className="w-40 bg-transparent text-sm text-gray-900 placeholder-gray-500 outline-none dark:text-white dark:placeholder-gray-400 lg:w-72"
      />
      <kbd className="hidden rounded-full bg-white/80 px-2 py-0.5 text-[10px] font-semibold text-gray-500 dark:bg-gray-900/80 dark:text-gray-400 lg:inline">
        {IS_MAC ? "⌘K" : "Ctrl K"}
      </kbd>
    </form>
  );
}
