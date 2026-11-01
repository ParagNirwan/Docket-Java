import { apiFetch } from "../lib/session";
import { useState } from "react";
import { type ApiErrorResponse, parseApiError, getFieldError } from "../lib/apiError";
import { useNavigate, Link } from "react-router-dom";
import { Building2, Globe, ArrowRight, Sparkles, Mail } from "lucide-react";

export function CreateFirstTenantPage() {
  const [tenantName, setTenantName] = useState("");
  const [tenantSlug, setTenantSlug] = useState("");
  const [isSlugTouched, setIsSlugTouched] = useState(false);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [apiError, setApiError] = useState<ApiErrorResponse | null>(null);

  // Auto-generate slug from organization name if user hasn't typed in slug manually
  function handleNameChange(e: React.ChangeEvent<HTMLInputElement>) {
    const val = e.target.value;
    setTenantName(val);
    if (!isSlugTouched) {
      const generatedSlug = val
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-|-$/g, "");
      setTenantSlug(generatedSlug);
    }
    if (fieldErrors.tenantName) {
      setFieldErrors((prev) => ({ ...prev, tenantName: "" }));
    }
  }

  function handleSlugChange(e: React.ChangeEvent<HTMLInputElement>) {
    setIsSlugTouched(true);
    setTenantSlug(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ""));
    if (fieldErrors.tenantSlug) {
      setFieldErrors((prev) => ({ ...prev, tenantSlug: "" }));
    }
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setApiError(null);
    setFieldErrors({});

    const cleanName = tenantName.trim();
    const cleanSlug = tenantSlug.trim();

    if (!cleanName) {
      setFieldErrors({ tenantName: "Organization name is required." });
      return;
    }
    if (!cleanSlug) {
      setFieldErrors({ tenantSlug: "URL slug is required." });
      return;
    }

    setLoading(true);

    try {
      const apiUrl = import.meta.env.VITE_API_URL || "/api";
      const token = localStorage.getItem("token");
      const response = await apiFetch(`${apiUrl}/tenants/create`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          name: cleanName,
          slug: cleanSlug,
          tenantName: cleanName,
          tenantSlug: cleanSlug,
        }),
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
    } catch {
      setApiError({
        code: "NETWORK_ERROR",
        message: "Failed to connect to the server. Please try again.",
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-[calc(100svh-4rem)] flex-1 items-center justify-center px-4 py-12 sm:px-6 lg:px-8">
      <div className="w-full max-w-lg">
        {/* Card */}
        <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white p-7 shadow-xs dark:border-gray-800 dark:bg-gray-900 sm:p-9">
          {/* Header */}
          <div className="text-center mb-7">
            <div className="mx-auto mb-3.5 flex h-13 w-13 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
              <Building2 className="h-7 w-7" strokeWidth={1.8} />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">
              Create your organization
            </h1>
            <p className="mt-1.5 text-xs sm:text-sm text-gray-500 dark:text-gray-400">
              Set up your workspace to manage documents, default trays, and team permissions
            </p>
          </div>

          {/* API Error Alert */}
          {apiError && (
            <div className="mb-6 rounded-xl border border-red-200 bg-red-50/60 p-3.5 text-xs text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
              {apiError.message || "An error occurred while creating your organization."}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Organization Name */}
            <div>
              <label
                htmlFor="org-name"
                className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
              >
                Organization / Tenant Name <span className="text-red-500">*</span>
              </label>
              <div className="relative">
                <input
                  id="org-name"
                  type="text"
                  required
                  placeholder="e.g. Acme Corporation, Morrison Legal"
                  value={tenantName}
                  onChange={handleNameChange}
                  className={`w-full rounded-xl border bg-gray-50/50 px-3.5 py-2.5 pl-10 text-sm text-gray-900 placeholder-gray-400 transition-all focus:bg-white focus:outline-none focus:ring-1 dark:bg-gray-800/50 dark:text-white dark:placeholder-gray-500 dark:focus:bg-gray-900 ${
                    fieldErrors.tenantName
                      ? "border-red-500 focus:border-red-500 focus:ring-red-500 dark:border-red-500"
                      : "border-gray-200 focus:border-indigo-500 focus:ring-indigo-500 dark:border-gray-700"
                  }`}
                  autoFocus
                />
                <Building2 className="pointer-events-none absolute left-3.5 top-3 h-4 w-4 text-gray-400 dark:text-gray-500" />
              </div>
              {fieldErrors.tenantName && (
                <p className="text-xs text-red-600 dark:text-red-400 mt-1 font-medium">
                  {fieldErrors.tenantName}
                </p>
              )}
            </div>

            {/* URL Slug */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label
                  htmlFor="org-slug"
                  className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300"
                >
                  Workspace URL Slug <span className="text-red-500">*</span>
                </label>
                <span className="text-[11px] text-gray-400 dark:text-gray-500">
                  Unique identifier
                </span>
              </div>
              <div className="relative flex items-center">
                <div className="pointer-events-none absolute left-3.5 top-3 flex items-center gap-1.5 text-gray-400 dark:text-gray-500">
                  <Globe className="h-4 w-4" />
                </div>
                <input
                  id="org-slug"
                  type="text"
                  required
                  placeholder="acme-corp"
                  value={tenantSlug}
                  onChange={handleSlugChange}
                  className={`w-full rounded-xl border bg-gray-50/50 px-3.5 py-2.5 pl-10 text-sm text-gray-900 placeholder-gray-400 transition-all focus:bg-white focus:outline-none focus:ring-1 dark:bg-gray-800/50 dark:text-white dark:placeholder-gray-500 dark:focus:bg-gray-900 ${
                    fieldErrors.tenantSlug
                      ? "border-red-500 focus:border-red-500 focus:ring-red-500 dark:border-red-500"
                      : "border-gray-200 focus:border-indigo-500 focus:ring-indigo-500 dark:border-gray-700"
                  }`}
                />
              </div>

              {/* URL Preview */}
              <div className="mt-2 flex items-center gap-1.5 text-[11px] text-gray-500 dark:text-gray-400">
                <Sparkles className="h-3 w-3 text-indigo-500" />
                <span>Workspace URL:</span>
                <code className="rounded-md bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 font-mono text-[11px] text-indigo-600 dark:text-indigo-400">
                  /workspace/{tenantSlug || "your-slug"}
                </code>
              </div>

              {fieldErrors.tenantSlug && (
                <p className="text-xs text-red-600 dark:text-red-400 mt-1 font-medium">
                  {fieldErrors.tenantSlug}
                </p>
              )}
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={loading || !tenantName.trim() || !tenantSlug.trim()}
              className="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-xs transition-colors hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:focus:ring-offset-gray-900"
            >
              <span>{loading ? "Creating organization..." : "Create Organization"}</span>
              <ArrowRight className="h-4 w-4" />
            </button>
          </form>

          {/* Footer Navigation */}
          <div className="mt-6 border-t border-gray-100 pt-5 text-center text-xs text-gray-500 dark:border-gray-800 dark:text-gray-400">
            Part of someone else’s organization?{" "}
            <Link
              to="/tenants"
              className="inline-flex items-center gap-1 font-semibold text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300 transition-colors"
            >
              <Mail className="h-3.5 w-3.5" />
              <span>View all workspaces & invites</span>
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
