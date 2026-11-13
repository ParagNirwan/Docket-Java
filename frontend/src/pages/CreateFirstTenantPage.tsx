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
    <div className="flex min-h-[calc(100svh-4rem)] flex-1 items-center justify-center px-4 py-10 sm:px-6 lg:px-8 bg-white">
      <div className="w-full max-w-lg">
        {/* Card */}
        <div className="overflow-hidden rounded-[8px] border border-[#DDDDDD] bg-white p-7 shadow-[0_4px_12px_rgba(0,0,0,0.1)] sm:p-9">
          {/* Header */}
          <div className="text-center mb-6">
            <div className="mx-auto mb-3.5 flex h-11 w-11 items-center justify-center rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] text-[#1D1C1D] shadow-[0_1px_2px_rgba(0,0,0,0.05)]">
              <Building2 className="h-6 w-6" strokeWidth={1.8} />
            </div>
            <h1 className="text-[22px] font-bold tracking-tight text-[#1D1C1D] leading-tight">
              Create your organization
            </h1>
            <p className="mt-1 text-[13px] text-[#616061]">
              Set up your workspace to manage documents, default trays, and team permissions
            </p>
          </div>

          {/* API Error Alert */}
          {apiError && (
            <div className="mb-5 rounded-[4px] border border-[#E01E5A]/30 bg-[#E01E5A]/10 p-3.5 text-[12px] font-medium text-[#E01E5A]">
              {apiError.message || "An error occurred while creating your organization."}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Organization Name */}
            <div>
              <label
                htmlFor="org-name"
                className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-1.5"
              >
                Organization / Tenant Name <span className="text-[#E01E5A]">*</span>
              </label>
              <div className="relative">
                <input
                  id="org-name"
                  type="text"
                  required
                  placeholder="e.g. Acme Corporation, Morrison Legal"
                  value={tenantName}
                  onChange={handleNameChange}
                  className={`w-full rounded-[8px] border bg-[#F8F8F8] px-3.5 py-2 pl-10 text-[14px] text-[#1D1C1D] placeholder-[#616061] transition-all focus:bg-white focus:outline-none focus:ring-1 ${
                    fieldErrors.tenantName
                      ? "border-[#E01E5A] focus:border-[#E01E5A] focus:ring-[#E01E5A]"
                      : "border-[#DDDDDD] focus:border-[#36C5F0] focus:ring-[#36C5F0]"
                  }`}
                  autoFocus
                />
                <Building2 className="pointer-events-none absolute left-3.5 top-2.5 h-4 w-4 text-[#616061]" />
              </div>
              {fieldErrors.tenantName && (
                <p className="text-[12px] text-[#E01E5A] mt-1 font-medium">
                  {fieldErrors.tenantName}
                </p>
              )}
            </div>

            {/* URL Slug */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label
                  htmlFor="org-slug"
                  className="block text-[11px] font-bold uppercase tracking-wider text-[#616061]"
                >
                  Workspace URL Slug <span className="text-[#E01E5A]">*</span>
                </label>
                <span className="text-[11px] text-[#616061]">
                  Unique identifier
                </span>
              </div>
              <div className="relative flex items-center">
                <div className="pointer-events-none absolute left-3.5 top-2.5 flex items-center gap-1.5 text-[#616061]">
                  <Globe className="h-4 w-4" />
                </div>
                <input
                  id="org-slug"
                  type="text"
                  required
                  placeholder="acme-corp"
                  value={tenantSlug}
                  onChange={handleSlugChange}
                  className={`w-full rounded-[8px] border bg-[#F8F8F8] px-3.5 py-2 pl-10 text-[14px] text-[#1D1C1D] placeholder-[#616061] transition-all focus:bg-white focus:outline-none focus:ring-1 ${
                    fieldErrors.tenantSlug
                      ? "border-[#E01E5A] focus:border-[#E01E5A] focus:ring-[#E01E5A]"
                      : "border-[#DDDDDD] focus:border-[#36C5F0] focus:ring-[#36C5F0]"
                  }`}
                />
              </div>

              {/* URL Preview */}
              <div className="mt-2 flex items-center gap-1.5 text-[11px] text-[#616061]">
                <Sparkles className="h-3 w-3 text-[#ECB22E]" />
                <span>Workspace URL:</span>
                <code className="rounded-[4px] bg-[#F8F8F8] border border-[#DDDDDD] px-1.5 py-0.5 font-mono text-[11px] text-[#1164A3]">
                  /workspace/{tenantSlug || "your-slug"}
                </code>
              </div>

              {fieldErrors.tenantSlug && (
                <p className="text-[12px] text-[#E01E5A] mt-1 font-medium">
                  {fieldErrors.tenantSlug}
                </p>
              )}
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={loading || !tenantName.trim() || !tenantSlug.trim()}
              className="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-[8px] bg-[#007A5A] px-4 py-2 text-[14px] font-bold text-white shadow-[0_1px_2px_rgba(0,0,0,0.1)] transition-colors hover:bg-[#00664B] focus:outline-none focus:ring-2 focus:ring-[#36C5F0] focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <span>{loading ? "Creating organization..." : "Create Organization"}</span>
              <ArrowRight className="h-4 w-4" />
            </button>
          </form>

          {/* Footer Navigation */}
          <div className="mt-6 border-t border-[#DDDDDD] pt-4 text-center text-[12px] text-[#616061]">
            Part of someone else’s organization?{" "}
            <Link
              to="/tenants"
              className="inline-flex items-center gap-1 font-semibold text-[#1164A3] hover:underline transition-colors"
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
