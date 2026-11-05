import { useEffect, useState } from "react";
import { X, Lock, AlertCircle, Loader2 } from "lucide-react";
import { getTrayPermissions, updateTrayPermissions, type Tray } from "../services/trayService";
import { listMembers, type Member } from "../services/memberService";

interface CustomTrayPermissionsModalProps {
  slug: string;
  tray: Tray;
  token: string;
  onClose: () => void;
  onPermissionsUpdated?: () => void;
}

export function CustomTrayPermissionsModal({
  slug,
  tray,
  token,
  onClose,
  onPermissionsUpdated,
}: CustomTrayPermissionsModalProps) {
  const [members, setMembers] = useState<Member[]>([]);
  const [selectedUserIds, setSelectedUserIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true);
        setError(null);
        const [membersData, permsData] = await Promise.all([
          listMembers(slug, token),
          getTrayPermissions(slug, tray.id, token),
        ]);
        setMembers(membersData);
        setSelectedUserIds(new Set(permsData.userIds));
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load tray permissions");
      } finally {
        setLoading(false);
      }
    }
    void loadData();
  }, [slug, tray.id, token]);

  function toggleUser(userId: string) {
    setSelectedUserIds((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) {
        next.delete(userId);
      } else {
        next.add(userId);
      }
      return next;
    });
  }

  async function handleSave() {
    try {
      setSaving(true);
      setError(null);
      await updateTrayPermissions(slug, tray.id, Array.from(selectedUserIds), token);
      onPermissionsUpdated?.();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save permissions");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="tray-perms-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
    >
      <div className="fixed inset-0 bg-black/50 backdrop-blur-xs" onClick={onClose} />
      <div className="relative w-full max-w-md bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden flex flex-col max-h-[88vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800 shrink-0">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400 shrink-0">
              <Lock className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <h2 id="tray-perms-title" className="text-base font-bold text-gray-900 dark:text-white truncate">
                Custom Tray Access
              </h2>
              <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
                {tray.name}
              </p>
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
        <div className="p-6 space-y-4 overflow-y-auto flex-1">
          <p className="text-xs text-gray-500 dark:text-gray-400">
            Owners and Admins always have access. Select specific members or guests below to grant them access to this custom tray.
          </p>

          {error && (
            <div role="alert" className="flex items-center gap-2 rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 p-3 text-xs text-red-600 dark:text-red-400">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {loading ? (
            <div className="flex justify-center py-8 text-gray-400">
              <Loader2 className="w-6 h-6 animate-spin" />
            </div>
          ) : members.length === 0 ? (
            <p className="text-xs text-gray-500 text-center py-4">No members found</p>
          ) : (
            <div className="space-y-1.5 divide-y divide-gray-100 dark:divide-gray-800">
              {members.map((member) => {
                const isSelected = selectedUserIds.has(member.membershipId); // using membershipId or userId
                const isAdminOrOwner = member.role === "OWNER" || member.role === "ADMIN";

                return (
                  <label
                    key={member.membershipId}
                    className={`flex items-center justify-between p-2.5 rounded-xl cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800/40 transition-colors ${
                      isAdminOrOwner ? "opacity-60 cursor-default" : ""
                    }`}
                  >
                    <div className="min-w-0 pr-3">
                      <div className="text-xs font-semibold text-gray-900 dark:text-white truncate">
                        {member.name || member.email}
                      </div>
                      <div className="text-[11px] text-gray-500 dark:text-gray-400 truncate">
                        {member.email} • {member.role}
                      </div>
                    </div>

                    <input
                      type="checkbox"
                      disabled={isAdminOrOwner}
                      checked={isAdminOrOwner || isSelected}
                      onChange={() => toggleUser(member.membershipId)}
                      className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 h-4 w-4 shrink-0"
                    />
                  </label>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
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
            onClick={handleSave}
            disabled={saving}
            className="px-4 py-2 text-xs font-semibold rounded-full bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors disabled:opacity-50"
          >
            {saving ? "Saving..." : "Save Permissions"}
          </button>
        </div>
      </div>
    </div>
  );
}

