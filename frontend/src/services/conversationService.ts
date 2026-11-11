import { apiFetch } from "../lib/session";
import { API_BASE } from "./documentService";
import { readAiStream, type AiQueryMode, type AiSearchScope, type AiSourceChunk, type AiStreamHandlers } from "./aiService";

export interface ConversationMessage {
  id: string;
  role: "user" | "ai";
  content: string;
  sources: AiSourceChunk[];
  createdAt: string;
}

export interface Conversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
  messages: ConversationMessage[] | null;
}

export async function submitAiFeedback(
  slug: string,
  conversationId: string,
  messageId: string,
  token: string,
  rating: -1 | 1,
  reason?: string,
): Promise<void> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations/${conversationId}/messages/${messageId}/feedback`;
  const response = await apiFetch(url, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ rating, reason }),
  });
  if (!response.ok) {
    throw new Error(`Failed to submit AI feedback (${response.status})`);
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.status === 429) {
    throw new Error("Rate limit exceeded. Please wait a moment and try again.");
  }
  if (!response.ok) {
    const body = await response.text();
    let message = `Request failed (${response.status})`;
    try {
      const parsed = JSON.parse(body);
      message = parsed.message || parsed.error || message;
    } catch {
      // keep default
    }
    throw new Error(message);
  }
  return response.json();
}

function headers(token: string) {
  return {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };
}

export async function listConversations(
  slug: string,
  token: string,
): Promise<Conversation[]> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations`;
  const response = await apiFetch(url, { headers: headers(token) });
  return handleResponse<Conversation[]>(response);
}

export async function getConversation(
  slug: string,
  conversationId: string,
  token: string,
): Promise<Conversation> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations/${conversationId}`;
  const response = await apiFetch(url, { headers: headers(token) });
  return handleResponse<Conversation>(response);
}

export async function createConversation(
  slug: string,
  question: string,
  token: string,
  mode: AiQueryMode = "ASK",
): Promise<Conversation> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations`;
  const response = await apiFetch(url, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ question, mode }),
  });
  return handleResponse<Conversation>(response);
}

export async function sendMessage(
  slug: string,
  conversationId: string,
  question: string,
  token: string,
  mode: AiQueryMode = "ASK",
): Promise<ConversationMessage> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations/${conversationId}/messages`;
  const response = await apiFetch(url, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ question, mode }),
  });
  return handleResponse<ConversationMessage>(response);
}

export async function deleteConversation(
  slug: string,
  conversationId: string,
  token: string,
): Promise<void> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations/${conversationId}`;
  const response = await apiFetch(url, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok && response.status !== 204) {
    throw new Error(`Failed to delete conversation (${response.status})`);
  }
}

// Starts a conversation and streams its first answer. Resolves with the stored conversation.
export async function streamCreateConversation(
  slug: string,
  question: string,
  token: string,
  mode: AiQueryMode,
  scope: AiSearchScope,
  handlers: AiStreamHandlers,
): Promise<Conversation> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations/stream`;
  const response = await apiFetch(url, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ question, mode, scope }),
    signal: handlers.signal,
  });
  return readAiStream<Conversation>(response, handlers);
}

// Streams the answer to a follow-up. Resolves with the stored AI message.
export async function streamSendMessage(
  slug: string,
  conversationId: string,
  question: string,
  token: string,
  mode: AiQueryMode,
  scope: AiSearchScope,
  handlers: AiStreamHandlers,
): Promise<ConversationMessage> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations/${conversationId}/messages/stream`;
  const response = await apiFetch(url, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ question, mode, scope }),
    signal: handlers.signal,
  });
  return readAiStream<ConversationMessage>(response, handlers);
}
