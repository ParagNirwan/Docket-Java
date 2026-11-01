import { API_BASE } from "./documentService";
import { apiFetch } from "../lib/session";

export interface SearchResult {
  documentId: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  uploadedByName: string | null;
  createdAt: string;
  folderId: string;
  folderName: string;
  trayId: string;
  trayName: string;
  // Plain text with each match between the control characters U+0002 and U+0003.
  snippet: string | null;
  page: number | null;
}

export interface SearchPage {
  content: SearchResult[];
  number: number;
  totalPages: number;
  totalElements: number;
}

export interface SearchFilters {
  q: string;
  type: string;
  tray: string;
  // Dates as yyyy-mm-dd, in the user's time zone
  from: string;
  to: string;
}

export const SEARCH_TYPES = [
  { value: "pdf", label: "PDF" },
  { value: "word", label: "Word" },
  { value: "spreadsheet", label: "Spreadsheet" },
  { value: "presentation", label: "Presentation" },
  { value: "image", label: "Image" },
  { value: "text", label: "Text" },
  { value: "archive", label: "Archive" },
];

export const MIN_SEARCH_LENGTH = 2;

export async function searchDocuments(slug: string, filters: SearchFilters, page: number, token: string): Promise<SearchPage> {
  const params = new URLSearchParams({ q: filters.q.trim(), page: String(page), size: "20" });
  if (filters.type) params.set("type", filters.type);
  if (filters.tray) params.set("tray", filters.tray);
  if (filters.from) params.set("from", new Date(`${filters.from}T00:00:00`).toISOString());
  if (filters.to) params.set("to", new Date(`${filters.to}T23:59:59.999`).toISOString());

  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/search?${params}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw new Error("Could not run the search");
  return response.json();
}

// Splits a snippet into text and highlighted matches. These stay plain strings, so text
// from inside a document can never be treated as HTML.
export function parseSnippet(snippet: string): { text: string; match: boolean }[] {
  const [lead, ...rest] = snippet.split("\u0002");
  const parts = [{ text: lead, match: false }];
  for (const part of rest) {
    const [matched, after = ""] = part.split("\u0003");
    parts.push({ text: matched, match: true }, { text: after, match: false });
  }
  return parts.filter((part) => part.text);
}
