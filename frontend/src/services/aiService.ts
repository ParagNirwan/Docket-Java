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

export type AiQueryMode = "ASK" | "SUMMARIZE";

export interface IndexingStatusResponse {
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "NOT_PROCESSABLE";
}

export async function queryAi(
  slug: string,
  payload: { question: string; documentId: string; mode?: AiQueryMode },
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

// Narrows a workspace search. Every part is optional.
export interface AiSearchScope {
  trayId?: string;
  // Includes every folder below it
  folderId?: string;
  documentIds?: string[];
  contentTypes?: string[];
  uploadedAfter?: string;
  uploadedBefore?: string;
  tagIds?: string[];
}

export interface AiStreamHandlers {
  // Everything the model is about to read, before it starts writing
  onSources?: (sources: AiSourceChunk[]) => void;
  onDelta: (text: string) => void;
  // Aborting it stops the answer. The promise then rejects with an AbortError.
  signal?: AbortSignal;
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

// Reads an answer sent as server-sent events and resolves with the "done" event, which
// carries the finished, stored result. EventSource can't be used here because it only
// does GET requests and can't send the Authorization header.
export async function readAiStream<TDone>(response: Response, handlers: AiStreamHandlers): Promise<TDone> {
  if (response.status === 429) {
    throw new Error("Rate limit exceeded. Please wait a moment and try again.");
  }
  if (!response.ok || !response.body) {
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

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    // An event ends with a blank line. Whatever follows the last one is still arriving.
    const frames = buffer.split("\n\n");
    buffer = frames.pop() ?? "";

    for (const frame of frames) {
      let event = "message";
      let data = "";
      for (const line of frame.split("\n")) {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:")) data += line.slice(5).trim();
      }
      if (!data) continue;
      const payload = JSON.parse(data);

      if (event === "sources") handlers.onSources?.(payload as AiSourceChunk[]);
      else if (event === "delta") handlers.onDelta((payload as { text: string }).text);
      else if (event === "error") throw new Error((payload as { message: string }).message);
      else if (event === "done") return payload as TDone;
    }
  }

  throw new Error("The answer was cut off. Please try again.");
}

export async function streamAiQuery(
  slug: string,
  payload: { question: string; documentId?: string; mode?: AiQueryMode; scope?: AiSearchScope },
  token: string,
  handlers: AiStreamHandlers,
): Promise<AiQueryResponse> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai/query/stream`;
  const response = await apiFetch(url, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(payload),
    signal: handlers.signal,
  });
  return readAiStream<AiQueryResponse>(response, handlers);
}

export interface DocumentSummary {
  // null when the document has no summary
  summary: string | null;
  keyPoints: string[];
  generatedAt: string | null;
}

export async function getDocumentSummary(slug: string, docId: string, token: string): Promise<DocumentSummary> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/summary`;
  const response = await apiFetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (!response.ok) {
    throw new Error(`Failed to load the summary (${response.status})`);
  }
  return response.json();
}

export interface AiSettings {
  summariesEnabled: boolean;
}

export async function getAiSettings(slug: string, token: string): Promise<AiSettings> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai-settings`;
  const response = await apiFetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (!response.ok) {
    throw new Error(`Failed to load AI settings (${response.status})`);
  }
  return response.json();
}

export async function updateAiSettings(slug: string, settings: AiSettings, token: string): Promise<AiSettings> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/ai-settings`;
  const response = await apiFetch(url, {
    method: "PUT",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(settings),
  });
  if (!response.ok) {
    throw new Error(`Failed to update AI settings (${response.status})`);
  }
  return response.json();
}
