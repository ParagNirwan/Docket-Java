// Shared classes and helpers for the organization settings panels.
export const INPUT_CLASS =
  "w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-700 dark:bg-gray-950 dark:text-white";
export const PRIMARY_BUTTON =
  "inline-flex items-center gap-1.5 rounded-full bg-indigo-600 px-3.5 py-2 text-sm font-medium text-white shadow-xs transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50";
export const SECONDARY_BUTTON =
  "inline-flex items-center gap-1.5 rounded-full border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-50 disabled:opacity-50 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800";
export const DANGER_BUTTON =
  "inline-flex items-center gap-1.5 rounded-full border border-red-200 px-3 py-1.5 text-xs font-medium text-red-600 transition-colors hover:bg-red-50 disabled:opacity-50 dark:border-red-900/60 dark:text-red-400 dark:hover:bg-red-950/40";

export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  return new Date(value).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export function errorMessage(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback;
}
