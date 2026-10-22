import { useState, useEffect, useRef, useCallback } from "react";
import { randomUUID } from "../lib/uuid";
import { X, Send, Sparkles } from "lucide-react";
import { AiMessage } from "./AiMessage";
import { queryAi, type AiSourceChunk } from "../services/aiService";
import { notify } from "../lib/notifications";

interface ChatMessage {
  id: string;
  role: "user" | "ai";
  content: string;
  sources?: AiSourceChunk[];
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

    const userMessage: ChatMessage = {
      id: randomUUID(),
      role: "user",
      content: question,
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setIsQuerying(true);

    try {
      const response = await queryAi(slug, { question, documentId }, token);

      const aiMessage: ChatMessage = {
        id: randomUUID(),
        role: "ai",
        content: response.answer,
        sources: response.sources,
      };

      setMessages((prev) => [...prev, aiMessage]);
    } catch (err) {
      const errorText = err instanceof Error ? err.message : "Something went wrong";
      notify.error("AI query failed", errorText);

      const errorMessage: ChatMessage = {
        id: randomUUID(),
        role: "ai",
        content: `I couldn't process that question. ${errorText}`,
      };

      setMessages((prev) => [...prev, errorMessage]);
    } finally {
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
      {/* Backdrop overlay */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/10 backdrop-blur-[1px] transition-opacity duration-300 md:hidden"
          onClick={onClose}
        />
      )}

      {/* Panel */}
      <aside
        className={`fixed right-0 top-16 bottom-0 z-50 flex w-full flex-col border-l border-gray-200 bg-white shadow-2xl transition-transform duration-300 ease-out dark:border-gray-800 dark:bg-gray-900 sm:w-[400px] ${
          isOpen ? "translate-x-0" : "translate-x-full"
        }`}
        aria-label="AI Chat Panel"
        role="complementary"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-gray-100 dark:border-gray-800 px-4 py-3">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400">
              <Sparkles className="h-4 w-4" />
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-gray-900 dark:text-white">
                Ask AI
              </p>
              <p className="truncate text-[11px] text-gray-500 dark:text-gray-400">
                {documentName}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200 transition-colors"
            aria-label="Close AI panel"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Messages area */}
        <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
          {/* Welcome message */}
          {messages.length === 0 && !isQuerying && (
            <div className="flex flex-col items-center justify-center h-full text-center px-4">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 mb-4">
                <Sparkles className="h-7 w-7" />
              </div>
              <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-1.5">
                Ask me anything
              </h3>
              <p className="text-xs text-gray-500 dark:text-gray-400 max-w-[260px] leading-relaxed">
                Ask questions about <span className="font-medium text-gray-700 dark:text-gray-300">{documentName}</span> and get AI-powered answers with source references.
              </p>
              <div className="mt-5 flex flex-wrap justify-center gap-2">
                {["Summarize this document", "What are the key points?", "List the main topics"].map((suggestion) => (
                  <button
                    key={suggestion}
                    type="button"
                    onClick={() => {
                      setInput(suggestion);
                      inputRef.current?.focus();
                    }}
                    className="rounded-full border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-1.5 text-[11px] font-medium text-gray-600 dark:text-gray-300 hover:border-indigo-300 hover:text-indigo-600 dark:hover:border-indigo-700 dark:hover:text-indigo-400 transition-colors"
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Chat messages */}
          {messages.map((msg) => (
            <AiMessage
              key={msg.id}
              role={msg.role}
              content={msg.content}
              sources={msg.sources}
            />
          ))}

          {/* Loading indicator */}
          {isQuerying && (
            <AiMessage role="ai" content="" isLoading />
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input area */}
        <div className="border-t border-gray-100 dark:border-gray-800 px-4 py-3">
          <div className="flex items-end gap-2">
            <div className="relative flex-1">
              <textarea
                ref={inputRef}
                value={input}
                onChange={handleInputChange}
                onKeyDown={handleKeyDown}
                placeholder="Ask a question..."
                disabled={isQuerying}
                rows={1}
                maxLength={1000}
                className="w-full resize-none rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 px-3.5 py-2.5 text-sm text-gray-900 dark:text-white placeholder-gray-400 outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100 dark:focus:ring-indigo-950/60 transition-colors disabled:opacity-50"
              />
              {input.length > 800 && (
                <span className={`absolute bottom-1 right-2 text-[10px] ${input.length > 950 ? "text-red-500" : "text-gray-400"}`}>
                  {input.length}/1000
                </span>
              )}
            </div>
            <button
              type="button"
              onClick={handleSend}
              disabled={!input.trim() || isQuerying}
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-xs hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              aria-label="Send message"
            >
              <Send className="h-4 w-4" />
            </button>
          </div>
          <p className="mt-1.5 text-[10px] text-gray-400 dark:text-gray-500">
            Enter to send · Shift+Enter for new line · Esc to close
          </p>
        </div>
      </aside>
    </>
  );
}
