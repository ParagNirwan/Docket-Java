import { apiFetch } from "../lib/session";
import { API_BASE } from "./documentService";

export interface AiSourceChunk {
  documentId: string;
  documentName: string;
  chunkText: string;
  chunkIndex: number;
  // Missing on answers saved before these existed
  relevanceScore?: number | null;
  pageStart?: number | null;
  pageEnd?: number | null;
  heading?: string | null;
}

export interface AiQueryResponse {
  answer: string;
  sources: AiSourceChunk[];
}

export interface IndexingStatusResponse {
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "NOT_PROCESSABLE";
}

export async function queryAi(
  slug: string,
  payload: { question: string; documentId: string },
  token: string,
): Promise<AiQueryResponse> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/query`;

  const response = await apiFetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (response.status === 429) {
    throw new Error("Rate limit exceeded. Please wait a moment and try again.");
  }

  if (!response.ok) {
    const body = await response.text();
    let message = `AI query failed (${response.status})`;
    try {
      const parsed = JSON.parse(body);
      message = parsed.message || parsed.error || message;
    } catch {
      // keep default message
    }
    throw new Error(message);
  }

  return response.json();
}

export async function getIndexingStatus(
  slug: string,
  docId: string,
  token: string,
): Promise<IndexingStatusResponse> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/indexing-status`;

  const response = await apiFetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    throw new Error(`Failed to get indexing status (${response.status})`);
  }

  return response.json();
}
