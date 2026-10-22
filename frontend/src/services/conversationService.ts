import { API_BASE } from "./documentService";
import type { AiSourceChunk } from "./aiService";

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
  const response = await fetch(url, { headers: headers(token) });
  return handleResponse<Conversation[]>(response);
}

export async function getConversation(
  slug: string,
  conversationId: string,
  token: string,
): Promise<Conversation> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations/${conversationId}`;
  const response = await fetch(url, { headers: headers(token) });
  return handleResponse<Conversation>(response);
}

export async function createConversation(
  slug: string,
  question: string,
  token: string,
): Promise<Conversation> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations`;
  const response = await fetch(url, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ question }),
  });
  return handleResponse<Conversation>(response);
}

export async function sendMessage(
  slug: string,
  conversationId: string,
  question: string,
  token: string,
): Promise<ConversationMessage> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations/${conversationId}/messages`;
  const response = await fetch(url, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({ question }),
  });
  return handleResponse<ConversationMessage>(response);
}

export async function deleteConversation(
  slug: string,
  conversationId: string,
  token: string,
): Promise<void> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/conversations/${conversationId}`;
  const response = await fetch(url, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok && response.status !== 204) {
    throw new Error(`Failed to delete conversation (${response.status})`);
  }
}
