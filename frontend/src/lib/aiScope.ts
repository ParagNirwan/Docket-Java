import type { AiSearchScope } from "../services/aiService";

// What the person picked. Empty strings mean "no limit".
export interface AiScopeSelection {
  trayId: string;
  fileType: string;
  period: string;
  tagId: string;
}

export const EMPTY_SCOPE_SELECTION: AiScopeSelection = { trayId: "", fileType: "", period: "", tagId: "" };

export const FILE_TYPES: Record<string, { label: string; contentTypes: string[] }> = {
  pdf: { label: "PDFs", contentTypes: ["application/pdf"] },
  word: {
    label: "Word documents",
    contentTypes: [
      "application/msword",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ],
  },
  sheet: {
    label: "Spreadsheets",
    contentTypes: [
      "application/vnd.ms-excel",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "text/csv",
    ],
  },
  slides: {
    label: "Presentations",
    contentTypes: [
      "application/vnd.ms-powerpoint",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ],
  },
  image: {
    label: "Images",
    contentTypes: ["image/png", "image/jpeg", "image/gif", "image/webp", "image/tiff"],
  },
  text: { label: "Text files", contentTypes: ["text/plain", "text/markdown"] },
};

export const PERIODS: Record<string, { label: string; days: number }> = {
  week: { label: "Last 7 days", days: 7 },
  month: { label: "Last 30 days", days: 30 },
  year: { label: "Last 12 months", days: 365 },
};

// The period is turned into a date here, when the question is sent, so that "last 7 days"
// still means the last 7 days in a conversation left open overnight.
export function toAiScope(selection: AiScopeSelection, now: Date = new Date()): AiSearchScope {
  const scope: AiSearchScope = {};
  if (selection.trayId) scope.trayId = selection.trayId;
  if (FILE_TYPES[selection.fileType]) scope.contentTypes = FILE_TYPES[selection.fileType].contentTypes;
  if (PERIODS[selection.period]) {
    scope.uploadedAfter = new Date(now.getTime() - PERIODS[selection.period].days * 86_400_000).toISOString();
  }
  if (selection.tagId) scope.tagIds = [selection.tagId];
  return scope;
}
