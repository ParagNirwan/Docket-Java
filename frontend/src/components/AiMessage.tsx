import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Bot, ChevronDown, ChevronUp, FileText } from "lucide-react";
import type { AiSourceChunk } from "../services/aiService";

interface AiMessageProps {
  role: "user" | "ai";
  content: string;
  sources?: AiSourceChunk[];
  isLoading?: boolean;
}

function SourceChips({ sources }: { sources: AiSourceChunk[] }) {
  const [expanded, setExpanded] = useState<number | null>(null);

  if (sources.length === 0) return null;

  return (
    <div className="mt-2.5 space-y-1.5">
      <button
        type="button"
        onClick={() => setExpanded(expanded !== null ? null : 0)}
        className="flex items-center gap-1 text-[10px] font-semibold text-gray-400 dark:text-gray-500 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
      >
        <FileText className="h-3 w-3" />
        {sources.length} source{sources.length !== 1 ? "s" : ""}
        {expanded !== null ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
      </button>

      {expanded !== null && (
        <div className="space-y-1 rounded-lg border border-gray-100 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 p-2">
          {sources.map((source, i) => (
            <button
              key={`${source.documentId}-${source.chunkIndex}`}
              type="button"
              onClick={() => setExpanded(expanded === i ? null : i)}
              className="w-full text-left"
            >
              <div className="flex items-center gap-2 rounded-md px-2 py-1.5 text-[11px] hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors">
                <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded bg-indigo-100 dark:bg-indigo-950/60 text-[9px] font-bold text-indigo-600 dark:text-indigo-400">
                  {source.chunkIndex + 1}
                </span>
                <span className="truncate font-medium text-gray-700 dark:text-gray-300">
                  {source.documentName}
                </span>
              </div>
              {expanded === i && (
                <p className="mt-1 rounded-md bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 px-3 py-2 text-[11px] leading-relaxed text-gray-600 dark:text-gray-400">
                  {source.chunkText.length > 300
                    ? source.chunkText.slice(0, 300) + "..."
                    : source.chunkText}
                </p>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export function AiMessage({ role, content, sources, isLoading }: AiMessageProps) {
  if (role === "user") {
    return (
      <div className="flex justify-end">
        <div className="max-w-[85%] rounded-2xl rounded-br-md bg-indigo-600 px-4 py-2.5 text-sm text-white shadow-xs">
          {content}
        </div>
      </div>
    );
  }

  return (
    <div className="flex gap-2.5">
      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 mt-0.5">
        <Bot className="h-4 w-4" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="rounded-2xl rounded-tl-md bg-gray-100 dark:bg-gray-800 px-4 py-2.5 shadow-xs">
          {isLoading ? (
            <div className="flex items-center gap-1.5 py-1">
              <span className="h-2 w-2 rounded-full bg-gray-400 animate-bounce [animation-delay:0ms]" />
              <span className="h-2 w-2 rounded-full bg-gray-400 animate-bounce [animation-delay:150ms]" />
              <span className="h-2 w-2 rounded-full bg-gray-400 animate-bounce [animation-delay:300ms]" />
            </div>
          ) : (
            <div className="prose prose-sm dark:prose-invert max-w-none text-sm leading-relaxed [&_p]:my-1 [&_ul]:my-1.5 [&_ol]:my-1.5 [&_li]:my-0.5 [&_pre]:my-2 [&_pre]:rounded-lg [&_pre]:bg-gray-900 [&_pre]:p-3 [&_code]:rounded [&_code]:bg-gray-200 [&_code]:px-1 [&_code]:py-0.5 [&_code]:text-xs dark:[&_code]:bg-gray-700 [&_pre_code]:bg-transparent [&_pre_code]:p-0">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {content}
              </ReactMarkdown>
            </div>
          )}
        </div>
        {sources && sources.length > 0 && !isLoading && (
          <SourceChips sources={sources} />
        )}
      </div>
    </div>
  );
}
