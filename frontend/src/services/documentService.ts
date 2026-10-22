import { randomUUID } from "../lib/uuid";

/**
 * ============================================================================
 * DOCUMENT UPLOAD & MANAGEMENT SERVICE
 * ============================================================================
 *
 * This file handles uploading files to the server, tracking their progress,
 * and managing the upload queue. It also provides functions to list, download,
 * and delete documents.
 *
 * Key concepts:
 * - We use XMLHttpRequest (not fetch) for uploads because it gives us
 *   real-time progress events (how many bytes have been sent so far).
 * - Multiple files can be uploaded at once, but we limit to 3 at a time
 *   to avoid overwhelming the server. Extra files wait in a queue.
 * - If an upload fails due to a network issue, we automatically retry
 *   up to 3 times with increasing wait times (1s, 2s, 4s).
 * ============================================================================
 */

export const API_BASE = import.meta.env.VITE_API_URL || "/api";

// ─── TYPES ──────────────────────────────────────────────────────────────

// Each file being uploaded gets tracked with this structure.
// It holds the file itself, its progress, speed, and any errors.
export interface UploadTask {
  id: string;                // A unique ID for this upload task
  file: File;                // The actual file the user picked
  folderId: string;          // Which folder to upload into
  status: "queued" | "uploading" | "done" | "failed";
  progress: number;          // 0 to 100 (percentage complete)
  speed: number;             // Upload speed in bytes per second
  error?: string;            // What went wrong (if anything)
  abortController: AbortController; // Used to cancel the upload
  retryCount: number;        // How many times we've retried this upload
}

// The shape of document data that comes back from the server.
export interface DocumentDTO {
  id: string;
  originalFileName: string;
  contentType: string;
  uploadedBy: string;
  uploadedByName?: string;
  fileSize: number;
  createdAt: string;
  updatedAt: string;
  indexingStatus?: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "NOT_PROCESSABLE";
}

