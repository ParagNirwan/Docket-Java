import { useEffect, useState } from "react";
import { X, Tag as TagIcon, Plus, Trash2, Check, AlertCircle, Loader2 } from "lucide-react";
import {
  getTags,
  createTag,
  deleteTag,
  setDocumentTags,
  type TagDTO,
} from "../services/tagService";
import type { DocumentDTO } from "../services/documentService";

const CURATED_COLORS = [
  "#ef4444", // Red
  "#f97316", // Orange
  "#f59e0b", // Amber
  "#10b981", // Emerald
  "#06b6d4", // Cyan
  "#3b82f6", // Blue
  "#8b5cf6", // Purple
  "#ec4899", // Pink
  "#6b7280", // Gray
];

interface ManageTagsModalProps {
  slug: string;
  document?: DocumentDTO | null;
  token: string;
  onClose: () => void;
  onTagsUpdated: () => void;
}

export function ManageTagsModal({
  slug,
  document,
  token,
  onClose,
  onTagsUpdated,
}: ManageTagsModalProps) {
  const [allTags, setAllTags] = useState<TagDTO[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // New tag form
  const [newTagName, setNewTagName] = useState("");
  const [newTagColor, setNewTagColor] = useState(CURATED_COLORS[0]);
  const [creating, setCreating] = useState(false);
  const [savingDocumentTags, setSavingDocumentTags] = useState(false);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  useEffect(() => {
    if (document?.tags) {
      setSelectedTagIds(new Set(document.tags.map((t) => t.id)));
    } else {
      setSelectedTagIds(new Set());
    }
  }, [document]);

  async function loadWorkspaceTags() {
    try {
      setLoading(true);
      setError(null);
      const tags = await getTags(slug, token);
      setAllTags(tags);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load tags");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadWorkspaceTags();
  }, [slug, token]);

  async function handleCreateTag(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = newTagName.trim();
    if (!trimmed) return;

    try {
      setCreating(true);
      setError(null);
      const tag = await createTag(slug, trimmed, newTagColor, token);
      setNewTagName("");
      await loadWorkspaceTags();
      if (document) {
        // Automatically select newly created tag for this document
        setSelectedTagIds((prev) => new Set(prev).add(tag.id));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create tag");
    } finally {
      setCreating(false);
    }
  }

  async function handleDeleteTag(tagId: string) {
    if (!window.confirm("Delete this tag? It will be removed from all documents.")) return;
    try {
      setError(null);
      await deleteTag(slug, tagId, token);
      setSelectedTagIds((prev) => {
        const next = new Set(prev);
        next.delete(tagId);
        return next;
      });
      await loadWorkspaceTags();
      onTagsUpdated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete tag");
    }
  }

  function toggleTag(tagId: string) {
    setSelectedTagIds((prev) => {
      const next = new Set(prev);
      if (next.has(tagId)) {
        next.delete(tagId);
      } else {
        next.add(tagId);
      }
      return next;
    });
  }

  async function handleSaveDocumentTags() {
    if (!document) {
      onClose();
      return;
    }
    try {
      setSavingDocumentTags(true);
      setError(null);
      await setDocumentTags(slug, document.id, Array.from(selectedTagIds), token);
      onTagsUpdated();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save document tags");
    } finally {
      setSavingDocumentTags(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="tags-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
    >
      <div className="fixed inset-0 bg-black/50 backdrop-blur-xs" onClick={onClose} />
      <div className="relative w-full max-w-md bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden flex flex-col max-h-[88vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800 shrink-0">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400 shrink-0">
              <TagIcon className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <h2 id="tags-title" className="text-base font-bold text-gray-900 dark:text-white truncate">
                {document ? "Document Tags" : "Manage Tags"}
              </h2>
              {document && (
                <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
                  {document.originalFileName}
                </p>
              )}
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="p-1.5 rounded-full text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-5 overflow-y-auto flex-1">
          {error && (
            <div role="alert" className="flex items-center gap-2 rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 p-3 text-xs text-red-600 dark:text-red-400">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Create Tag Form */}
          <form onSubmit={handleCreateTag} className="space-y-3 p-3.5 rounded-xl border border-gray-200 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-800/30">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
              Create New Tag
            </h3>
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Tag name (e.g. Urgent, Invoice)"
                value={newTagName}
                onChange={(e) => setNewTagName(e.target.value)}
                disabled={creating}
                className="flex-1 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 px-3.5 py-1.5 text-xs text-gray-900 dark:text-white focus:border-indigo-500 focus:outline-none"
              />
              <button
                type="submit"
                disabled={creating || !newTagName.trim()}
                className="inline-flex items-center gap-1 px-3.5 py-1.5 text-xs font-semibold rounded-full bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors disabled:opacity-50"
              >
                <Plus className="w-3.5 h-3.5" />
                Add
              </button>
            </div>

            {/* Color picker swatches */}
            <div className="flex items-center gap-1.5 pt-1">
              {CURATED_COLORS.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setNewTagColor(c)}
                  style={{ backgroundColor: c }}
                  className={`w-5 h-5 rounded-full transition-transform ${
                    newTagColor === c ? "ring-2 ring-offset-2 ring-indigo-500 scale-110" : "hover:scale-105"
                  }`}
                  aria-label={`Select color ${c}`}
                />
              ))}
            </div>
          </form>

          {/* Tags List */}
          <div className="space-y-2">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
              {document ? "Select Tags to Apply" : "Workspace Tags"} ({allTags.length})
            </h3>

            {loading ? (
              <div className="flex justify-center py-6 text-gray-400">
                <Loader2 className="w-5 h-5 animate-spin" />
              </div>
            ) : allTags.length === 0 ? (
              <p className="text-xs text-gray-500 dark:text-gray-400 py-3 text-center">
                No tags created yet. Add one above!
              </p>
            ) : (
              <div className="flex flex-wrap gap-2 pt-1">
                {allTags.map((tag) => {
                  const isSelected = selectedTagIds.has(tag.id);
                  const tagColor = tag.color || "#6b7280";

                  return (
                    <div
                      key={tag.id}
                      className={`group inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border transition-all ${
                        document ? "cursor-pointer" : ""
                      } ${
                        isSelected
                          ? "ring-2 ring-indigo-500 bg-indigo-50 dark:bg-indigo-950/40 border-indigo-300 dark:border-indigo-700"
                          : "border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-200 hover:border-gray-300"
                      }`}
                      onClick={() => document && toggleTag(tag.id)}
                    >
                      <span
                        className="w-2.5 h-2.5 rounded-full shrink-0"
                        style={{ backgroundColor: tagColor }}
                      />
                      <span>{tag.name}</span>
                      {document && isSelected && (
                        <Check className="w-3 h-3 text-indigo-600 dark:text-indigo-400 shrink-0" />
                      )}
                      {!document && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            void handleDeleteTag(tag.id);
                          }}
                          className="opacity-0 group-hover:opacity-100 p-0.5 rounded-full text-red-500 hover:text-red-700 transition-opacity ml-1"
                        >
                          <Trash2 className="w-3 h-3" />
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        {document && (
          <div className="flex items-center justify-end gap-2.5 px-6 py-3.5 border-t border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-800/20 shrink-0">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-medium rounded-full border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleSaveDocumentTags}
              disabled={savingDocumentTags}
              className="px-4 py-2 text-xs font-semibold rounded-full bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors disabled:opacity-50"
            >
              {savingDocumentTags ? "Saving..." : "Apply Tags"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

