import { useState } from "react";
import ReactMarkdown from "react-markdown";
import type { Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  Bot,
  User,
  ChevronDown,
  ChevronUp,
  FileText,
  ThumbsDown,
  ThumbsUp,
  Smile,
  MessageSquare,
  Bookmark,
  Check,
} from "lucide-react";
import type { AiSourceChunk } from "../services/aiService";

interface AiMessageProps {
  role: "user" | "ai";
  content: string;
  sources?: AiSourceChunk[];
  isLoading?: boolean;
  timestamp?: string;
  userName?: string;
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
    <div className="mt-2 space-y-1.5">
      <button
        type="button"
        onClick={() => setExpanded(expanded !== null ? null : 0)}
        className="inline-flex items-center gap-1.5 rounded-[4px] border border-[#DDDDDD] bg-white px-2 py-0.5 text-[11px] font-medium text-[#616061] hover:bg-[#F8F8F8] hover:text-[#1D1C1D] transition-colors"
      >
        <FileText className="h-3 w-3 text-[#36C5F0]" />
        <span>{sources.length} source{sources.length !== 1 ? "s" : ""}</span>
        {expanded !== null ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
      </button>

      {expanded !== null && (
        <div className="space-y-1 rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] p-2 mt-1">
          {sources.map((source, i) => (
            <button
              key={`${source.documentId}-${source.chunkIndex}`}
              type="button"
              onClick={() => setExpanded(expanded === i ? null : i)}
              className="w-full text-left"
            >
              <div className="flex items-center gap-2 rounded-[4px] px-2 py-1.5 text-[11px] hover:bg-white transition-colors">
                <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-[4px] bg-[#36C5F0]/20 text-[9px] font-bold text-[#1164A3]">
                  {i + 1}
                </span>
                <span className="truncate font-semibold text-[#1D1C1D]">
                  {source.documentName}
                </span>
                {sourceLocation(source) && (
                  <span className="ml-auto shrink-0 text-[10px] text-[#616061]">
                    {sourceLocation(source)}
                  </span>
                )}
              </div>
              {expanded === i && (
                <p className="mt-1 rounded-[4px] bg-white border border-[#DDDDDD] px-3 py-2 text-[11px] leading-relaxed text-[#616061]">
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
  timestamp,
  userName,
  onFeedback,
  onCitationClick,
}: AiMessageProps) {
  const [feedback, setFeedback] = useState<-1 | 1 | null>(null);
  const [reason, setReason] = useState("");
  const [feedbackError, setFeedbackError] = useState("");
  const [feedbackSaved, setFeedbackSaved] = useState(false);
  const [bookmarked, setBookmarked] = useState(false);
  const [reactions, setReactions] = useState<{ emoji: string; count: number }[]>([]);

  const defaultTimestamp = timestamp || "Just now";
  const displayName = userName || (role === "user" ? "You" : "Docket AI");

  const markdownComponents: Components = {
    a: ({ href, children }) => {
      const match = href?.match(/^#citation-(\d+)$/);
      const source = match && sources ? sources[Number(match[1]) - 1] : undefined;
      if (!source || !onCitationClick) {
        return (
          <a href={href} className="text-[#36C5F0] hover:underline font-medium">
            {children}
          </a>
        );
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
          className="inline-flex items-center rounded-[4px] bg-[#36C5F0]/20 px-1 text-[0.85em] font-semibold text-[#1164A3] hover:bg-[#36C5F0]/30 transition-colors"
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

  function handleAddReaction(emoji: string) {
    setReactions((prev) => {
      const existing = prev.find((r) => r.emoji === emoji);
      if (existing) {
        return prev.map((r) => (r.emoji === emoji ? { ...r, count: r.count + 1 } : r));
      }
      return [...prev, { emoji, count: 1 }];
    });
  }

  return (
    <div className="group relative flex gap-3 px-4 py-2 hover:bg-[#F8F8F8] rounded-[8px] transition-colors duration-150">
      {/* Action Toolbar on Hover (Slack style: emoji, thread, bookmark) */}
      <div className="absolute right-3 -top-3 hidden group-hover:flex items-center gap-0.5 rounded-[4px] border border-[#DDDDDD] bg-white px-1 py-0.5 shadow-[0_1px_2px_rgba(0,0,0,0.1)] z-10">
        <button
          type="button"
          onClick={() => handleAddReaction("👍")}
          title="React with 👍"
          aria-label="React with thumbs up"
          className="rounded-[4px] p-1 text-[#616061] hover:bg-[#F8F8F8] hover:text-[#1D1C1D] transition-colors"
        >
          <Smile className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          onClick={() => handleAddReaction("👀")}
          title="Reply in thread"
          aria-label="Reply in thread"
          className="rounded-[4px] p-1 text-[#616061] hover:bg-[#F8F8F8] hover:text-[#1D1C1D] transition-colors"
        >
          <MessageSquare className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          onClick={() => setBookmarked((prev) => !prev)}
          title={bookmarked ? "Remove bookmark" : "Add to bookmarks"}
          aria-label="Bookmark message"
          className={`rounded-[4px] p-1 transition-colors ${
            bookmarked ? "text-[#ECB22E]" : "text-[#616061] hover:bg-[#F8F8F8] hover:text-[#1D1C1D]"
          }`}
        >
          <Bookmark className="h-3.5 w-3.5" />
        </button>
      </div>

      {/* Left: 36px Avatar (Slack standard) */}
      <div className="shrink-0 pt-0.5">
        {role === "user" ? (
          <div className="flex h-9 w-9 items-center justify-center rounded-[8px] bg-[#1D1C1D] text-white shadow-sm font-bold text-sm">
            <User className="h-5 w-5" />
          </div>
        ) : (
          <div className="flex h-9 w-9 items-center justify-center rounded-[8px] bg-[#ECB22E] text-[#1D1C1D] shadow-sm font-bold text-sm">
            <Bot className="h-5 w-5" />
          </div>
        )}
      </div>

      {/* Right: Username (bold) + Timestamp (<=12px), Message body, Reactions */}
      <div className="min-w-0 flex-1">
        {/* Username + Timestamp row */}
        <div className="flex items-baseline gap-2 mb-1">
          <span className="font-bold text-[15px] leading-tight text-[#1D1C1D]">
            {displayName}
          </span>
          <span className="text-[12px] font-normal leading-[1.4] text-[#616061]">
            {defaultTimestamp}
          </span>
        </div>

        {/* Message Body */}
        {isLoading ? (
          <div className="flex items-center gap-1.5 py-1 text-[#616061]">
            <span className="h-2 w-2 rounded-full bg-[#616061] animate-bounce [animation-delay:0ms]" />
            <span className="h-2 w-2 rounded-full bg-[#616061] animate-bounce [animation-delay:150ms]" />
            <span className="h-2 w-2 rounded-full bg-[#616061] animate-bounce [animation-delay:300ms]" />
          </div>
        ) : (
          <div className="text-[15px] leading-[1.46] text-[#1D1C1D] space-y-1 [&_p]:my-0.5 [&_ul]:my-1 [&_ol]:my-1 [&_li]:my-0.5 [&_pre]:my-2 [&_pre]:rounded-[8px] [&_pre]:bg-[#1D1C1D] [&_pre]:text-white [&_pre]:p-3 [&_code]:rounded-[4px] [&_code]:bg-[#F8F8F8] [&_code]:border [&_code]:border-[#DDDDDD] [&_code]:px-1.5 [&_code]:py-0.5 [&_code]:text-xs [&_pre_code]:bg-transparent [&_pre_code]:border-0 [&_pre_code]:p-0">
            <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
              {citationMarkdown}
            </ReactMarkdown>
          </div>
        )}

        {/* Sources Row */}
        {sources && sources.length > 0 && !isLoading && (
          <SourceChips sources={sources} />
        )}

        {/* Reactions row at bottom */}
        {reactions.length > 0 && (
          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            {reactions.map((r, i) => (
              <button
                key={i}
                type="button"
                onClick={() => handleAddReaction(r.emoji)}
                className="inline-flex items-center gap-1 rounded-[4px] border border-[#DDDDDD] bg-white px-2 py-0.5 text-xs text-[#1D1C1D] hover:bg-[#F8F8F8] transition-colors"
              >
                <span>{r.emoji}</span>
                <span className="text-[11px] font-bold text-[#616061]">{r.count}</span>
              </button>
            ))}
          </div>
        )}

        {/* Feedback Row (for AI responses) */}
        {onFeedback && !isLoading && role === "ai" && (
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
              className="rounded-[4px] p-1 text-[#616061] hover:text-[#2EB67D] hover:bg-white border border-transparent hover:border-[#DDDDDD] transition-colors disabled:opacity-70"
            >
              <ThumbsUp className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              aria-label="Unhelpful answer"
              aria-pressed={feedback === -1}
              disabled={feedback !== null}
              onClick={() => setFeedback(-1)}
              className="rounded-[4px] p-1 text-[#616061] hover:text-[#E01E5A] hover:bg-white border border-transparent hover:border-[#DDDDDD] transition-colors disabled:opacity-70"
            >
              <ThumbsDown className="h-3.5 w-3.5" />
            </button>
            {feedback === -1 && (
              <>
                <select
                  aria-label="Feedback reason"
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  className="rounded-[4px] border border-[#DDDDDD] bg-white px-2 py-1 text-[11px] text-[#1D1C1D] outline-none"
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
                  className="rounded-[4px] px-2 py-0.5 text-[11px] font-semibold text-[#1164A3] hover:underline"
                >
                  {feedbackSaved ? "Thanks" : "Send"}
                </button>
              </>
            )}
            {feedback === 1 && (
              <span className="flex items-center gap-1 text-[11px] font-medium text-[#2EB67D]">
                <Check className="h-3 w-3" />
                Thanks for the feedback
              </span>
            )}
            {feedbackError && <span role="alert" className="text-[11px] text-[#E01E5A]">{feedbackError}</span>}
          </div>
        )}
      </div>
    </div>
  );
}