// The paginated response from the server when listing documents.
export interface PagedDocuments {
  content: DocumentDTO[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

// ─── CONSTANTS ──────────────────────────────────────────────────────────

// Only these file types are allowed to be uploaded.
// This list matches the server-side allowlist.
const ALLOWED_EXTENSIONS = new Set([
  ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
  ".txt", ".csv", ".png", ".jpg", ".jpeg", ".gif", ".svg",
  ".webp", ".zip", ".rar", ".7z",
]);

// 50 megabytes — same as the server limit
const MAX_FILE_SIZE = 50 * 1024 * 1024;

// Maximum number of files that can be uploaded at the same time
const MAX_CONCURRENT_UPLOADS = 3;

// Retry settings for failed uploads
const MAX_RETRIES = 3;
const BASE_RETRY_DELAY_MS = 1000; // Start at 1 second, then doubles

// ─── VALIDATION ─────────────────────────────────────────────────────────

// Check a file before we try to upload it.
// Returns an error message if something is wrong, or null if the file is ok.
export function validateFile(file: File): string | null {
  if (file.size === 0) {
    return "Cannot upload an empty file";
  }

  if (file.size > MAX_FILE_SIZE) {
    return "File exceeds the maximum allowed size (50 MB)";
  }

  const extension = getExtension(file.name).toLowerCase();
  if (!ALLOWED_EXTENSIONS.has(extension)) {
    return `File type '${extension}' is not allowed`;
  }

  return null;
}

// Get the file extension (like ".pdf") from a filename
function getExtension(filename: string): string {
  const dot = filename.lastIndexOf(".");
  return dot >= 0 ? filename.substring(dot) : "";
}

// ─── UPLOAD MANAGER ─────────────────────────────────────────────────────

// This class manages the entire upload process: queueing files,
// running up to 3 at a time, retrying on failure, and notifying
// the UI whenever anything changes.
export class UploadManager {
  // The list of all upload tasks (queued, in-progress, done, or failed)
  private tasks: UploadTask[] = [];

  // How many uploads are currently running
  private activeCount = 0;

  // A function that gets called whenever any task changes,
  // so the UI can re-render with the latest state
  private onChange: (tasks: UploadTask[]) => void;

  // Auth token and tenant info needed for the API calls
  private token: string;
  private slug: string;

  constructor(slug: string, token: string, onChange: (tasks: UploadTask[]) => void) {
    this.slug = slug;
    this.token = token;
    this.onChange = onChange;
  }

  // Add files to the upload queue. Each file gets validated first.
  // Invalid files are immediately marked as "failed" with an error message.
  addFiles(files: File[], folderId: string) {
    for (const file of files) {
      const validationError = validateFile(file);

      const task: UploadTask = {
        id: randomUUID(),
        file,
        folderId,
        status: validationError ? "failed" : "queued",
        progress: 0,
        speed: 0,
        error: validationError || undefined,
        abortController: new AbortController(),
        retryCount: 0,
      };

      this.tasks.push(task);
    }

    this.notifyChange();

    // Start uploading any queued files (respecting the concurrency limit)
    this.processQueue();
  }

  // Cancel a specific upload. If it's currently uploading, abort the
  // network request. If it's queued, just remove it from the list.
  cancelTask(taskId: string) {
    const task = this.tasks.find((t) => t.id === taskId);
    if (!task) return;

    if (task.status === "uploading") {
      task.abortController.abort();
      this.activeCount--;
    }

    this.tasks = this.tasks.filter((t) => t.id !== taskId);
    this.notifyChange();
    this.processQueue();
  }

  // Cancel every upload — both queued and in-progress ones
  cancelAll() {
    for (const task of this.tasks) {
      if (task.status === "uploading") {
        task.abortController.abort();
      }
    }
    this.tasks = [];
    this.activeCount = 0;
    this.notifyChange();
  }

  // Retry a failed upload
  retryTask(taskId: string) {
    const task = this.tasks.find((t) => t.id === taskId);
    if (!task || task.status !== "failed") return;

    task.status = "queued";
    task.progress = 0;
    task.speed = 0;
    task.error = undefined;
    task.retryCount = 0;
    task.abortController = new AbortController();

    this.notifyChange();
    this.processQueue();
  }

  // Remove completed and failed tasks from the list (cleanup)
  clearFinished() {
    this.tasks = this.tasks.filter(
      (t) => t.status === "queued" || t.status === "uploading"
    );
    this.notifyChange();
  }

  // Get a snapshot of all current tasks
  getTasks(): UploadTask[] {
    return [...this.tasks];
  }

  // Check if any uploads are currently running
  hasActiveUploads(): boolean {
    return this.tasks.some(
      (t) => t.status === "uploading" || t.status === "queued"
    );
  }

  // ── Internal: start the next queued uploads ──

  private processQueue() {
    // Start as many uploads as we can without exceeding the limit
    while (this.activeCount < MAX_CONCURRENT_UPLOADS) {
      const nextTask = this.tasks.find((t) => t.status === "queued");
      if (!nextTask) break;

      this.activeCount++;
      nextTask.status = "uploading";
      this.notifyChange();
      this.executeUpload(nextTask);
    }
  }

  // ── Internal: perform one upload using XMLHttpRequest ──
  //
  // We use XMLHttpRequest instead of fetch() because XHR gives us
  // the "progress" event which tells us exactly how many bytes
  // have been sent. fetch() doesn't support upload progress yet.
  private executeUpload(task: UploadTask) {
    const xhr = new XMLHttpRequest();
    const startTime = Date.now();

    // This event fires repeatedly while the file is being sent,
    // telling us how much has been uploaded so far
    xhr.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable) {
        task.progress = Math.round((event.loaded / event.total) * 100);

        // Calculate upload speed (bytes per second)
        const elapsedSeconds = (Date.now() - startTime) / 1000;
        task.speed = elapsedSeconds > 0 ? event.loaded / elapsedSeconds : 0;

        this.notifyChange();
      }
    });

    // The upload finished successfully
    xhr.addEventListener("load", () => {
      if (xhr.status === 201) {
        // Server accepted the file
        task.status = "done";
        task.progress = 100;
        this.activeCount--;
        this.notifyChange();
        this.processQueue();
      } else if (this.shouldRetry(xhr.status, task)) {
        // Server had a temporary problem — try again
        this.retryWithBackoff(task);
      } else {
        // Server rejected the file permanently (bad file type, too large, etc.)
        task.status = "failed";
        task.error = this.parseErrorMessage(xhr);
        this.activeCount--;
        this.notifyChange();
        this.processQueue();
      }
    });

    // The network connection failed entirely (offline, server down, etc.)
    xhr.addEventListener("error", () => {
      if (this.shouldRetry(0, task)) {
        this.retryWithBackoff(task);
      } else {
        task.status = "failed";
        task.error = "Upload failed — connection lost";
        this.activeCount--;
        this.notifyChange();
        this.processQueue();
      }
    });

    // The upload took too long
    xhr.addEventListener("timeout", () => {
      task.status = "failed";
      task.error = "Upload timed out — the file may be too large for your connection";
      this.activeCount--;
      this.notifyChange();
      this.processQueue();
    });

    // The user clicked "Cancel" on this upload
    task.abortController.signal.addEventListener("abort", () => {
      xhr.abort();
    });

    // Build the URL and send the file
    const url = `${API_BASE}/tenants/${encodeURIComponent(this.slug)}/folders/${task.folderId}/documents`;

