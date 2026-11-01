import { apiFetch, getStoredToken, refreshAccessToken } from "../lib/session";
import { randomUUID } from "../lib/uuid";
import type { LockMode } from "../lib/retention";

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
  authRetried?: boolean;     // Has this upload already refreshed an expired login?
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
  folderId?: string;
  retainUntil?: string | null;
  lockMode?: LockMode | null;
  legalHold?: boolean;
  archivedAt?: string | null;
}

// A 423 from the server: says what's protecting the document.
export class DocumentLockedError extends Error {
  retainUntil: string | null;
  lockMode: LockMode | null;
  legalHold: boolean;

  constructor(message: string, retainUntil: string | null, lockMode: LockMode | null, legalHold: boolean) {
    super(message);
    this.name = "DocumentLockedError";
    this.retainUntil = retainUntil;
    this.lockMode = lockMode;
    this.legalHold = legalHold;
  }

  static async from(response: Response): Promise<DocumentLockedError> {
    const body = await response.json().catch(() => ({}));
    return new DocumentLockedError(
      body.message ?? "This is locked and can't be changed",
      body.retainUntil ?? null,
      body.lockMode ?? null,
      Boolean(body.legalHold),
    );
  }
}

// Only an Owner can use this, and only on a Governance lock.
export interface LockOverride {
  reason: string;
}

function overrideQuery(override?: LockOverride): string {
  return override ? `?override=true&reason=${encodeURIComponent(override.reason)}` : "";
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
    // A queued upload can start long after this manager was created.
    const sentToken = getStoredToken() ?? this.token;

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
      if (xhr.status === 401 && !task.authRetried) {
        // The login expired while this file was waiting. XHR can't use apiFetch,
        // so refresh here and send the file once more.
        task.authRetried = true;
        refreshAccessToken(sentToken).then((refreshed) => {
          if (refreshed) {
            this.executeUpload(task);
          } else {
            task.status = "failed";
            task.error = this.parseErrorMessage(xhr);
            this.activeCount--;
            this.notifyChange();
            this.processQueue();
          }
        });
      } else if (xhr.status === 201) {
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
    xhr.setRequestHeader("Authorization", `Bearer ${sentToken}`);
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

  const response = await apiFetch(url, {
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
  apiFetch(url, {
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
  override?: LockOverride,
): Promise<void> {
  const url = `${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}${overrideQuery(override)}`;

  const response = await apiFetch(url, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status === 423) {
    throw await DocumentLockedError.from(response);
  }
  if (!response.ok) {
    throw new Error(`Failed to delete document (${response.status})`);
  }
}

// Rename and/or move a document. The API answers with a status code and no text, so
// the wording lives here.
export async function updateDocument(
  slug: string,
  docId: string,
  changes: { name?: string; folderId?: string },
  token: string,
  override?: LockOverride,
): Promise<DocumentDTO> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}${overrideQuery(override)}`, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(changes),
  });

  if (response.status === 423) {
    throw await DocumentLockedError.from(response);
  }
  if (!response.ok) {
    const messages: Record<number, string> = {
      403: "You don't have permission to change this document",
      404: "That document or folder is no longer available",
      409: "A document with this name already exists in the folder",
      422: "The file extension can't be changed",
    };
    throw new Error(messages[response.status] ?? `Failed to update document (${response.status})`);
  }
  return response.json();
}

// Copy a document. If the name is taken the copy gets a suffix, like "report (1).pdf".
export async function copyDocument(
  slug: string,
  docId: string,
  folderId: string,
  token: string,
): Promise<DocumentDTO> {
  const response = await apiFetch(`${API_BASE}/tenants/${encodeURIComponent(slug)}/documents/${docId}/copy`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ folderId }),
  });

  if (!response.ok) {
    const messages: Record<number, string> = {
      403: "You don't have permission to copy documents",
      404: "That document or folder is no longer available",
    };
    throw new Error(messages[response.status] ?? `Failed to copy document (${response.status})`);
  }
  return response.json();
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
