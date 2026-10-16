import { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import {
  BuildingIcon,
  PlusIcon,
  SettingsIcon,
  ShieldIcon,
  ChevronRightIcon,
} from "../components/icons";
import { StatusBadge } from "../components/ui/StatusBadge";

interface TenantItem {
  id?: number | string;
  name?: string;
  tenantName?: string;
  slug?: string;
  tenantSlug?: string;
  role?: string;
  status?: string;
}

export function TenantsPage() {
  const { token, openSettingsModal } = useAuth();
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadTenants() {
      if (!token) return;
      try {
        const apiUrl = import.meta.env.VITE_API_URL || "/api";
        const res = await fetch(`${apiUrl}/users/list-tenants`, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (res.ok) {
          const data = await res.json();
          if (Array.isArray(data)) {
            setTenants(data);
          }
        } else {
          setError("Unable to load organizations");
        }
      } catch {
        setError("Network connection issue while loading organizations");
      } finally {
        setLoading(false);
      }
    }

    loadTenants();
  }, [token]);

  return (
    <div className="w-full px-4 py-8 sm:px-6 lg:px-8">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-gray-900 dark:text-white">
            Organizations & Tenants
          </h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Select a tenant workspace to review dockets and manage documents.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={openSettingsModal}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-sm font-medium border border-gray-200 dark:border-gray-800 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-xl text-gray-700 dark:text-gray-200 transition-colors"
          >
            <SettingsIcon className="w-4 h-4" />
            Account Settings
          </button>
          <Link
            to="/create-first-tenant"
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-xl shadow-xs transition-colors"
          >
            <PlusIcon className="w-4 h-4" />
            New Tenant
          </Link>
        </div>
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex items-center justify-center p-16 text-sm text-gray-500 dark:text-gray-400">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600 mr-3" />
          Loading your workspaces...
        </div>
      ) : error ? (
        <div className="p-4 rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 text-sm">
          {error}
        </div>
      ) : tenants.length === 0 ? (
        <div className="text-center py-16 px-4 bg-white dark:bg-gray-900 border border-dashed border-gray-300 dark:border-gray-800 rounded-2xl">
          <div className="w-12 h-12 rounded-2xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center mx-auto mb-4">
            <BuildingIcon className="w-6 h-6" />
          </div>
          <h3 className="text-lg font-bold text-gray-900 dark:text-white">
            No tenants yet
          </h3>
          <p className="text-sm text-gray-500 dark:text-gray-400 max-w-sm mx-auto mt-1 mb-6">
            You don't have any workspaces yet. Create your first tenant to start
            managing your legal dockets.
          </p>
          <Link
            to="/create-first-tenant"
            className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 rounded-xl shadow-sm transition-colors"
          >
            <PlusIcon className="w-4 h-4" />
            Create Your First Tenant
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {tenants.map((t, index) => {
            const name =
              t.tenantName || t.name || `Tenant #${t.id || index + 1}`;
            const slug = t.tenantSlug || t.slug || "";
            return (
              <Link
                key={t.id || index}
                to={`/workspace/${encodeURIComponent(String(t.slug || t.tenantSlug ))}`}
                state={{ tenantName: name }}
                className="group relative bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl p-5 shadow-xs hover:shadow-md hover:border-indigo-300 dark:hover:border-indigo-800 transition-all flex flex-col justify-between focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-4">
                    <div className="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 flex items-center justify-center font-bold">
                      <BuildingIcon className="w-5 h-5" />
                    </div>
                    <StatusBadge status={t.status || "Inactive"} />
                  </div>
                  <h3 className="text-base font-bold text-gray-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                    {name}
                  </h3>
                  {slug && (
                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-1 font-mono">
                      {slug}
                    </p>
                  )}
                </div>

                <div className="pt-5 mt-4 border-t border-gray-100 dark:border-gray-800 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
                  <span className="flex items-center gap-1">
                    <ShieldIcon className="w-3.5 h-3.5" />
                    {t.role || "Member"}
                  </span>
                  <span className="flex items-center gap-1 text-indigo-600 dark:text-indigo-400 font-medium group-hover:translate-x-0.5 transition-transform">
                    Enter Workspace <ChevronRightIcon className="w-3.5 h-3.5" />
                  </span>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
