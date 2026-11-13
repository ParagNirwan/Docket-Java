import { useState, useEffect, useRef, useCallback } from "react";
import { randomUUID } from "../lib/uuid";
import {
  X,
  Send,
  Sparkles,
  Square,
  FileText,
  Bold,
  Italic,
  Link2,
  List,
} from "lucide-react";
import { AiMessage } from "./AiMessage";
import { isAbortError, streamAiQuery, type AiSourceChunk } from "../services/aiService";
import { notify } from "../lib/notifications";

interface ChatMessage {
  id: string;
  role: "user" | "ai";
  content: string;
  sources?: AiSourceChunk[];
  timestamp?: string;
}

interface AiChatPanelProps {
  isOpen: boolean;
  onClose: () => void;
  slug: string;
  token: string | null;
  documentId: string;
  documentName: string;
}

export function AiChatPanel({
  isOpen,
  onClose,
  slug,
  token,
  documentId,
  documentName,
}: AiChatPanelProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [isQuerying, setIsQuerying] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const prevDocIdRef = useRef<string>("");
  const abortRef = useRef<AbortController | null>(null);

  // Leaving the panel's document, or the page, stops an answer nobody will read
  useEffect(() => () => abortRef.current?.abort(), [documentId]);

  const handleStop = useCallback(() => abortRef.current?.abort(), []);

  // Reset chat when document changes
  useEffect(() => {
    if (documentId && documentId !== prevDocIdRef.current) {
      setMessages([]);
      setInput("");
      prevDocIdRef.current = documentId;
    }
  }, [documentId]);

  // Auto-focus input when panel opens
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 350);
    }
  }, [isOpen]);

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView?.({ behavior: "smooth" });
  }, [messages]);

  // Escape key closes panel
  useEffect(() => {
    if (!isOpen) return;
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  const handleSend = useCallback(async () => {
    const question = input.trim();
    if (!question || !token || isQuerying) return;

    const timeStr = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

    const userMessage: ChatMessage = {
      id: randomUUID(),
      role: "user",
      content: question,
      timestamp: timeStr,
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setIsQuerying(true);

    // The answer fills this message in as it arrives
    const answerId = randomUUID();
    const updateAnswer = (change: (message: ChatMessage) => ChatMessage) =>
      setMessages((prev) => prev.map((message) => (message.id === answerId ? change(message) : message)));
    setMessages((prev) => [
      ...prev,
      { id: answerId, role: "ai", content: "", timestamp: timeStr },
    ]);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const response = await streamAiQuery(slug, { question, documentId }, token, {
        signal: controller.signal,
        onDelta: (text) => updateAnswer((message) => ({ ...message, content: message.content + text })),
      });

      updateAnswer((message) => ({ ...message, content: response.answer, sources: response.sources }));
    } catch (err) {
      if (isAbortError(err)) {
        updateAnswer((message) => ({ ...message, content: message.content || "Stopped." }));
        return;
      }
      const errorText = err instanceof Error ? err.message : "Something went wrong";
      notify.error("AI query failed", errorText);
      updateAnswer((message) => ({ ...message, content: `I couldn't process that question. ${errorText}` }));
    } finally {
      abortRef.current = null;
      setIsQuerying(false);
    }
  }, [input, token, isQuerying, slug, documentId]);

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  // Auto-resize textarea
  function handleInputChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    setInput(e.target.value);
    const el = e.target;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 120) + "px";
  }

  return (
    <>
      {/* Backdrop overlay for mobile */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/20 backdrop-blur-[1px] transition-opacity duration-300 md:hidden"
          onClick={onClose}
        />
      )}

      {/* Slack Thread Panel: Exactly 400px width on desktop */}
      <aside
        className={`fixed right-0 top-16 bottom-0 z-50 flex w-full flex-col border-l border-[#DDDDDD] bg-white shadow-[0_8px_30px_rgba(0,0,0,0.2)] transition-transform duration-200 ease-out sm:w-[400px] ${
          isOpen ? "translate-x-0" : "translate-x-full"
        }`}
        aria-label="AI Chat Panel"
        role="complementary"
      >
        {/* Thread Header */}
        <div className="flex h-14 items-center justify-between border-b border-[#DDDDDD] px-4 py-2 bg-white">
          <div className="flex items-center gap-2.5 min-w-0">
            <span className="font-bold text-[18px] text-[#1D1C1D] leading-tight">
              Thread
            </span>
            <span className="text-[#DDDDDD]">|</span>
            <div className="flex items-center gap-1.5 min-w-0 text-[#616061]">
              <FileText className="h-4 w-4 shrink-0 text-[#36C5F0]" />
              <p className="truncate text-[13px] font-medium text-[#616061]">
                {documentName}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] text-[#616061] hover:bg-[#F8F8F8] hover:text-[#1D1C1D] transition-colors"
            aria-label="Close AI panel"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Messages and Thread History */}
        <div className="flex-1 overflow-y-auto py-3 space-y-5 bg-white">
          {/* Original Message at Top (Slack thread requirement) */}
          <div className="border-b border-[#DDDDDD] pb-4">
            <div className="group relative flex gap-3 px-4 py-2 hover:bg-[#F8F8F8] rounded-[8px] transition-colors">
              <div className="shrink-0 pt-0.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-[8px] bg-[#4A154B] text-white shadow-sm font-bold text-sm">
                  <Sparkles className="h-5 w-5 text-[#ECB22E]" />
                </div>
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-2 mb-1">
                  <span className="font-bold text-[15px] leading-tight text-[#1D1C1D]">
                    Docket Assistant
                  </span>
                  <span className="text-[12px] font-normal leading-[1.4] text-[#616061]">
                    Document root
                  </span>
                </div>
                <div className="text-[15px] leading-[1.46] text-[#1D1C1D]">
                  <p>
                    Thread discussion for <span className="font-semibold text-[#1D1C1D]">{documentName}</span>. Ask any question or request extraction of clauses.
                  </p>
                </div>
              </div>
            </div>

            {/* Replies Divider */}
            <div className="relative mt-3 flex items-center px-4">
              <div className="flex-grow border-t border-[#DDDDDD]" />
              <span className="mx-2 shrink-0 text-[11px] font-bold uppercase tracking-wider text-[#616061]">
                {messages.length === 0 ? "No replies yet" : `${messages.length} ${messages.length === 1 ? "reply" : "replies"}`}
              </span>
              <div className="flex-grow border-t border-[#DDDDDD]" />
            </div>
          </div>

          {/* Welcome suggestions when no replies yet */}
          {messages.length === 0 && !isQuerying && (
            <div className="px-4 py-2">
              <p className="text-[13px] font-medium text-[#616061] mb-2.5">
                Quick questions to ask:
              </p>
              <div className="flex flex-col gap-1.5">
                {["Summarize this document", "What are the key terms?", "List all signatories and dates"].map((suggestion) => (
                  <button
                    key={suggestion}
                    type="button"
                    onClick={() => {
                      setInput(suggestion);
                      inputRef.current?.focus();
                    }}
                    className="w-full text-left rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3 py-2 text-[13px] text-[#1D1C1D] hover:bg-white hover:border-[#36C5F0] transition-colors"
                  >
                    💬 {suggestion}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Chat Messages / Replies List */}
          {messages.map((msg) => (
            <AiMessage
              key={msg.id}
              role={msg.role}
              content={msg.content}
              sources={msg.sources}
              timestamp={msg.timestamp}
              isLoading={msg.role === "ai" && msg.content === ""}
            />
          ))}

          <div ref={messagesEndRef} />
        </div>

        {/* Reply Input Pinned to Bottom (Slack rich thread input) */}
        <div className="border-t border-[#DDDDDD] bg-white p-3">
          <div className="rounded-[8px] border border-[#DDDDDD] bg-white focus-within:border-[#616061] focus-within:ring-1 focus-within:ring-[#616061] transition-all">
            {/* Rich Text Mini-Toolbar */}
            <div className="flex items-center gap-1 border-b border-[#DDDDDD]/60 px-2 py-1 bg-[#F8F8F8] rounded-t-[7px]">
              <button
                type="button"
                className="rounded-[4px] p-1 text-[#616061] hover:bg-white hover:text-[#1D1C1D] transition-colors"
                title="Bold"
                aria-label="Bold text"
              >
                <Bold className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                className="rounded-[4px] p-1 text-[#616061] hover:bg-white hover:text-[#1D1C1D] transition-colors"
                title="Italic"
                aria-label="Italic text"
              >
                <Italic className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                className="rounded-[4px] p-1 text-[#616061] hover:bg-white hover:text-[#1D1C1D] transition-colors"
                title="Link"
                aria-label="Add link"
              >
                <Link2 className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                className="rounded-[4px] p-1 text-[#616061] hover:bg-white hover:text-[#1D1C1D] transition-colors"
                title="List"
                aria-label="Add bullet list"
              >
                <List className="h-3.5 w-3.5" />
              </button>
            </div>

            {/* Input Textarea */}
            <textarea
              ref={inputRef}
              value={input}
              onChange={handleInputChange}
              onKeyDown={handleKeyDown}
              placeholder="Reply in thread..."
              disabled={isQuerying}
              rows={2}
              maxLength={1000}
              className="w-full resize-none bg-transparent px-3 py-2 text-[15px] leading-[1.46] text-[#1D1C1D] placeholder-[#616061] outline-none disabled:opacity-50"
            />

            {/* Bottom Actions Row */}
            <div className="flex items-center justify-between px-3 py-1.5 bg-white rounded-b-[7px]">
              <span className="text-[11px] text-[#616061]">
                Return to send · Shift + Return for new line
              </span>
              <div className="flex items-center gap-2">
                {isQuerying ? (
                  <button
                    type="button"
                    onClick={handleStop}
                    className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[4px] bg-[#E01E5A] text-white hover:bg-[#c0184b] transition-colors shadow-[0_1px_2px_rgba(0,0,0,0.1)]"
                    aria-label="Stop answering"
                    title="Stop"
                  >
                    <Square className="h-3 w-3 fill-current" />
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={handleSend}
                    disabled={!input.trim()}
                    className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[4px] bg-[#2EB67D] text-white hover:bg-[#259b69] disabled:opacity-30 disabled:cursor-not-allowed transition-colors shadow-[0_1px_2px_rgba(0,0,0,0.1)]"
                    aria-label="Send message"
                  >
                    <Send className="h-3.5 w-3.5" />
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      </aside>
    </>
  );
}
