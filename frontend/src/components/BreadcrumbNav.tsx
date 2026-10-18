import { ChevronRight, Folder, Layers } from "lucide-react";
import type { BreadcrumbNode } from "../services/trayService";

interface BreadcrumbNavProps {
  items: BreadcrumbNode[];
  onNavigate: (node: BreadcrumbNode) => void;
}

export function BreadcrumbNav({ items, onNavigate }: BreadcrumbNavProps) {
  if (items.length === 0) return null;

  return (
    <nav
      aria-label="Folder Breadcrumb"
      className="flex items-center flex-wrap gap-1.5 text-xs text-gray-500 dark:text-gray-400 py-1"
    >
      {items.map((item, index) => {
        const isLast = index === items.length - 1;
        const Icon = item.isTray ? Layers : Folder;

        return (
          <div key={item.id} className="flex items-center gap-1.5">
            {index > 0 && (
              <ChevronRight className="h-3.5 w-3.5 text-gray-400 shrink-0" />
            )}
            {isLast ? (
              <span
                className="flex items-center gap-1.5 font-semibold text-gray-900 dark:text-white"
                aria-current="location"
              >
                <Icon className="h-3.5 w-3.5 text-indigo-600 dark:text-indigo-400 shrink-0" />
                <span className="truncate max-w-[200px]">{item.name}</span>
              </span>
            ) : (
              <button
                type="button"
                onClick={() => onNavigate(item)}
                className="flex items-center gap-1.5 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors focus:outline-none focus-visible:underline"
                title={`Go back to ${item.name}`}
              >
                <Icon className="h-3.5 w-3.5 text-gray-400 shrink-0" />
                <span className="truncate max-w-[160px]">{item.name}</span>
              </button>
            )}
          </div>
        );
      })}
    </nav>
  );
}

