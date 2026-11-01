import { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, Folder, Layers, X } from "lucide-react";
import { getFolderChildren, type FolderItem, type Tray } from "../services/trayService";

interface FolderPickerModalProps {
  slug: string;
  token?: string | null;
  mode: "move" | "copy";
  itemName: string;
  // A folder being moved, which can't be chosen as its own destination.
  excludeFolderId?: string;
  trays: Tray[];
  onClose: () => void;
  onPick: (folderId: string) => Promise<void>;
}

interface TreeNodeProps {
  id: string;
  name: string;
  isTray: boolean;
  depth: number;
  slug: string;
  token?: string | null;
  excludeFolderId?: string;
  selectedId: string | null;
  onSelect: (id: string, name: string) => void;
}

// One row of the tree. Its children load the first time it's opened.
function TreeNode({ id, name, isTray, depth, slug, token, excludeFolderId, selectedId, onSelect }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(false);
  const [children, setChildren] = useState<FolderItem[] | null>(null);
  const Icon = isTray ? Layers : Folder;

  async function toggle() {
    const next = !expanded;
    setExpanded(next);
    if (next && children === null) {
      const { folders } = await getFolderChildren(slug, id, token);
      setChildren(folders.filter((folder) => folder.id !== excludeFolderId));
    }
  }

  return (
    <li>
      <div
        className={`flex items-center gap-1 rounded-lg pr-2 ${selectedId === id ? "bg-indigo-50 dark:bg-indigo-950/60" : "hover:bg-gray-100 dark:hover:bg-gray-800"}`}
        style={{ paddingLeft: depth * 16 }}
      >
        <button type="button" onClick={toggle} aria-label={`${expanded ? "Collapse" : "Expand"} ${name}`} className="p-1 text-gray-400 hover:text-gray-700 dark:hover:text-gray-200">
          {expanded ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
        </button>
        <button
          type="button"
          aria-pressed={selectedId === id}
          onClick={() => onSelect(id, name)}
          className="flex min-w-0 flex-1 items-center gap-2 py-1.5 text-left text-xs font-medium text-gray-800 dark:text-gray-100"
        >
          <Icon className={`h-3.5 w-3.5 shrink-0 ${isTray ? "text-indigo-500" : "text-amber-500"}`} />
          <span className="truncate">{name}</span>
        </button>
      </div>
      {expanded && children && children.length > 0 && (
        <ul>
          {children.map((child) => (
            <TreeNode
              key={child.id}
              id={child.id}
              name={child.name}
              isTray={false}
              depth={depth + 1}
              slug={slug}
              token={token}
              excludeFolderId={excludeFolderId}
              selectedId={selectedId}
              onSelect={onSelect}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

// The "Move to..." and "Copy to..." dialog: a tree of the trays this person can see.
export function FolderPickerModal({ slug, token, mode, itemName, excludeFolderId, trays, onClose, onPick }: FolderPickerModalProps) {
  const [selected, setSelected] = useState<{ id: string; name: string } | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const verb = mode === "move" ? "Move" : "Copy";

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  async function handleConfirm() {
    if (!selected) return;
    try {
      setIsSubmitting(true);
      setError(null);
      await onPick(selected.id);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : `Could not ${mode} this`);
      setIsSubmitting(false);
    }
  }

  return (
    <div role="dialog" aria-modal="true" aria-labelledby="folder-picker-title" className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6">
      <div className="fixed inset-0 bg-black/50 backdrop-blur-xs" onClick={onClose} />
      <div className="relative w-full max-w-md bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden flex flex-col max-h-[80vh]">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
          <div className="min-w-0">
            <h2 id="folder-picker-title" className="text-lg font-bold text-gray-900 dark:text-white">
              {verb} to...
            </h2>
            <p className="truncate text-xs text-gray-500 dark:text-gray-400">{itemName}</p>
          </div>
          <button type="button" onClick={onClose} aria-label="Close dialog" className="p-1.5 rounded-lg text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          {error && (
            <div role="alert" className="mb-3 rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 p-3 text-xs text-red-600 dark:text-red-400">
              {error}
            </div>
          )}
          <ul className="space-y-0.5">
            {trays.map((tray) => (
              <TreeNode
                key={tray.id}
                id={tray.id}
                name={tray.name}
                isTray
                depth={0}
                slug={slug}
                token={token}
                excludeFolderId={excludeFolderId}
                selectedId={selected?.id ?? null}
                onSelect={(id, name) => setSelected({ id, name })}
              />
            ))}
          </ul>
        </div>

        <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-100 dark:border-gray-800">
          <button type="button" onClick={onClose} disabled={isSubmitting} className="px-4 py-2 text-xs font-medium rounded-xl border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors">
            Cancel
          </button>
          <button type="button" onClick={handleConfirm} disabled={isSubmitting || !selected} className="px-4 py-2 text-xs font-medium rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
            {isSubmitting ? `${verb === "Move" ? "Moving" : "Copying"}...` : selected ? `${verb} to ${selected.name}` : verb}
          </button>
        </div>
      </div>
    </div>
  );
}
