import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import {
  UserIcon,
  ShieldIcon,
  BuildingIcon,
  LogOutIcon,
  CheckIcon,
  PlusIcon,
} from "../components/icons";
import { StatusBadge } from "../components/ui/StatusBadge";

export function SettingsPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const email = user?.email || "User";
  const initial = (email.charAt(0) || "U").toUpperCase();
  const role = user?.role || "Administrator";
  const userType = user?.userType || "Standard User";
  const expiresAt = user?.exp
    ? new Date(user.exp * 1000).toLocaleString(undefined, {
        dateStyle: "full",
        timeStyle: "short",
      })
    : "Active Session";

  function handleLogout() {
    logout();
    navigate("/login");
  }

  return (
    <div className="w-full px-4 py-8 sm:px-6 lg:px-8">
      {/* Page Header */}
      <div className="mb-8 border-b border-gray-200 dark:border-gray-800 pb-5">
        <h1 className="text-3xl font-bold tracking-tight text-gray-900 dark:text-white">
          Account Settings
        </h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          Manage your personal profile, workspace membership, and active sessions.
        </p>
      </div>

      <div className="space-y-6">
        {/* Profile Card */}
        <div className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl p-6 shadow-xs">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-gray-100 dark:border-gray-800">
            <div className="flex items-center gap-4">
              <div className="w-16 h-16 rounded-full bg-gradient-to-tr from-indigo-600 via-indigo-500 to-violet-500 flex items-center justify-center text-white text-2xl font-bold shadow-md">
                {initial}
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-xl font-bold text-gray-900 dark:text-white">
                    {email}
                  </h2>
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 dark:bg-emerald-950/80 text-emerald-700 dark:text-emerald-300">
                    <CheckIcon className="w-3.5 h-3.5" />
                    Verified
                  </span>
                </div>
                <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                  Member of Docket Cloud Platform
                </p>
              </div>
            </div>
            <div>
              <button
                type="button"
                onClick={handleLogout}
                className="inline-flex items-center gap-2 px-4 py-2 border border-red-200 dark:border-red-900/60 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 rounded-xl text-sm font-medium transition-colors"
              >
                <LogOutIcon className="w-4 h-4" />
                Sign Out
              </button>
            </div>
          </div>

          {/* Profile fields */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-6">
            <div className="p-4 rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-800/30">
              <div className="flex items-center gap-2 text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
                <UserIcon className="w-4 h-4" />
                <span>Email Address</span>
              </div>
              <div className="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
                {email}
              </div>
            </div>

            <div className="p-4 rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-800/30">
              <div className="flex items-center gap-2 text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
                <ShieldIcon className="w-4 h-4" />
                <span>Role & User Type</span>
              </div>
              <div className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                {role} ({userType})
              </div>
            </div>
          </div>
        </div>

        {/* Workspace & Tenants */}
        <div className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl p-6 shadow-xs space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-lg font-bold text-gray-900 dark:text-white">
                Workspaces & Organizations
              </h3>
              <p className="text-sm text-gray-500 dark:text-gray-400">
                Manage your active tenant memberships and access.
              </p>
            </div>
            <Link
              to="/create-first-tenant"
              className="inline-flex items-center gap-1.5 px-3.5 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-xl shadow-xs transition-colors"
            >
              <PlusIcon className="w-4 h-4" />
              New Tenant
            </Link>
          </div>

          <div className="p-4 rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-800/30 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400">
                <BuildingIcon className="w-5 h-5" />
              </div>
              <div>
                <div className="text-sm font-semibold text-gray-900 dark:text-white">
                  {user?.tenantId ? `Tenant #${user.tenantId}` : "Default Workspace"}
                </div>
                <div className="text-xs text-gray-500 dark:text-gray-400">
                  Status: <StatusBadge status={user?.status} />
                </div>
              </div>
            </div>
            <Link
              to="/tenants"
              className="text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:underline"
            >
              View Dashboard →
            </Link>
          </div>
        </div>

        {/* Security & Token Session */}
        <div className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl p-6 shadow-xs space-y-4">
          <div>
            <h3 className="text-lg font-bold text-gray-900 dark:text-white">
              Security & Active Session
            </h3>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              Details about your authenticated session.
            </p>
          </div>

          <div className="p-4 rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-800/30 flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 dark:bg-emerald-950/60 flex items-center justify-center text-emerald-600 dark:text-emerald-400 shrink-0">
              <ShieldIcon className="w-5 h-5" />
            </div>
            <div>
              <div className="text-sm font-semibold text-gray-900 dark:text-white">
                Valid JWT Authentication Token
              </div>
              <div className="text-xs text-gray-500 dark:text-gray-400">
                Session Expiration: {expiresAt}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

