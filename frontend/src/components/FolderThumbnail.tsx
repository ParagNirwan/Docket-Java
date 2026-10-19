import { Folder, FolderOpen } from "lucide-react";

interface FolderThumbnailProps {
  name: string;
  itemCount?: number;
  className?: string;
  isOpen?: boolean;
}

export function FolderThumbnail({
  name,
  itemCount,
  className = "",
  isOpen = false,
}: FolderThumbnailProps) {
  return (
    <div
      title={name}
      aria-label={name}
      className={`relative group overflow-hidden rounded-xl border border-amber-200/80 dark:border-amber-900/50 bg-gradient-to-br from-amber-50 to-orange-50/50 dark:from-amber-950/40 dark:to-orange-950/20 p-4 flex flex-col items-center justify-center select-none transition-all duration-200 ${className}`}
    >
      {/* Folder illustration */}
      <div className="relative flex items-center justify-center">
        {/* Glow behind */}
        <div className="absolute inset-0 bg-amber-400/20 dark:bg-amber-400/10 blur-xl rounded-full transform group-hover:scale-125 transition-transform" />

        {/* Folder graphic container */}
        <div className="relative flex h-16 w-20 items-center justify-center rounded-2xl bg-amber-100/80 dark:bg-amber-900/40 text-amber-600 dark:text-amber-400 border border-amber-300/60 dark:border-amber-800 shadow-2xs group-hover:scale-105 group-hover:shadow-md transition-all">
          {isOpen ? (
            <FolderOpen className="h-9 w-9 text-amber-600 dark:text-amber-300 drop-shadow-xs" />
          ) : (
            <Folder className="h-9 w-9 text-amber-600 dark:text-amber-400 drop-shadow-xs" />
          )}

          {/* Mini item count tag in bottom-right corner of folder */}
          {itemCount !== undefined && (
            <span className="absolute -bottom-1.5 -right-1.5 rounded-md bg-amber-600 text-white dark:bg-amber-500 dark:text-gray-950 px-1.5 py-0.5 text-[9px] font-bold shadow-xs">
              {itemCount}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