    // FormData is the standard way to send files over HTTP.
    // It packages the file into the format the server expects.
    const formData = new FormData();
    formData.append("file", task.file);

    xhr.open("POST", url);
    xhr.setRequestHeader("Authorization", `Bearer ${this.token}`);
    xhr.timeout = 5 * 60 * 1000; // 5 minute timeout for large files
    xhr.send(formData);
  }

  // ── Internal: decide whether to retry a failed upload ──
  //
  // We only retry on temporary problems (server errors, rate limits).
  // We never retry on permanent problems (wrong file type, no permission).
  private shouldRetry(httpStatus: number, task: UploadTask): boolean {
    if (task.retryCount >= MAX_RETRIES) return false;

    // Network error (status 0) or server temporarily unavailable
    const retryableStatuses = [0, 429, 502, 503, 504];
    return retryableStatuses.includes(httpStatus);
  }

  // ── Internal: wait a bit and try the upload again ──
  //
  // Each retry waits longer than the last: 1 second, then 2, then 4.
  // A small random amount is added so that if many uploads fail at once,
  // they don't all retry at exactly the same time (which would overload the server).
  private retryWithBackoff(task: UploadTask) {
    task.retryCount++;
    task.progress = 0;
    task.speed = 0;
    task.abortController = new AbortController();

    // Exponential backoff: 1s -> 2s -> 4s, plus random jitter (0-1s)
    const delay = Math.min(
      BASE_RETRY_DELAY_MS * Math.pow(2, task.retryCount - 1) + Math.random() * 1000,
      30000
    );

    this.activeCount--;

    setTimeout(() => {
      if (task.status === "uploading" || task.status === "queued") {
        this.activeCount++;
        task.status = "uploading";
        this.notifyChange();
        this.executeUpload(task);
      }
    }, delay);
  }

  // ── Internal: extract the error message from the server's response ──
  private parseErrorMessage(xhr: XMLHttpRequest): string {
    try {
      const response = JSON.parse(xhr.responseText);
      return response.message || response.error || `Upload failed (${xhr.status})`;
    } catch {
      // The status-to-message mapping for common HTTP errors
      const messages: Record<number, string> = {
        400: "Invalid file",
        401: "Your session has expired — please log in again",
        403: "You don't have permission to upload files here",
        409: "A file with this name already exists in the folder",
        413: "File exceeds the maximum allowed size (50 MB)",
        422: "This file type is not allowed",
        507: "Server storage is full — please contact your administrator",
      };
      return messages[xhr.status] || `Upload failed (${xhr.status})`;
    }
  }

  // ── Internal: tell the UI that something changed ──
  private notifyChange() {
    this.onChange([...this.tasks]);
  }
}

// ─── API FUNCTIONS ──────────────────────────────────────────────────────

// Fetch documents in a folder (with pagination).
// The server returns them as a "Page" which includes the documents
// plus info about how many total documents and pages there are.
export async function getDocuments(
  slug: string,
  folderId: string,
  token: string,
  page = 0,
  size = 20,
): Promise<PagedDocuments> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/folders/${folderId}/documents?page=${page}&size=${size}`;

  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    throw new Error(`Failed to load documents (${response.status})`);
  }

  return response.json();
}

// Download a document. Opens the file in the browser's download dialog.
export function downloadDocument(slug: string, docId: string, token: string) {
  // We create a temporary link with the auth token and "click" it.
  // This is the standard way to trigger a file download from JavaScript.
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/download`;

  // We need to use fetch + blob because the download endpoint requires
  // an Authorization header, and regular <a> links can't send headers.
  fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  })
    .then((response) => {
      if (!response.ok) throw new Error("Download failed");
      return response.blob();
    })
    .then((blob) => {
      // Create a temporary URL pointing to the downloaded data
      const blobUrl = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = blobUrl;

      // Try to get the filename from the server's response header
      link.download = "download";
      link.click();

      // Clean up the temporary URL after a short delay
      setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
    });
}

// Soft-delete a document (marks it as deleted, doesn't remove the file).
export async function deleteDocument(
  slug: string,
  docId: string,
  token: string,
): Promise<void> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}`;

  const response = await fetch(url, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    throw new Error(`Failed to delete document (${response.status})`);
  }
}

// ─── UTILITY FUNCTIONS ──────────────────────────────────────────────────

// Turn bytes into a human-readable size like "2.4 MB" or "890 KB"
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  const size = bytes / Math.pow(1024, i);
  return `${size.toFixed(i > 0 ? 1 : 0)} ${units[i]}`;
}

// Turn bytes-per-second into a readable speed like "2.4 MB/s"
export function formatSpeed(bytesPerSecond: number): string {
  return `${formatFileSize(bytesPerSecond)}/s`;
}
