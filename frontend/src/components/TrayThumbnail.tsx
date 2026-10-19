import { Layers, Trash2 } from "lucide-react";
import type { Tray } from "../services/trayService";

interface TrayThumbnailProps {
  tray: Tray;
  className?: string;
}

export function TrayThumbnail({ tray, className = "" }: TrayThumbnailProps) {
  const isTrash = tray.id === "trash" || tray.name.toLowerCase() === "trash";

  return (
    <div
      className={`relative group overflow-hidden rounded-xl border ${
        isTrash
          ? "border-red-200/80 dark:border-red-900/50 bg-gradient-to-br from-red-50 to-rose-50/50 dark:from-red-950/40 dark:to-rose-950/20"
          : "border-indigo-200/80 dark:border-indigo-900/50 bg-gradient-to-br from-indigo-50 to-blue-50/50 dark:from-indigo-950/40 dark:to-blue-950/20"
      } p-4 flex flex-col items-center justify-center select-none transition-all duration-200 ${className}`}
    >
      {/* Tray illustration */}
      <div className="relative flex items-center justify-center">
        {/* Glow behind */}
        <div
          className={`absolute inset-0 ${
            isTrash ? "bg-red-400/20" : "bg-indigo-400/20"
          } blur-xl rounded-full transform group-hover:scale-125 transition-transform`}
        />

        {/* Tray graphic container */}
        <div
          className={`relative flex h-16 w-20 items-center justify-center rounded-2xl ${
            isTrash
              ? "bg-red-100/80 dark:bg-red-900/40 text-red-600 dark:text-red-400 border-red-300/60 dark:border-red-800"
              : "bg-indigo-100/80 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 border-indigo-300/60 dark:border-indigo-800"
          } border shadow-2xs group-hover:scale-105 group-hover:shadow-md transition-all`}
        >
          {isTrash ? (
            <Trash2 className="h-9 w-9 text-red-600 dark:text-red-300 drop-shadow-xs" />
          ) : (
            <Layers className="h-9 w-9 text-indigo-600 dark:text-indigo-300 drop-shadow-xs" />
          )}

          {/* Mini item count tag */}
          {tray.itemCount !== undefined && (
            <span
              className={`absolute -bottom-1.5 -right-1.5 rounded-md ${
                isTrash ? "bg-red-600 text-white" : "bg-indigo-600 text-white"
              } px-1.5 py-0.5 text-[9px] font-bold shadow-xs`}
            >
              {tray.itemCount}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
