import { List, Grid, LayoutGrid } from "lucide-react";

export type ViewMode = "list" | "thumbnail" | "cards";

interface ViewModeSwitcherProps {
  viewMode: ViewMode;
  onChange: (mode: ViewMode) => void;
  className?: string;
  size?: "sm" | "md";
}

export function ViewModeSwitcher({
  viewMode,
  onChange,
  className = "",
  size = "md",
}: ViewModeSwitcherProps) {
  const isSm = size === "sm";

  return (
    <div
      role="group"
      aria-label="View mode selection"
      className={`inline-flex items-center rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/70 dark:bg-gray-800/50 p-1 text-xs select-none shadow-2xs ${className}`}
    >
      {/* List View */}
      <button
        type="button"
        onClick={() => onChange("list")}
        className={`flex items-center gap-1.5 rounded-lg transition-all ${
          isSm ? "px-2 py-1 text-[11px]" : "px-2.5 py-1.5 text-xs"
        } font-medium ${
          viewMode === "list"
            ? "bg-white dark:bg-gray-700 text-indigo-600 dark:text-indigo-300 shadow-xs font-semibold"
            : "text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white"
        }`}
        title="List view"
        aria-label="List view"
        aria-pressed={viewMode === "list"}
      >
        <List className={isSm ? "h-3 w-3" : "h-3.5 w-3.5"} />
        <span>List</span>
      </button>

      {/* Thumbnail View */}
      <button
        type="button"
        onClick={() => onChange("thumbnail")}
        className={`flex items-center gap-1.5 rounded-lg transition-all ${
          isSm ? "px-2 py-1 text-[11px]" : "px-2.5 py-1.5 text-xs"
        } font-medium ${
          viewMode === "thumbnail"
            ? "bg-white dark:bg-gray-700 text-indigo-600 dark:text-indigo-300 shadow-xs font-semibold"
            : "text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white"
        }`}
        title="Thumbnail view"
        aria-label="Thumbnail view"
        aria-pressed={viewMode === "thumbnail"}
      >
        <Grid className={isSm ? "h-3 w-3" : "h-3.5 w-3.5"} />
        <span>Thumbnail</span>
      </button>

      {/* Cards View */}
      <button
        type="button"
        onClick={() => onChange("cards")}
        className={`flex items-center gap-1.5 rounded-lg transition-all ${
          isSm ? "px-2 py-1 text-[11px]" : "px-2.5 py-1.5 text-xs"
        } font-medium ${
          viewMode === "cards"
            ? "bg-white dark:bg-gray-700 text-indigo-600 dark:text-indigo-300 shadow-xs font-semibold"
            : "text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white"
        }`}
        title="Cards view"
        aria-label="Cards view"
        aria-pressed={viewMode === "cards"}
      >
        <LayoutGrid className={isSm ? "h-3 w-3" : "h-3.5 w-3.5"} />
        <span>Cards</span>
      </button>
    </div>
  );
}

