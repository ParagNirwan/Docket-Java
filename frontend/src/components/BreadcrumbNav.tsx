import type { DOMAttributes } from "react";
import { ChevronRight, Folder, Layers } from "lucide-react";
import type { BreadcrumbNode } from "../services/trayService";

interface BreadcrumbNavProps {
  items: BreadcrumbNode[];
  onNavigate: (node: BreadcrumbNode) => void;
  // Lets folders and documents be dragged up to a parent folder.
  dropProps?: (node: BreadcrumbNode) => DOMAttributes<HTMLButtonElement>;
  dropTargetId?: string | null;
}

export function BreadcrumbNav({ items, onNavigate, dropProps, dropTargetId }: BreadcrumbNavProps) {
  if (items.length === 0) return null;

  return (
    <nav
      aria-label="Folder Breadcrumb"
      className="flex items-center flex-wrap gap-1.5 text-[12px] text-[#616061] py-1"
    >
      {items.map((item, index) => {
        const isLast = index === items.length - 1;
        const Icon = item.isTray ? Layers : Folder;

        return (
          <div key={item.id} className="flex items-center gap-1.5">
            {index > 0 && (
              <ChevronRight className="h-3.5 w-3.5 text-[#DDDDDD] shrink-0" />
            )}
            {isLast ? (
              <span
                className="flex items-center gap-1.5 font-bold text-[#1D1C1D]"
                aria-current="location"
              >
                <Icon className="h-3.5 w-3.5 text-[#1164A3] shrink-0" />
                <span className="truncate max-w-[200px]">{item.name}</span>
              </span>
            ) : (
              <button
                type="button"
                onClick={() => onNavigate(item)}
                {...dropProps?.(item)}
                className={`flex items-center gap-1.5 rounded hover:text-[#1164A3] transition-colors focus:outline-none focus-visible:underline ${dropTargetId === item.id ? "ring-2 ring-[#36C5F0]" : ""}`}
                title={`Go back to ${item.name}`}
              >
                <Icon className="h-3.5 w-3.5 text-[#616061] shrink-0" />
                <span className="truncate max-w-[160px]">{item.name}</span>
              </button>
            )}
          </div>
        );
      })}
    </nav>
  );
}

