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
    <form
      onSubmit={handleSubmit}
      role="search"
      className="hidden items-center gap-2 rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3 py-1.5 shadow-[0_1px_2px_rgba(0,0,0,0.05)] transition-all hover:bg-white hover:border-[#C1C1C1] focus-within:border-[#36C5F0] focus-within:bg-white focus-within:ring-2 focus-within:ring-[#36C5F0]/20 md:flex"
    >
      <Search className="h-4 w-4 shrink-0 text-[#616061]" />
      <input
        ref={inputRef}
        type="search"
        aria-label="Search this workspace"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search documents"
        className="w-44 bg-transparent text-[14px] text-[#1D1C1D] placeholder-[#616061] outline-none lg:w-72"
      />
      <kbd className="hidden rounded-[4px] border border-[#DDDDDD] bg-white px-1.5 py-0.5 text-[11px] font-semibold text-[#616061] shadow-[0_1px_1px_rgba(0,0,0,0.05)] lg:inline">
        {IS_MAC ? "⌘K" : "Ctrl K"}
      </kbd>
    </form>
  );
}
