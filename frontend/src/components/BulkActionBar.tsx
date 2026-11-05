import { useState } from "react";
import { Trash2, FolderInput, Archive, Tag, X, Loader2 } from "lucide-react";
import { FolderPickerModal } from "./FolderPickerModal";
import {
  bulkDeleteDocuments,
  bulkMoveDocuments,
  bulkArchiveDocuments,
  bulkTagDocuments,
} from "../services/documentService";
import { getTags, type TagDTO } from "../services/tagService";
import type { Tray } from "../services/trayService";

interface BulkActionBarProps {
  slug: string;
  selectedDocIds: string[];
  token: string;
  trays?: Tray[];
  onClearSelection: () => void;
  onOperationComplete: (summary: string) => void;
}

export function BulkActionBar({
  slug,
  selectedDocIds,
  token,
  trays = [],
  onClearSelection,
  onOperationComplete,
}: BulkActionBarProps) {
  const [loading, setLoading] = useState(false);
  const [showFolderPicker, setShowFolderPicker] = useState(false);
  const [showTagPicker, setShowTagPicker] = useState(false);
  const [availableTags, setAvailableTags] = useState<TagDTO[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>([]);

  if (selectedDocIds.length === 0) return null;

  async function handleDelete() {
    if (!window.confirm(`Move ${selectedDocIds.length} selected documents to Trash?`)) return;
    try {
      setLoading(true);
      const res = await bulkDeleteDocuments(slug, selectedDocIds, token);
      onOperationComplete(`Deleted ${res.succeeded} documents${res.failed > 0 ? ` (${res.failed} failed)` : ""}`);
      onClearSelection();
    } catch (err) {
      alert(err instanceof Error ? err.message : "Bulk delete failed");
    } finally {
      setLoading(false);
    }
  }

  async function handleMove(targetFolderId: string) {
    try {
      setLoading(true);
      setShowFolderPicker(false);
      const res = await bulkMoveDocuments(slug, selectedDocIds, targetFolderId, token);
      onOperationComplete(`Moved ${res.succeeded} documents${res.failed > 0 ? ` (${res.failed} failed)` : ""}`);
      onClearSelection();
    } catch (err) {
      alert(err instanceof Error ? err.message : "Bulk move failed");
    } finally {
      setLoading(false);
    }
  }

  async function handleArchive() {
    if (!window.confirm(`Archive ${selectedDocIds.length} selected documents?`)) return;
    try {
      setLoading(true);
      const res = await bulkArchiveDocuments(slug, selectedDocIds, token);
      onOperationComplete(`Archived ${res.succeeded} documents${res.failed > 0 ? ` (${res.failed} failed)` : ""}`);
      onClearSelection();
    } catch (err) {
      alert(err instanceof Error ? err.message : "Bulk archive failed");
    } finally {
      setLoading(false);
    }
  }

  async function openTagPicker() {
    try {
      setLoading(true);
      const tags = await getTags(slug, token);
      setAvailableTags(tags);
      setSelectedTagIds([]);
      setShowTagPicker(true);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }

  async function handleApplyTags() {
    try {
      setLoading(true);
      setShowTagPicker(false);
      const res = await bulkTagDocuments(slug, selectedDocIds, selectedTagIds, [], token);
      onOperationComplete(`Tagged ${res.succeeded} documents`);
      onClearSelection();
    } catch (err) {
      alert(err instanceof Error ? err.message : "Bulk tag failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 max-w-xl w-[90vw] bg-gray-900 dark:bg-gray-800 text-white rounded-2xl shadow-2xl border border-gray-700/80 px-4 py-2.5 flex items-center justify-between gap-3 animate-in fade-in slide-in-from-bottom-4 duration-200">
        <div className="flex items-center gap-2 min-w-0">
          <div className="flex items-center justify-center w-7 h-7 rounded-lg bg-indigo-600 text-white font-bold text-xs shrink-0">
            {selectedDocIds.length}
          </div>
          <span className="text-xs font-medium text-gray-200 truncate">
            selected
          </span>
          <button
            type="button"
            onClick={onClearSelection}
            className="text-[11px] text-gray-400 hover:text-white underline ml-1 shrink-0"
          >
            Clear
          </button>
        </div>

        <div className="flex items-center gap-1.5 shrink-0">
          {loading ? (
            <div className="px-4 py-1.5 flex items-center gap-2 text-xs text-gray-300">
              <Loader2 className="w-4 h-4 animate-spin text-indigo-400" />
              Processing...
            </div>
          ) : (
            <>
              <button
                type="button"
                onClick={openTagPicker}
                title="Tag selected"
                className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-semibold rounded-full bg-gray-800 hover:bg-gray-700 dark:bg-gray-700 dark:hover:bg-gray-600 text-gray-200 transition-colors"
              >
                <Tag className="w-3.5 h-3.5 text-indigo-400" />
                <span className="hidden sm:inline">Tag</span>
              </button>

              <button
                type="button"
                onClick={() => setShowFolderPicker(true)}
                title="Move selected"
                className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-semibold rounded-full bg-gray-800 hover:bg-gray-700 dark:bg-gray-700 dark:hover:bg-gray-600 text-gray-200 transition-colors"
              >
                <FolderInput className="w-3.5 h-3.5 text-blue-400" />
                <span className="hidden sm:inline">Move</span>
              </button>

              <button
                type="button"
                onClick={handleArchive}
                title="Archive selected"
                className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-semibold rounded-full bg-gray-800 hover:bg-gray-700 dark:bg-gray-700 dark:hover:bg-gray-600 text-gray-200 transition-colors"
              >
                <Archive className="w-3.5 h-3.5 text-amber-400" />
                <span className="hidden sm:inline">Archive</span>
              </button>

              <button
                type="button"
                onClick={handleDelete}
                title="Delete selected"
                className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-semibold rounded-full bg-red-600 hover:bg-red-700 text-white transition-colors"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Delete</span>
              </button>
            </>
          )}
        </div>
      </div>

      {showFolderPicker && (
        <FolderPickerModal
          slug={slug}
          token={token}
          mode="move"
          itemName={`${selectedDocIds.length} documents`}
          trays={trays}
          onClose={() => setShowFolderPicker(false)}
          onPick={handleMove}
        />
      )}

      {showTagPicker && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
        >
          <div className="fixed inset-0 bg-black/50 backdrop-blur-xs" onClick={() => setShowTagPicker(false)} />
          <div className="relative w-full max-w-sm bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-gray-900 dark:text-white">
                Add Tags to {selectedDocIds.length} Documents
              </h3>
              <button
                type="button"
                onClick={() => setShowTagPicker(false)}
                className="p-1 rounded-full text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex flex-wrap gap-2 max-h-48 overflow-y-auto">
              {availableTags.map((tag) => {
                const selected = selectedTagIds.includes(tag.id);
                return (
                  <button
                    key={tag.id}
                    type="button"
                    onClick={() => {
                      setSelectedTagIds((prev) =>
                        prev.includes(tag.id) ? prev.filter((id) => id !== tag.id) : [...prev, tag.id]
                      );
                    }}
                    className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${
                      selected
                        ? "bg-indigo-600 text-white border-indigo-600"
                        : "bg-gray-50 dark:bg-gray-800 text-gray-700 dark:text-gray-300 border-gray-200 dark:border-gray-700"
                    }`}
                  >
                    <span className="w-2 h-2 rounded-full" style={{ backgroundColor: tag.color || "#6b7280" }} />
                    {tag.name}
                  </button>
                );
              })}
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-gray-100 dark:border-gray-800">
              <button
                type="button"
                onClick={() => setShowTagPicker(false)}
                className="px-3 py-1.5 text-xs font-medium rounded-full border border-gray-200 dark:border-gray-700"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleApplyTags}
                disabled={selectedTagIds.length === 0}
                className="px-3.5 py-1.5 text-xs font-semibold rounded-full bg-indigo-600 hover:bg-indigo-700 text-white disabled:opacity-50"
              >
                Apply Tags
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

