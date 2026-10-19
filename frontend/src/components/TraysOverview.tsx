import { useState } from "react";
import {
  Layers,
  Trash2,
  Shield,
  Users,
  Search,
  ChevronRight,
  MoreVertical,
  Plus,
} from "lucide-react";
import { ViewModeSwitcher, type ViewMode } from "./ViewModeSwitcher";
import { TrayThumbnail } from "./TrayThumbnail";
import { DeleteConfirmationModal } from "./DeleteConfirmationModal";
import type { Tray } from "../services/trayService";

interface TraysOverviewProps {
  trays: Tray[];
  onSelectTray: (trayId: string) => void;
  canCreateTray: boolean;
  canDeleteTray: boolean;
  onDeleteTray?: (trayId: string) => void;
  onCreateTrayClick: () => void;
}

export function TraysOverview({
  trays,
  onSelectTray,
  canCreateTray,
  canDeleteTray,
  onDeleteTray,
  onCreateTrayClick,
}: TraysOverviewProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<{
    id: string;
    name: string;
  } | null>(null);

  const [viewMode, setViewMode] = useState<ViewMode>(() => {
    try {
      const saved = localStorage.getItem("docket_tray_view_mode");
      if (saved === "list" || saved === "thumbnail" || saved === "cards") {
        return saved;
      }
    } catch {
      // Ignore localStorage issues
    }
    return "cards";
  });

  function handleViewModeChange(mode: ViewMode) {
    setViewMode(mode);
    try {
      localStorage.setItem("docket_tray_view_mode", mode);
    } catch {
      // Ignore
    }
  }

  const filteredTrays = trays.filter(
    (t) =>
      t.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (t.description && t.description.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  async function handleConfirmDelete() {
    if (!pendingDelete) return;
    if (onDeleteTray) {
      await onDeleteTray(pendingDelete.id);
    }
    setPendingDelete(null);
  }

  return (
    <div className="space-y-6">
      {/* ── Top Header Card ──────────────────────────────────── */}
      <section className="overflow-hidden rounded-2xl border border-gray-200 bg-white p-6 shadow-xs dark:border-gray-800 dark:bg-gray-900">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-4">
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
              <Layers className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="text-xl font-bold tracking-tight text-gray-900 dark:text-white">
                  Document Trays
                </h1>
                <span className="rounded-full bg-indigo-50 dark:bg-indigo-950/50 px-2 py-0.5 text-[10px] font-semibold text-indigo-600 dark:text-indigo-300">
                  {trays.length} {trays.length === 1 ? "tray" : "trays"}
                </span>
              </div>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Top-level organizational containers with independent visibility controls.
              </p>
            </div>
          </div>

          {canCreateTray && (
            <button
              type="button"
              onClick={onCreateTrayClick}
              className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-3.5 py-2 text-xs font-semibold text-white shadow-xs hover:bg-indigo-700 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900 self-start sm:self-auto"
            >
              <Plus className="h-4 w-4" />
              <span>Create Tray</span>
            </button>
          )}
        </div>

        {/* ── Search & View Mode Switcher Row ────────────────── */}
        <div className="mt-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          {/* Search bar */}
          <div className="flex-1 flex items-center gap-2 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/60 dark:bg-gray-800/40 px-3 py-2 text-xs text-gray-500 dark:text-gray-400">
            <Search className="h-4 w-4 text-gray-400 shrink-0" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search trays..."
              className="w-full bg-transparent border-none outline-none text-xs text-gray-900 dark:text-white placeholder-gray-400"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 text-xs px-1"
                title="Clear search"
                aria-label="Clear search"
              >
                ✕
              </button>
            )}
          </div>

          {/* View Mode Switcher */}
          <ViewModeSwitcher viewMode={viewMode} onChange={handleViewModeChange} />
        </div>
      </section>

      {/* ── Trays Content ────────────────────────────────────── */}
      {filteredTrays.length === 0 ? (
        <section className="flex min-h-[260px] flex-col items-center justify-center rounded-2xl border border-dashed border-gray-300 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-900">
          <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
            <Layers className="h-7 w-7" strokeWidth={1.5} />
          </div>
          <h3 className="text-base font-semibold text-gray-900 dark:text-white">
            No trays found
          </h3>
          <p className="mt-1 max-w-sm text-xs text-gray-500 dark:text-gray-400">
            {searchQuery
              ? `No trays matched "${searchQuery}". Try a different keyword.`
              : "Create your first document tray to organize files and folders."}
          </p>
          {canCreateTray && !searchQuery && (
            <button
              type="button"
              onClick={onCreateTrayClick}
              className="mt-5 inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-indigo-700 transition-colors"
            >
              <Plus className="h-4 w-4" />
              <span>Create First Tray</span>
            </button>
          )}
        </section>
      ) : (
        <div>
          {/* 1. Trays — CARDS VIEW */}
          {viewMode === "cards" && (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {filteredTrays.map((tray) => {
                const isTrash = tray.id === "trash" || tray.name.toLowerCase() === "trash";
                return (
                  <div
                    key={tray.id}
                    className="group flex flex-col justify-between rounded-2xl border border-gray-200 bg-white p-5 shadow-xs hover:border-indigo-300 hover:shadow-md dark:border-gray-800 dark:bg-gray-900 dark:hover:border-indigo-800 transition-all cursor-pointer relative"
                    onClick={() => onSelectTray(tray.id)}
                  >
                    <div>
                      <div className="flex items-start justify-between gap-3 mb-3">
                        <div
                          className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${
                            isTrash
                              ? "bg-red-50 text-red-600 dark:bg-red-950/40 dark:text-red-400"
                              : "bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400"
                          } group-hover:scale-105 transition-transform`}
                        >
                          {isTrash ? <Trash2 className="h-5 w-5" /> : <Layers className="h-5 w-5" />}
                        </div>

                        <div className="flex items-center gap-1.5 flex-wrap justify-end">
                          {tray.isDefault ? (
                            <span className="rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-[10px] font-semibold text-gray-600 dark:text-gray-300">
                              Default
                            </span>
                          ) : (
                            <span className="rounded-full bg-indigo-50 dark:bg-indigo-950/50 px-2 py-0.5 text-[10px] font-semibold text-indigo-600 dark:text-indigo-300">
                              Custom
                            </span>
                          )}
                          {tray.visibility === "ADMIN_ONLY" ? (
                            <span className="flex items-center gap-1 rounded-full bg-amber-50 dark:bg-amber-950/40 px-2 py-0.5 text-[10px] font-semibold text-amber-700 dark:text-amber-300">
                              <Shield className="h-2.5 w-2.5" /> Admin
                            </span>
                          ) : (
                            <span className="flex items-center gap-1 rounded-full bg-emerald-50 dark:bg-emerald-950/40 px-2 py-0.5 text-[10px] font-semibold text-emerald-700 dark:text-emerald-300">
                              <Users className="h-2.5 w-2.5" /> Members
                            </span>
                          )}
                          {!tray.isDefault && canDeleteTray && onDeleteTray && (
                            <div
                              className="dropdown-menu-container relative"
                              onClick={(e) => e.stopPropagation()}
                            >
                              <button
                                type="button"
                                onClick={() =>
                                  setActiveMenuId(
                                    activeMenuId === `tray-card-${tray.id}`
                                      ? null
                                      : `tray-card-${tray.id}`
                                  )
                                }
                                className="p-1 rounded-lg text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                                title="Tray options"
                                aria-label={`Options for ${tray.name}`}
                              >
                                <MoreVertical className="h-3.5 w-3.5" />
                              </button>
                              {activeMenuId === `tray-card-${tray.id}` && (
                                <div className="absolute right-0 top-full mt-1 w-36 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-xl p-1 z-30">
                                  <button
                                    type="button"
                                    onClick={() => {
                                      setActiveMenuId(null);
                                      setPendingDelete({
                                        id: tray.id,
                                        name: tray.name,
                                      });
                                    }}
                                    className="flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors"
                                  >
                                    <Trash2 className="h-3.5 w-3.5" />
                                    <span>Delete tray</span>
                                  </button>
                                </div>
                              )}
                            </div>
                          )}
                        </div>
                      </div>

                      <h3 className="font-semibold text-base text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors line-clamp-1">
                        {tray.name}
                      </h3>
                      <p className="mt-1 text-xs text-gray-500 dark:text-gray-400 line-clamp-2">
                        {tray.description || "Top-level tray for document organization."}
                      </p>
                    </div>

                    <div className="mt-5 pt-3.5 border-t border-gray-100 dark:border-gray-800/80 flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
                      <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-gray-600 dark:text-gray-300">
                        <Layers className="h-3 w-3 text-gray-400" />
                        {tray.itemCount ?? 0} {tray.itemCount === 1 ? "item" : "items"}
                      </span>
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 dark:text-indigo-400 group-hover:translate-x-0.5 transition-transform">
                        Open Tray <ChevronRight className="h-3.5 w-3.5" />
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* 2. Trays — THUMBNAIL VIEW */}
          {viewMode === "thumbnail" && (
            <div className="grid gap-4 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
              {filteredTrays.map((tray) => (
                <div
                  key={tray.id}
                  className="group flex flex-col rounded-2xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 p-2.5 shadow-2xs hover:border-indigo-400 hover:shadow-md dark:hover:border-indigo-600 transition-all cursor-pointer relative"
                  onClick={() => onSelectTray(tray.id)}
                >
                  <TrayThumbnail tray={tray} className="h-28 w-full" />
                  <div className="mt-2.5 px-1 min-w-0">
                    <p
                      className="truncate text-xs font-semibold text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors"
                      title={tray.name}
                    >
                      {tray.name}
                    </p>
                    <div className="mt-1 flex items-center justify-between text-[11px] text-gray-400 dark:text-gray-500">
                      <span>{tray.itemCount ?? 0} items</span>
                      <span className="text-[9px] uppercase font-bold text-indigo-500 dark:text-indigo-400">
                        {tray.isDefault ? "Default" : "Custom"}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* 3. Trays — LIST VIEW */}
          {viewMode === "list" && (
            <div className="overflow-hidden rounded-2xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-2xs">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="border-b border-gray-100 dark:border-gray-800 bg-gray-50/70 dark:bg-gray-800/40 text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    <tr>
                      <th className="py-3 px-4">Tray Name</th>
                      <th className="py-3 px-4 hidden md:table-cell">Description</th>
                      <th className="py-3 px-4 hidden sm:table-cell">Items</th>
                      <th className="py-3 px-4">Type</th>
                      <th className="py-3 px-4 hidden lg:table-cell">Visibility</th>
                      <th className="py-3 px-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                    {filteredTrays.map((tray) => {
                      const isTrash = tray.id === "trash" || tray.name.toLowerCase() === "trash";
                      return (
                        <tr
                          key={tray.id}
                          className="hover:bg-gray-50/80 dark:hover:bg-gray-800/40 transition-colors cursor-pointer group"
                          onClick={() => onSelectTray(tray.id)}
                        >
                          <td className="py-3 px-4">
                            <div className="flex items-center gap-3">
                              <div
                                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${
                                  isTrash
                                    ? "bg-red-50 text-red-600 dark:bg-red-950/40 dark:text-red-400"
                                    : "bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400"
                                } group-hover:scale-105 transition-transform`}
                              >
                                {isTrash ? (
                                  <Trash2 className="h-4 w-4" />
                                ) : (
                                  <Layers className="h-4 w-4" />
                                )}
                              </div>
                              <span className="font-semibold text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors truncate max-w-xs">
                                {tray.name}
                              </span>
                            </div>
                          </td>
                          <td className="py-3 px-4 hidden md:table-cell text-gray-500 dark:text-gray-400 truncate max-w-xs">
                            {tray.description || "—"}
                          </td>
                          <td className="py-3 px-4 hidden sm:table-cell text-gray-600 dark:text-gray-300 font-medium">
                            {tray.itemCount ?? 0}
                          </td>
                          <td className="py-3 px-4">
                            {tray.isDefault ? (
                              <span className="rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-[10px] font-semibold text-gray-600 dark:text-gray-300">
                                Default
                              </span>
                            ) : (
                              <span className="rounded-full bg-indigo-50 dark:bg-indigo-950/50 px-2 py-0.5 text-[10px] font-semibold text-indigo-600 dark:text-indigo-300">
                                Custom
                              </span>
                            )}
                          </td>
                          <td className="py-3 px-4 hidden lg:table-cell">
                            {tray.visibility === "ADMIN_ONLY" ? (
                              <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 dark:bg-amber-950/40 px-2 py-0.5 text-[10px] font-semibold text-amber-700 dark:text-amber-300">
                                <Shield className="h-2.5 w-2.5" /> Admin Only
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 dark:bg-emerald-950/40 px-2 py-0.5 text-[10px] font-semibold text-emerald-700 dark:text-emerald-300">
                                <Users className="h-2.5 w-2.5" /> All Members
                              </span>
                            )}
                          </td>
                          <td
                            className="py-3 px-4 text-right"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <div className="flex items-center justify-end gap-1">
                              <button
                                type="button"
                                onClick={() => onSelectTray(tray.id)}
                                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-indigo-600 hover:bg-indigo-50 dark:hover:bg-indigo-950/40 transition-colors font-medium text-xs"
                              >
                                <span>Open</span>
                                <ChevronRight className="h-3.5 w-3.5" />
                              </button>
                              {!tray.isDefault && canDeleteTray && onDeleteTray && (
                                <button
                                  type="button"
                                  onClick={() =>
                                    setPendingDelete({
                                      id: tray.id,
                                      name: tray.name,
                                    })
                                  }
                                  className="p-1.5 rounded-lg text-gray-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors"
                                  title="Delete custom tray"
                                  aria-label={`Delete ${tray.name}`}
                                >
                                  <Trash2 className="h-3.5 w-3.5" />
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Delete Modal */}
      <DeleteConfirmationModal
        isOpen={pendingDelete !== null}
        itemName={pendingDelete?.name || ""}
        itemType="tray"
        onClose={() => setPendingDelete(null)}
        onConfirm={handleConfirmDelete}
      />
    </div>
  );
}
