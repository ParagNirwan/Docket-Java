/**
 * ============================================================================
 * UPLOAD PROGRESS PANEL
 * ============================================================================
 *
 * This panel appears at the bottom-right of the screen when files are being
 * uploaded. It shows each file's progress, speed, and status. Users can:
 *  - See a progress bar for each file
 *  - Cancel individual uploads or all at once
 *  - Retry failed uploads
 *  - Minimize the panel to keep working while uploads continue
 *  - Clear finished uploads from the list
 * ============================================================================
 */

import { useState } from "react";
import {
  X,
  ChevronDown,
  ChevronUp,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Clock,
  RefreshCw,
} from "lucide-react";
import type { UploadTask } from "../services/documentService";
import { formatFileSize, formatSpeed } from "../services/documentService";

interface UploadProgressPanelProps {
  // The list of all upload tasks (from the UploadManager)
  tasks: UploadTask[];
  // Called when the user clicks "Cancel" on one upload
  onCancel: (taskId: string) => void;
  // Called when the user clicks "Cancel All"
  onCancelAll: () => void;
  // Called when the user clicks "Retry" on a failed upload
  onRetry: (taskId: string) => void;
  // Called when the user clicks "Clear finished"
  onClearFinished: () => void;
}

export function UploadProgressPanel({
  tasks,
  onCancel,
  onCancelAll,
  onRetry,
  onClearFinished,
}: UploadProgressPanelProps) {
  // Whether the panel is collapsed (minimized) or expanded
  const [minimized, setMinimized] = useState(false);

  // Don't show the panel at all if there are no uploads
  if (tasks.length === 0) return null;

  // Count how many uploads are in each state
  const doneCount = tasks.filter((t) => t.status === "done").length;
  const failedCount = tasks.filter((t) => t.status === "failed").length;
  const activeCount = tasks.filter(
    (t) => t.status === "uploading" || t.status === "queued"
  ).length;

  // Build a short summary like "2 of 5 uploaded" or "3 uploading"
  const summaryText =
    activeCount > 0
      ? `Uploading ${activeCount} file${activeCount !== 1 ? "s" : ""}...`
      : failedCount > 0
        ? `${doneCount} uploaded, ${failedCount} failed`
        : `${doneCount} file${doneCount !== 1 ? "s" : ""} uploaded`;

  return (
    // The panel sticks to the bottom-right corner of the screen
    <div className="fixed bottom-4 right-4 z-50 w-[380px] overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-2xl dark:border-gray-800 dark:bg-gray-900">
      {/* ── Panel Header ─────────────────────────────────── */}
      {/* Shows a summary and controls to minimize/close the panel */}
      <div className="flex items-center justify-between border-b border-gray-100 px-4 py-3 dark:border-gray-800">
        <div className="flex items-center gap-2">
          {/* Show a spinner while uploads are active, or a checkmark when done */}
          {activeCount > 0 ? (
            <Loader2 className="h-4 w-4 animate-spin text-indigo-600" />
          ) : failedCount > 0 ? (
            <AlertCircle className="h-4 w-4 text-red-500" />
          ) : (
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          )}
          <span className="text-sm font-medium text-gray-900 dark:text-white">
            {summaryText}
          </span>
        </div>

        <div className="flex items-center gap-1">
          {/* Minimize/expand button */}
          <button
            type="button"
            onClick={() => setMinimized(!minimized)}
            className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
            title={minimized ? "Expand" : "Minimize"}
          >
            {minimized ? (
              <ChevronUp className="h-4 w-4" />
            ) : (
              <ChevronDown className="h-4 w-4" />
            )}
          </button>

          {/* Close button — only when no active uploads */}
          {activeCount === 0 && (
            <button
              type="button"
              onClick={onClearFinished}
              className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
              title="Close"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>

      {/* ── File List (hidden when minimized) ────────────── */}
      {!minimized && (
        <div className="max-h-[300px] divide-y divide-gray-100 overflow-y-auto dark:divide-gray-800">
          {tasks.map((task) => (
            <div key={task.id} className="px-4 py-3">
              {/* File name and cancel/retry button */}
              <div className="mb-1.5 flex items-start justify-between gap-2">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                    {task.file.name}
                  </p>
                  <p className="text-[11px] text-gray-400">
                    {formatFileSize(task.file.size)}
                  </p>
                </div>

                {/* Show different buttons depending on the upload's state */}
                {task.status === "uploading" || task.status === "queued" ? (
                  // Cancel button for active/queued uploads
                  <button
                    type="button"
                    onClick={() => onCancel(task.id)}
                    className="shrink-0 rounded-lg p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800"
                    title="Cancel upload"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                ) : task.status === "failed" ? (
                  // Retry button for failed uploads
                  <button
                    type="button"
                    onClick={() => onRetry(task.id)}
                    className="shrink-0 rounded-lg p-1 text-amber-500 hover:bg-amber-50 hover:text-amber-600 dark:hover:bg-amber-950/40"
                    title="Retry upload"
                  >
                    <RefreshCw className="h-3.5 w-3.5" />
                  </button>
                ) : null}
              </div>

              {/* Progress bar and status */}
              {task.status === "uploading" && (
                <>
                  {/* The progress bar — a simple div whose width changes */}
                  <div className="mb-1 h-1.5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
                    <div
                      className="h-full rounded-full bg-indigo-600 transition-all duration-300"
                      style={{ width: `${task.progress}%` }}
                    />
                  </div>
                  {/* Speed and percentage */}
                  <div className="flex justify-between text-[11px] text-gray-400">
                    <span>{formatSpeed(task.speed)}</span>
                    <span>{task.progress}%</span>
                  </div>
                </>
              )}

              {/* Status badges */}
              {task.status === "queued" && (
                <div className="flex items-center gap-1.5 text-[11px] text-gray-400">
                  <Clock className="h-3 w-3" />
                  <span>Waiting in queue...</span>
                </div>
              )}

              {task.status === "done" && (
                <div className="flex items-center gap-1.5 text-[11px] text-emerald-600 dark:text-emerald-400">
                  <CheckCircle2 className="h-3 w-3" />
                  <span>Uploaded successfully</span>
                </div>
              )}

              {task.status === "failed" && (
                <div className="flex items-center gap-1.5 text-[11px] text-red-500">
                  <AlertCircle className="h-3 w-3" />
                  <span>{task.error || "Upload failed"}</span>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* ── Footer with "Cancel All" button ──────────────── */}
      {/* Only shows when there are active uploads */}
      {!minimized && activeCount > 0 && (
        <div className="border-t border-gray-100 px-4 py-2.5 dark:border-gray-800">
          <button
            type="button"
            onClick={onCancelAll}
            className="text-xs font-medium text-red-500 hover:text-red-600"
          >
            Cancel all uploads
          </button>
        </div>
      )}
    </div>
  );
}
