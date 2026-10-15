import { useState } from "react";
import { type ApiErrorResponse, parseApiError, getFieldError } from "../lib/apiError";
import { useNavigate, Link } from "react-router-dom";

export function CreateFirstTenantPage() {
  const [tenantName, setTenantName] = useState("");
  const [tenantSlug, setTenantSlug] = useState("");
  const navigate = useNavigate();

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [apiError, setApiError] = useState<ApiErrorResponse | null>(null);

  async function handleSubmit(e: React.SubmitEvent<HTMLFormElement>) {
    e.preventDefault();
    setApiError(null);
    setFieldErrors({});

    const apiUrl = import.meta.env.VITE_API_URL || "/api";
    const token = localStorage.getItem("token");
    const response = await fetch(`${apiUrl}/tenants/create`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ tenantName, slug: tenantSlug, tenantSlug }),
    });

    if (!response.ok) {
      const parsed = await parseApiError(response);
      setApiError(parsed);
      if (parsed.errors) {
        const backendFieldErrors: Record<string, string> = {};
        Object.keys(parsed.errors).forEach((key) => {
          const err = getFieldError(parsed.errors, key);
          if (err) {
            backendFieldErrors[key] = err;
          }
        });
        setFieldErrors((prev) => ({ ...prev, ...backendFieldErrors }));
      }
    } else {
      navigate("/tenants");
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-950 px-4 sm:px-6">
      <div className="w-full bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-xl p-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">
            Create your first Tenant
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Set up your organization workspace to get started.
          </p>
        </div>

        {apiError && (
          <div className="p-3 text-sm text-red-600 bg-red-50 dark:bg-red-950/50 dark:text-red-400 rounded-lg">
            {apiError.message || "An error occurred."}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Tenant Name
            </label>
            <input
              type="text"
              value={tenantName}
              onChange={(e) => setTenantName(e.target.value)}
              placeholder="Acme Corp"
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg bg-transparent text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            {fieldErrors.tenantName && (
              <p className="text-xs text-red-500 mt-1">
                {fieldErrors.tenantName}
              </p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Tenant Slug
            </label>
            <input
              type="text"
              value={tenantSlug}
              onChange={(e) => setTenantSlug(e.target.value)}
              placeholder="acme-corp"
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-700 rounded-lg bg-transparent text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            {fieldErrors.tenantSlug && (
              <p className="text-xs text-red-500 mt-1">
                {fieldErrors.tenantSlug}
              </p>
            )}
          </div>

          <button
            type="submit"
            className="w-full py-2.5 px-4 bg-indigo-600 hover:bg-indigo-700 text-white font-medium rounded-lg transition-colors shadow-sm"
          >
            Create Tenant
          </button>
        </form>

        <div className="text-center text-sm text-gray-500 dark:text-gray-400 pt-2 border-t border-gray-100 dark:border-gray-800">
          Part of someone else's organisation?{" "}
          <Link
            to="/invites"
            className="text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
          >
            View Invites
          </Link>
        </div>
      </div>
    </div>
  );
}
