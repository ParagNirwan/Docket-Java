import { useEffect, useRef, useState } from "react";
import type { LucideIcon } from "lucide-react";
import { MoreVertical } from "lucide-react";

export interface ItemAction {
  label: string;
  icon: LucideIcon;
  danger?: boolean;
  onSelect: () => void;
}

interface ItemActionsMenuProps {
  itemName: string;
  actions: ItemAction[];
  // Which way to open, for when the item sits near the edge of a card.
  openUp?: boolean;
}

// The "⋮" menu on folders and documents. Shows nothing if there's nothing the person can do.
export function ItemActionsMenu({ itemName, actions, openUp = false }: ItemActionsMenuProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function handleOutside(e: MouseEvent) {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", handleOutside);
    window.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleOutside);
      window.removeEventListener("keydown", handleEscape);
    };
  }, [open]);

  if (actions.length === 0) return null;

  return (
    <div ref={containerRef} className="relative" onClick={(e) => e.stopPropagation()}>
      <button
        type="button"
        onClick={() => setOpen((previous) => !previous)}
        className="p-1 rounded-lg text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
        title="More actions"
        aria-label={`More actions for ${itemName}`}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <MoreVertical className="h-3.5 w-3.5" />
      </button>
      {open && (
        <div
          role="menu"
          className={`absolute right-0 w-40 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-xl p-1 z-30 text-left ${openUp ? "bottom-full mb-1" : "top-full mt-1"}`}
        >
          {actions.map((action) => {
            const Icon = action.icon;
            return (
              <button
                key={action.label}
                type="button"
                role="menuitem"
                onClick={() => {
                  setOpen(false);
                  action.onSelect();
                }}
                className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-xs font-medium transition-colors ${
                  action.danger
                    ? "text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40"
                    : "text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800"
                }`}
              >
                <Icon className="h-3.5 w-3.5" />
                <span>{action.label}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
