import { getFileTypeInfo } from "../lib/fileTypeUtils";

interface DocumentThumbnailProps {
  filename: string;
  contentType?: string;
  className?: string;
}

export function DocumentThumbnail({
  filename,
  contentType,
  className = "",
}: DocumentThumbnailProps) {
  const typeInfo = getFileTypeInfo(filename, contentType);
  const Icon = typeInfo.icon;

  return (
    <div
      className={`relative group overflow-hidden rounded-xl border ${typeInfo.borderClass} ${typeInfo.bgClass} flex flex-col items-center justify-center select-none transition-all duration-200 ${className}`}
    >
      {/* Visual Mock representation based on Category */}
      {typeInfo.category === "pdf" && (
        <div className="relative w-20 h-24 bg-white dark:bg-gray-800 rounded-sm shadow-xs border border-rose-200 dark:border-rose-900/60 p-2 flex flex-col justify-between overflow-hidden">
          {/* Top red header */}
          <div className="flex items-center justify-between pb-1 border-b border-rose-100 dark:border-rose-900/40">
            <span className="text-[9px] font-black tracking-wider text-rose-600 dark:text-rose-400">
              PDF
            </span>
            <div className="h-1.5 w-1.5 rounded-full bg-rose-500" />
          </div>
          {/* Mock document lines */}
          <div className="space-y-1.5 my-1">
            <div className="h-1 w-full bg-rose-200/80 dark:bg-rose-900/40 rounded-full" />
            <div className="h-1 w-4/5 bg-gray-200 dark:bg-gray-700 rounded-full" />
            <div className="h-1 w-full bg-gray-200 dark:bg-gray-700 rounded-full" />
            <div className="h-1 w-2/3 bg-gray-200 dark:bg-gray-700 rounded-full" />
          </div>
          {/* Watermark icon */}
          <Icon className="h-5 w-5 text-rose-500/30 self-end" />
          {/* Folded corner */}
          <div className="absolute top-0 right-0 w-3 h-3 bg-rose-100 dark:bg-rose-950/80 border-b border-l border-rose-200 dark:border-rose-800" />
        </div>
      )}

      {typeInfo.category === "sheet" && (
        <div className="relative w-20 h-24 bg-white dark:bg-gray-800 rounded-sm shadow-xs border border-emerald-200 dark:border-emerald-900/60 p-2 flex flex-col justify-between overflow-hidden">
          {/* Header */}
          <div className="flex items-center justify-between pb-1 border-b border-emerald-100 dark:border-emerald-900/40">
            <span className="text-[9px] font-black tracking-wider text-emerald-600 dark:text-emerald-400">
              {typeInfo.extension}
            </span>
            <div className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
          </div>
          {/* Mock spreadsheet grid */}
          <div className="grid grid-cols-3 gap-0.5 my-1 p-0.5 bg-emerald-50/50 dark:bg-emerald-950/30 rounded border border-emerald-100 dark:border-emerald-900/30 text-[6px]">
            <div className="h-2 bg-emerald-100 dark:bg-emerald-900/50 rounded-2xs" />
            <div className="h-2 bg-emerald-100 dark:bg-emerald-900/50 rounded-2xs" />
            <div className="h-2 bg-emerald-100 dark:bg-emerald-900/50 rounded-2xs" />
            <div className="h-2 bg-gray-100 dark:bg-gray-700 rounded-2xs" />
            <div className="h-2 bg-gray-100 dark:bg-gray-700 rounded-2xs" />
            <div className="h-2 bg-gray-100 dark:bg-gray-700 rounded-2xs" />
            <div className="h-2 bg-gray-100 dark:bg-gray-700 rounded-2xs" />
            <div className="h-2 bg-gray-100 dark:bg-gray-700 rounded-2xs" />
            <div className="h-2 bg-gray-100 dark:bg-gray-700 rounded-2xs" />
          </div>
          <Icon className="h-5 w-5 text-emerald-500/30 self-end" />
        </div>
      )}

      {typeInfo.category === "doc" && (
        <div className="relative w-20 h-24 bg-white dark:bg-gray-800 rounded-sm shadow-xs border border-blue-200 dark:border-blue-900/60 p-2 flex flex-col justify-between overflow-hidden">
          {/* Header */}
          <div className="flex items-center justify-between pb-1 border-b border-blue-100 dark:border-blue-900/40">
            <span className="text-[9px] font-black tracking-wider text-blue-600 dark:text-blue-400">
              DOC
            </span>
            <div className="h-1.5 w-1.5 rounded-full bg-blue-500" />
          </div>
          {/* Document lines */}
          <div className="space-y-1.5 my-1">
            <div className="h-1 w-3/4 bg-blue-200/80 dark:bg-blue-900/40 rounded-full" />
            <div className="h-1 w-full bg-gray-200 dark:bg-gray-700 rounded-full" />
            <div className="h-1 w-full bg-gray-200 dark:bg-gray-700 rounded-full" />
            <div className="h-1 w-4/5 bg-gray-200 dark:bg-gray-700 rounded-full" />
          </div>
          <Icon className="h-5 w-5 text-blue-500/30 self-end" />
          <div className="absolute top-0 right-0 w-3 h-3 bg-blue-100 dark:bg-blue-950/80 border-b border-l border-blue-200 dark:border-blue-800" />
        </div>
      )}

      {typeInfo.category === "image" && (
        <div className="relative w-20 h-24 bg-gradient-to-br from-purple-100 to-indigo-50 dark:from-purple-950/50 dark:to-indigo-950/40 rounded-sm shadow-xs border border-purple-200 dark:border-purple-900/60 p-2 flex flex-col items-center justify-center overflow-hidden">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-purple-200/60 dark:bg-purple-900/60 text-purple-700 dark:text-purple-300 mb-1">
            <Icon className="h-6 w-6" />
          </div>
          <span className="text-[9px] font-bold tracking-wider text-purple-700 dark:text-purple-300">
            {typeInfo.extension}
          </span>
        </div>
      )}

      {typeInfo.category === "archive" && (
        <div className="relative w-20 h-24 bg-white dark:bg-gray-800 rounded-sm shadow-xs border border-amber-200 dark:border-amber-900/60 p-2 flex flex-col justify-between overflow-hidden">
          <div className="flex items-center justify-between pb-1 border-b border-amber-100 dark:border-amber-900/40">
            <span className="text-[9px] font-black tracking-wider text-amber-600 dark:text-amber-400">
              ZIP
            </span>
            <div className="h-1.5 w-1.5 rounded-full bg-amber-500" />
          </div>
          <div className="flex flex-col items-center justify-center my-1 text-amber-500/40">
            <Icon className="h-8 w-8 text-amber-600 dark:text-amber-400" />
          </div>
          <div className="h-1 w-full bg-amber-200 dark:bg-amber-900/40 rounded-full" />
        </div>
      )}

      {typeInfo.category === "code" && (
        <div className="relative w-20 h-24 bg-slate-900 rounded-sm shadow-xs border border-slate-700 p-2 flex flex-col justify-between overflow-hidden text-slate-300">
          <div className="flex items-center justify-between pb-1 border-b border-slate-800">
            <span className="text-[9px] font-mono font-bold text-sky-400">
              {typeInfo.extension}
            </span>
            <div className="flex gap-0.5">
              <div className="h-1 w-1 rounded-full bg-red-400" />
              <div className="h-1 w-1 rounded-full bg-amber-400" />
              <div className="h-1 w-1 rounded-full bg-emerald-400" />
            </div>
          </div>
          <div className="space-y-1 font-mono text-[6px] text-slate-400 py-1">
            <div className="text-emerald-400 font-bold">&lt;doc&gt;</div>
            <div className="pl-1.5 h-1 w-3/4 bg-slate-700 rounded-2xs" />
            <div className="pl-3 h-1 w-1/2 bg-slate-700 rounded-2xs" />
            <div className="text-emerald-400 font-bold">&lt;/doc&gt;</div>
          </div>
          <Icon className="h-4 w-4 text-slate-600 self-end" />
        </div>
      )}

      {typeInfo.category === "other" && (
        <div className="relative w-20 h-24 bg-white dark:bg-gray-800 rounded-sm shadow-xs border border-gray-200 dark:border-gray-700 p-2 flex flex-col items-center justify-center overflow-hidden">
          <Icon className="h-8 w-8 text-indigo-500 mb-1" />
          <span className="text-[9px] font-bold tracking-wider text-gray-500 dark:text-gray-400">
            {typeInfo.extension}
          </span>
        </div>
      )}

    </div>
  );
}
