import { useState } from "react";
import ReactMarkdown from "react-markdown";
import type { Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import { Bot, ChevronDown, ChevronUp, FileText, ThumbsDown, ThumbsUp } from "lucide-react";
import type { AiSourceChunk } from "../services/aiService";

interface AiMessageProps {
  role: "user" | "ai";
  content: string;
  sources?: AiSourceChunk[];
  isLoading?: boolean;
  onFeedback?: (rating: -1 | 1, reason?: string) => Promise<void>;
  onCitationClick?: (source: AiSourceChunk) => void;
}

function sourceLocation({ pageStart, pageEnd, heading }: AiSourceChunk): string {
  let page = "";
  if (pageStart != null) {
    page = pageEnd != null && pageEnd !== pageStart ? `pp. ${pageStart}–${pageEnd}` : `p. ${pageStart}`;
  }
  return [page, heading].filter(Boolean).join(" · ");
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
                  {i + 1}
                </span>
                <span className="truncate font-medium text-gray-700 dark:text-gray-300">
                  {source.documentName}
                </span>
                {sourceLocation(source) && (
                  <span className="ml-auto shrink-0 text-[10px] text-gray-400 dark:text-gray-500">
                    {sourceLocation(source)}
                  </span>
                )}
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

export function AiMessage({
  role,
  content,
  sources,
  isLoading,
  onFeedback,
  onCitationClick,
}: AiMessageProps) {
  const [feedback, setFeedback] = useState<-1 | 1 | null>(null);
  const [reason, setReason] = useState("");
  const [feedbackError, setFeedbackError] = useState("");
  const [feedbackSaved, setFeedbackSaved] = useState(false);
  const markdownComponents: Components = {
    a: ({ href, children }) => {
      const match = href?.match(/^#citation-(\d+)$/);
      const source = match && sources ? sources[Number(match[1]) - 1] : undefined;
      if (!source || !onCitationClick) {
        return <a href={href}>{children}</a>;
      }
      const descriptionId = `citation-${source.documentId}-${source.chunkIndex}`;
      const location = sourceLocation(source);
      return (
        <button
          type="button"
          onClick={() => onCitationClick(source)}
          title={[source.documentName, location, source.chunkText].filter(Boolean).join(" · ")}
          aria-label={`Open ${source.documentName}${location ? `, ${location}` : ""}`}
          aria-describedby={descriptionId}
          className="inline-flex items-center rounded bg-indigo-100 px-1 text-[0.85em] font-semibold text-indigo-700 hover:bg-indigo-200 dark:bg-indigo-950/70 dark:text-indigo-300"
        >
          {children}
          <span id={descriptionId} className="sr-only">
            {source.chunkText}
          </span>
        </button>
      );
    },
  };
  const citationMarkdown = sources?.length && onCitationClick
    ? content.replace(/\[(\d+)\]/g, (marker, number: string) =>
        sources[Number(number) - 1] ? `[${number}](#citation-${number})` : marker)
    : content;

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
              <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
                {citationMarkdown}
              </ReactMarkdown>
            </div>
          )}
        </div>
        {sources && sources.length > 0 && !isLoading && (
          <SourceChips sources={sources} />
        )}
        {onFeedback && !isLoading && (
          <div className="mt-2 flex items-center gap-2">
            <button
              type="button"
              aria-label="Helpful answer"
              aria-pressed={feedback === 1}
              disabled={feedback !== null}
              onClick={() => void onFeedback(1)
                .then(() => {
                  setFeedback(1);
                  setFeedbackSaved(true);
                })
                .catch(() => setFeedbackError("Feedback could not be saved."))}
              className="rounded p-1 text-gray-400 hover:text-indigo-600 disabled:opacity-70"
            >
              <ThumbsUp className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              aria-label="Unhelpful answer"
              aria-pressed={feedback === -1}
              disabled={feedback !== null}
              onClick={() => setFeedback(-1)}
              className="rounded p-1 text-gray-400 hover:text-indigo-600 disabled:opacity-70"
            >
              <ThumbsDown className="h-3.5 w-3.5" />
            </button>
            {feedback === -1 && (
              <>
                <select
                  aria-label="Feedback reason"
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  className="rounded border border-gray-200 bg-white px-1.5 py-1 text-[10px] text-gray-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                >
                  <option value="">Optional reason</option>
                  <option value="wrong source">Wrong source</option>
                  <option value="incomplete">Incomplete</option>
                  <option value="hallucinated">Hallucinated</option>
                </select>
                <button
                  type="button"
                  disabled={feedbackSaved}
                  onClick={() => void onFeedback(-1, reason || undefined)
                    .then(() => {
                      setFeedbackError("");
                      setFeedbackSaved(true);
                    })
                    .catch(() => setFeedbackError("Feedback could not be saved."))}
                  className="text-[10px] font-medium text-indigo-600 hover:text-indigo-700"
                >
                  {feedbackSaved ? "Thanks" : "Send"}
                </button>
              </>
            )}
            {feedback === 1 && <span className="text-[10px] text-gray-400">Thanks for the feedback</span>}
            {feedbackError && <span role="alert" className="text-[10px] text-red-500">{feedbackError}</span>}
          </div>
        )}
      </div>
    </div>
  );
}
