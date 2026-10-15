import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import {
  CloseIcon,
  LogOutIcon,
  UserIcon,
  ShieldIcon,
  BuildingIcon,
  CheckIcon,
} from "./icons";

export function AccountSettingsModal() {
  const { isSettingsModalOpen, closeSettingsModal, user, logout } = useAuth();
  const navigate = useNavigate();

  // Close on Escape key press
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape" && isSettingsModalOpen) {
        closeSettingsModal();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isSettingsModalOpen, closeSettingsModal]);

  if (!isSettingsModalOpen) return null;

  const email = user?.email || "User";
  const initial = (email.charAt(0) || "U").toUpperCase();
  const role = user?.role || "Member";
  const userType = user?.userType || "Standard User";
  const expiresAt = user?.exp
    ? new Date(user.exp * 1000).toLocaleString(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
      })
    : "Active Session";

  function handleLogout() {
    closeSettingsModal();
    logout();
    navigate("/login");
  }

  function handleNavigateToTenant() {
    closeSettingsModal();
    navigate("/create-first-tenant");
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="account-settings-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
    >
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/50 backdrop-blur-xs transition-opacity duration-200"
        onClick={closeSettingsModal}
      />

      {/* Modal Dialog Card */}
      <div className="relative w-full max-w-lg bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400">
              <UserIcon className="w-5 h-5" />
            </div>
            <div>
              <h2
                id="account-settings-title"
                className="text-lg font-bold text-gray-900 dark:text-white"
              >
                Account Settings
              </h2>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                Manage your credentials and session
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={closeSettingsModal}
            className="p-1.5 rounded-lg text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            aria-label="Close settings"
          >
            <CloseIcon className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 space-y-6 overflow-y-auto">
          {/* User Profile Badge */}
          <div className="flex items-center gap-4 p-4 rounded-xl bg-gray-50 dark:bg-gray-800/50 border border-gray-100 dark:border-gray-800">
            <div className="w-14 h-14 rounded-full bg-gradient-to-tr from-indigo-600 via-indigo-500 to-violet-500 flex items-center justify-center text-white font-bold text-xl shadow-md shrink-0">
              {initial}
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="font-semibold text-gray-900 dark:text-white truncate">
                  {email}
                </span>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-100 dark:bg-emerald-950/80 text-emerald-700 dark:text-emerald-300">
                  <CheckIcon className="w-3 h-3" />
                  Verified
                </span>
              </div>
              <div className="flex items-center gap-2 mt-1 text-xs text-gray-500 dark:text-gray-400">
                <span className="capitalize">{role.toLowerCase()}</span>
                <span>•</span>
                <span>{userType}</span>
              </div>
            </div>
          </div>

          {/* Account Details Group */}
          <div className="space-y-3">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
              Account Information
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="p-3.5 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900/60">
                <div className="text-xs text-gray-500 dark:text-gray-400 mb-1">
                  Primary Email
                </div>
                <div className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                  {email}
                </div>
              </div>

              <div className="p-3.5 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900/60">
                <div className="text-xs text-gray-500 dark:text-gray-400 mb-1">
                  Role & Permissions
                </div>
                <div className="text-sm font-medium text-gray-900 dark:text-gray-100">
                  {role}
                </div>
              </div>
            </div>
          </div>

          {/* Organization & Tenant */}
          <div className="space-y-3">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
              Tenant & Organization
            </h3>
            <div className="p-4 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900/60 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-lg bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400">
                  <BuildingIcon className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-sm font-medium text-gray-900 dark:text-white">
                    {user?.tenantId ? `Tenant #${user.tenantId}` : "Workspace"}
                  </div>
                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    Document Vault & Workspace
                  </div>
                </div>
              </div>
              <button
                type="button"
                onClick={handleNavigateToTenant}
                className="px-3 py-1.5 text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-950/60 rounded-lg transition-colors border border-indigo-200 dark:border-indigo-800"
              >
                New Tenant
              </button>
            </div>
          </div>

          {/* Session Security */}
          <div className="space-y-3">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
              Session & Security
            </h3>
            <div className="p-4 rounded-xl border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900/60 flex items-center gap-3">
              <div className="w-9 h-9 rounded-lg bg-emerald-50 dark:bg-emerald-950/60 flex items-center justify-center text-emerald-600 dark:text-emerald-400 shrink-0">
                <ShieldIcon className="w-5 h-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium text-gray-900 dark:text-white">
                  JWT Session Valid
                </div>
                <div className="text-xs text-gray-500 dark:text-gray-400">
                  Expires: {expiresAt}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Footer Actions */}
        <div className="px-6 py-4 border-t border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-900/50 flex items-center justify-between">
          <button
            type="button"
            onClick={handleLogout}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-sm font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/50 rounded-lg transition-colors"
          >
            <LogOutIcon className="w-4 h-4" />
            Log Out
          </button>
          <button
            type="button"
            onClick={closeSettingsModal}
            className="px-4 py-2 text-sm font-medium bg-gray-900 hover:bg-gray-800 text-white dark:bg-gray-100 dark:text-gray-900 dark:hover:bg-white rounded-lg transition-colors shadow-sm"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
}

