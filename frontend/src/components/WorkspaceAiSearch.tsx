import { useState, useEffect, useRef, useCallback } from "react";
import {
  Sparkles,
  Send,
  Plus,
  Trash2,
  MessageSquare,
  ChevronLeft,
  Clock,
  Search,
} from "lucide-react";
import { AiMessage } from "./AiMessage";
import { notify } from "../lib/notifications";
import {
  listConversations,
  getConversation,
  createConversation,
  sendMessage,
  deleteConversation,
  type Conversation,
  type ConversationMessage,
} from "../services/conversationService";

interface WorkspaceAiSearchProps {
  slug: string;
  token: string | null;
}

export function WorkspaceAiSearch({ slug, token }: WorkspaceAiSearchProps) {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConversation, setActiveConversation] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<ConversationMessage[]>([]);
  const [input, setInput] = useState("");
  const [isQuerying, setIsQuerying] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [searchFilter, setSearchFilter] = useState("");

  const inputRef = useRef<HTMLTextAreaElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Load conversation list
  useEffect(() => {
    if (!token) return;
    setIsLoading(true);
    listConversations(slug, token)
      .then(setConversations)
      .catch(() => {
        // AI features may not be available (no gcp profile)
      })
      .finally(() => setIsLoading(false));
  }, [slug, token]);

  // Auto-scroll on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView?.({ behavior: "smooth" });
  }, [messages]);

  // Auto-focus input
  useEffect(() => {
    if (activeConversation || !isLoading) {
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  }, [activeConversation, isLoading]);

  const handleOpenConversation = useCallback(
    async (conv: Conversation) => {
      if (!token) return;
      try {
        const full = await getConversation(slug, conv.id, token);
        setActiveConversation(full);
        setMessages(full.messages || []);
      } catch (err) {
        notify.error("Failed to load conversation", err instanceof Error ? err.message : "Unknown error");
      }
    },
    [slug, token],
  );

  const handleNewConversation = useCallback(() => {
    setActiveConversation(null);
    setMessages([]);
    setInput("");
    setTimeout(() => inputRef.current?.focus(), 100);
  }, []);

  const handleDeleteConversation = useCallback(
    async (convId: string, e: React.MouseEvent) => {
      e.stopPropagation();
      if (!token) return;
      try {
        await deleteConversation(slug, convId, token);
        setConversations((prev) => prev.filter((c) => c.id !== convId));
        if (activeConversation?.id === convId) {
          handleNewConversation();
        }
        notify.success("Conversation deleted", "The conversation has been removed");
      } catch (err) {
        notify.error("Failed to delete", err instanceof Error ? err.message : "Unknown error");
      }
    },
    [slug, token, activeConversation, handleNewConversation],
  );

  const handleSend = useCallback(async () => {
    const question = input.trim();
    if (!question || !token || isQuerying) return;

    setInput("");
    setIsQuerying(true);

    // Optimistically add user message
    const tempUserMsg: ConversationMessage = {
      id: crypto.randomUUID(),
      role: "user",
      content: question,
      sources: [],
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, tempUserMsg]);

    try {
      if (!activeConversation) {
        // Create new conversation
        const conv = await createConversation(slug, question, token);
        setActiveConversation(conv);
        setMessages(conv.messages || []);
        setConversations((prev) => [conv, ...prev]);
      } else {
        // Send message to existing conversation
        const aiMsg = await sendMessage(slug, activeConversation.id, question, token);

        // Replace temp user msg with real messages: keep the user msg, add AI response
        setMessages((prev) => [...prev, aiMsg]);

        // Update conversation's updatedAt in the list
        setConversations((prev) =>
          prev.map((c) =>
            c.id === activeConversation.id
              ? { ...c, updatedAt: new Date().toISOString(), messageCount: c.messageCount + 2 }
              : c,
          ),
        );
      }
    } catch (err) {
      const errorText = err instanceof Error ? err.message : "Something went wrong";
      notify.error("AI query failed", errorText);

      const errorMsg: ConversationMessage = {
        id: crypto.randomUUID(),
        role: "ai",
        content: `I couldn't process that question. ${errorText}`,
        sources: [],
        createdAt: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setIsQuerying(false);
    }
  }, [input, token, isQuerying, slug, activeConversation]);

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  function handleInputChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    setInput(e.target.value);
    const el = e.target;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 120) + "px";
  }

  function formatTime(dateStr: string) {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffDays === 0) return "Today";
    if (diffDays === 1) return "Yesterday";
    if (diffDays < 7) return `${diffDays} days ago`;
    return date.toLocaleDateString();
  }

  const filteredConversations = conversations.filter((c) =>
    c.title.toLowerCase().includes(searchFilter.toLowerCase()),
  );

  // ── Chat View (active conversation or new conversation) ──
  if (activeConversation || messages.length > 0) {
    return (
      <div className="flex flex-1 flex-col rounded-2xl border border-gray-200 bg-white shadow-xs dark:border-gray-800 dark:bg-gray-900 overflow-hidden">
        {/* Chat header */}
        <div className="flex items-center gap-3 border-b border-gray-100 dark:border-gray-800 px-5 py-3.5">
          <button
            type="button"
            onClick={handleNewConversation}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200 transition-colors"
            title="Back to conversations"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-gray-900 dark:text-white">
              {activeConversation?.title || "New conversation"}
            </p>
            <p className="text-[11px] text-gray-500 dark:text-gray-400">
              Searching across all workspace documents
            </p>
          </div>
          <button
            type="button"
            onClick={handleNewConversation}
            className="flex items-center gap-1.5 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-2.5 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-750 transition-colors"
          >
            <Plus className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">New Chat</span>
          </button>
        </div>

        {/* Messages area */}
        <div className="flex-1 overflow-y-auto px-5 py-5 space-y-4">
          {messages.length === 0 && !isQuerying && (
            <WelcomePrompt onSuggestion={(s) => { setInput(s); inputRef.current?.focus(); }} />
          )}

          {messages.map((msg) => (
            <AiMessage
              key={msg.id}
              role={msg.role}
              content={msg.content}
              sources={msg.sources}
            />
          ))}

          {isQuerying && <AiMessage role="ai" content="" isLoading />}
          <div ref={messagesEndRef} />
        </div>

        {/* Input area */}
        <div className="border-t border-gray-100 dark:border-gray-800 px-5 py-3">
          <div className="flex items-end gap-2">
            <div className="relative flex-1">
              <textarea
                ref={inputRef}
                value={input}
                onChange={handleInputChange}
                onKeyDown={handleKeyDown}
                placeholder="Search across all documents..."
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
            Enter to send · Shift+Enter for new line
          </p>
        </div>
      </div>
    );
  }

  // ── Conversation List View ──
  return (
    <div className="flex flex-1 flex-col gap-6">
      {/* Search header card */}
      <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-xs dark:border-gray-800 dark:bg-gray-900">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
            <Sparkles className="h-6 w-6" />
          </div>
          <div>
            <h2 className="text-xl font-bold tracking-tight text-gray-900 dark:text-white">
              AI Search
            </h2>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              Ask questions across all documents in this workspace. Your conversations are saved automatically.
            </p>
          </div>
        </div>

        {/* Quick search input */}
        <div className="flex items-end gap-2">
          <div className="relative flex-1">
            <textarea
              ref={inputRef}
              value={input}
              onChange={handleInputChange}
              onKeyDown={handleKeyDown}
              placeholder="Ask anything about your documents..."
              disabled={isQuerying}
              rows={1}
              maxLength={1000}
              className="w-full resize-none rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 px-3.5 py-2.5 text-sm text-gray-900 dark:text-white placeholder-gray-400 outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100 dark:focus:ring-indigo-950/60 transition-colors disabled:opacity-50"
            />
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

        {/* Suggestion chips */}
        <div className="mt-3 flex flex-wrap gap-2">
          {["Summarize recent uploads", "What are the key findings?", "List all agreements"].map(
            (suggestion) => (
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
            ),
          )}
        </div>
      </section>

      {/* Conversation history */}
      <section className="rounded-2xl border border-gray-200 bg-white shadow-xs dark:border-gray-800 dark:bg-gray-900 overflow-hidden flex-1 flex flex-col">
        <div className="flex items-center justify-between border-b border-gray-100 dark:border-gray-800 px-5 py-3.5">
          <div>
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
              Conversation History
            </h3>
            <p className="text-[11px] text-gray-500 dark:text-gray-400">
              {conversations.length} {conversations.length === 1 ? "conversation" : "conversations"}
            </p>
          </div>
          {conversations.length > 0 && (
            <div className="flex items-center gap-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50/60 dark:bg-gray-800/40 px-2.5 py-1.5 text-xs text-gray-500 dark:text-gray-400">
              <Search className="h-3.5 w-3.5" />
              <input
                type="text"
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
                placeholder="Filter conversations..."
                className="w-32 bg-transparent border-none outline-none text-xs text-gray-900 dark:text-white placeholder-gray-400"
              />
            </div>
          )}
        </div>

        <div className="flex-1 overflow-y-auto">
          {isLoading ? (
            <div className="flex items-center justify-center py-12 text-sm text-gray-500 dark:text-gray-400">
              <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-indigo-600 mr-2.5" />
              Loading conversations...
            </div>
          ) : filteredConversations.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 px-6 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gray-100 dark:bg-gray-800 text-gray-400 mb-3">
                <MessageSquare className="h-6 w-6" />
              </div>
              <p className="text-sm font-medium text-gray-700 dark:text-gray-300">
                {searchFilter ? "No matching conversations" : "No conversations yet"}
              </p>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400 max-w-[240px]">
                {searchFilter
                  ? "Try a different search term"
                  : "Start a new conversation by typing a question above."}
              </p>
            </div>
          ) : (
            <div className="divide-y divide-gray-100 dark:divide-gray-800">
              {filteredConversations.map((conv) => (
                <button
                  key={conv.id}
                  type="button"
                  onClick={() => handleOpenConversation(conv)}
                  className="flex w-full items-center gap-3 px-5 py-3.5 text-left transition-colors hover:bg-gray-50/80 dark:hover:bg-gray-800/40 group"
                >
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400">
                    <MessageSquare className="h-4 w-4" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                      {conv.title}
                    </p>
                    <div className="mt-0.5 flex items-center gap-2 text-[11px] text-gray-400 dark:text-gray-500">
                      <span className="flex items-center gap-1">
                        <Clock className="h-3 w-3" />
                        {formatTime(conv.updatedAt)}
                      </span>
                      <span>·</span>
                      <span>
                        {conv.messageCount} {conv.messageCount === 1 ? "message" : "messages"}
                      </span>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={(e) => handleDeleteConversation(conv.id, e)}
                    className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-gray-300 opacity-0 group-hover:opacity-100 hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-950/40 transition-all"
                    title="Delete conversation"
                    aria-label={`Delete "${conv.title}"`}
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </button>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function WelcomePrompt({ onSuggestion }: { onSuggestion: (s: string) => void }) {
  return (
    <div className="flex flex-col items-center justify-center h-full text-center px-4 py-12">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 mb-4">
        <Sparkles className="h-7 w-7" />
      </div>
      <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-1.5">
        Search your workspace
      </h3>
      <p className="text-xs text-gray-500 dark:text-gray-400 max-w-[280px] leading-relaxed">
        Ask questions about any document in this workspace. AI will search across all your files and provide answers with source references.
      </p>
      <div className="mt-5 flex flex-wrap justify-center gap-2">
        {[
          "Summarize recent uploads",
          "What are the key findings?",
          "Compare the main documents",
        ].map((suggestion) => (
          <button
            key={suggestion}
            type="button"
            onClick={() => onSuggestion(suggestion)}
            className="rounded-full border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-1.5 text-[11px] font-medium text-gray-600 dark:text-gray-300 hover:border-indigo-300 hover:text-indigo-600 dark:hover:border-indigo-700 dark:hover:text-indigo-400 transition-colors"
          >
            {suggestion}
          </button>
        ))}
      </div>
    </div>
  );
}
