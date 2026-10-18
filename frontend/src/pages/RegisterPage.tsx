import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { registerSchema } from "../lib/validation";
import { parseApiError, getFieldError, type ApiErrorResponse } from "../lib/apiError";
import { ErrorAlert } from "../components/ErrorAlert";
import { UserPlus, ArrowRight, User, Mail, Lock } from "lucide-react";

export function RegisterPage() {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [apiError, setApiError] = useState<ApiErrorResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setApiError(null);
    setFieldErrors({});

    // Client-side schema validation
    const result = registerSchema.safeParse({
      fullName,
      email,
      password,
      confirmPassword,
    });

    if (!result.success) {
      const errors: Record<string, string> = {};
      result.error.issues.forEach((issue) => {
        const fieldName = issue.path[0]?.toString();
        if (fieldName && !errors[fieldName]) {
          errors[fieldName] = issue.message;
        }
      });
      setFieldErrors(errors);
      return;
    }

    setLoading(true);

    try {
      const apiUrl = import.meta.env.VITE_API_URL || "/api";
      const nameParts = fullName.trim().split(/\s+/);
      const firstName = nameParts[0] || "";
      const lastName = nameParts.slice(1).join(" ") || firstName;

      const response = await fetch(`${apiUrl}/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ firstName, lastName, email, password }),
      });

      if (!response.ok) {
        const parsed = await parseApiError(response);
        setApiError(parsed);

        // Bind backend field errors to inputs
        if (parsed.errors) {
          const backendFieldErrors: Record<string, string> = {};
          Object.keys(parsed.errors).forEach((key) => {
            const err = getFieldError(parsed.errors, key);
            if (err) {
              if (key === "firstName" || key === "lastName") {
                backendFieldErrors["fullName"] = err;
              } else {
                backendFieldErrors[key] = err;
              }
            }
          });
          setFieldErrors((prev) => ({ ...prev, ...backendFieldErrors }));
        }

        return;
      }

      // Registration succeeded - navigate to login
      navigate("/login", {
        state: { message: "Account created successfully! Please sign in." },
      });
    } catch {
      setApiError({
        code: "NETWORK_ERROR",
        message: "Network error occurred. Please check your internet connection and try again.",
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-[calc(100svh-4rem)] flex-1 items-center justify-center px-4 py-12 sm:px-6 lg:px-8">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white p-7 shadow-xs dark:border-gray-800 dark:bg-gray-900 sm:p-8">
          {/* Header */}
          <div className="text-center mb-6">
            <div className="mx-auto mb-3.5 flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
              <UserPlus className="h-6 w-6" strokeWidth={1.8} />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">
              Create an account
            </h1>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              Join Docket to securely organize, stage, and review documents
            </p>
          </div>

          {apiError && (
            <ErrorAlert
              error={apiError}
              className="mb-5"
              onDismiss={() => setApiError(null)}
            />
          )}

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {/* Full Name */}
            <div>
              <label
                htmlFor="register-fullname"
                className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
              >
                Full Name
              </label>
              <div className="relative">
                <input
                  id="register-fullname"
                  type="text"
                  placeholder="Jane Doe"
                  value={fullName}
                  onChange={(e) => {
                    setFullName(e.target.value);
                    if (fieldErrors.fullName) {
                      setFieldErrors((prev) => ({ ...prev, fullName: "" }));
                    }
                  }}
                  className={`w-full rounded-xl border bg-gray-50/50 px-3.5 py-2.5 pl-10 text-sm text-gray-900 placeholder-gray-400 transition-all focus:bg-white focus:outline-none focus:ring-1 dark:bg-gray-800/50 dark:text-white dark:placeholder-gray-500 dark:focus:bg-gray-900 ${
                    fieldErrors.fullName
                      ? "border-red-500 focus:border-red-500 focus:ring-red-500 dark:border-red-500"
                      : "border-gray-200 focus:border-indigo-500 focus:ring-indigo-500 dark:border-gray-700"
                  }`}
                />
                <User className="pointer-events-none absolute left-3.5 top-3 h-4 w-4 text-gray-400 dark:text-gray-500" />
              </div>
              {fieldErrors.fullName && (
                <p className="text-xs text-red-600 dark:text-red-400 mt-1 font-medium">
                  {fieldErrors.fullName}
                </p>
              )}
            </div>

            {/* Email */}
            <div>
              <label
                htmlFor="register-email"
                className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
              >
                Email Address
              </label>
              <div className="relative">
                <input
                  id="register-email"
                  type="email"
                  placeholder="jane@company.com"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (fieldErrors.email) {
                      setFieldErrors((prev) => ({ ...prev, email: "" }));
                    }
                  }}
                  className={`w-full rounded-xl border bg-gray-50/50 px-3.5 py-2.5 pl-10 text-sm text-gray-900 placeholder-gray-400 transition-all focus:bg-white focus:outline-none focus:ring-1 dark:bg-gray-800/50 dark:text-white dark:placeholder-gray-500 dark:focus:bg-gray-900 ${
                    fieldErrors.email
                      ? "border-red-500 focus:border-red-500 focus:ring-red-500 dark:border-red-500"
                      : "border-gray-200 focus:border-indigo-500 focus:ring-indigo-500 dark:border-gray-700"
                  }`}
                />
                <Mail className="pointer-events-none absolute left-3.5 top-3 h-4 w-4 text-gray-400 dark:text-gray-500" />
              </div>
              {fieldErrors.email && (
                <p className="text-xs text-red-600 dark:text-red-400 mt-1 font-medium">
                  {fieldErrors.email}
                </p>
              )}
            </div>

            {/* Password */}
            <div>
              <label
                htmlFor="register-password"
                className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
              >
                Password
              </label>
              <div className="relative">
                <input
                  id="register-password"
                  type="password"
                  placeholder="At least 8 characters"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (fieldErrors.password) {
                      setFieldErrors((prev) => ({ ...prev, password: "" }));
                    }
                  }}
                  className={`w-full rounded-xl border bg-gray-50/50 px-3.5 py-2.5 pl-10 text-sm text-gray-900 placeholder-gray-400 transition-all focus:bg-white focus:outline-none focus:ring-1 dark:bg-gray-800/50 dark:text-white dark:placeholder-gray-500 dark:focus:bg-gray-900 ${
                    fieldErrors.password
                      ? "border-red-500 focus:border-red-500 focus:ring-red-500 dark:border-red-500"
                      : "border-gray-200 focus:border-indigo-500 focus:ring-indigo-500 dark:border-gray-700"
                  }`}
                />
                <Lock className="pointer-events-none absolute left-3.5 top-3 h-4 w-4 text-gray-400 dark:text-gray-500" />
              </div>
              {fieldErrors.password ? (
                <p className="text-xs text-red-600 dark:text-red-400 mt-1 font-medium">
                  {fieldErrors.password}
                </p>
              ) : (
                <p className="text-[11px] text-gray-500 dark:text-gray-400 mt-1">
                  Must be at least 8 characters with letters and numbers.
                </p>
              )}
            </div>

            {/* Confirm Password */}
            <div>
              <label
                htmlFor="register-confirm-password"
                className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
              >
                Confirm Password
              </label>
              <div className="relative">
                <input
                  id="register-confirm-password"
                  type="password"
                  placeholder="Re-type your password"
                  value={confirmPassword}
                  onChange={(e) => {
                    setConfirmPassword(e.target.value);
                    if (fieldErrors.confirmPassword) {
                      setFieldErrors((prev) => ({ ...prev, confirmPassword: "" }));
                    }
                  }}
                  className={`w-full rounded-xl border bg-gray-50/50 px-3.5 py-2.5 pl-10 text-sm text-gray-900 placeholder-gray-400 transition-all focus:bg-white focus:outline-none focus:ring-1 dark:bg-gray-800/50 dark:text-white dark:placeholder-gray-500 dark:focus:bg-gray-900 ${
                    fieldErrors.confirmPassword
                      ? "border-red-500 focus:border-red-500 focus:ring-red-500 dark:border-red-500"
                      : "border-gray-200 focus:border-indigo-500 focus:ring-indigo-500 dark:border-gray-700"
                  }`}
                />
                <Lock className="pointer-events-none absolute left-3.5 top-3 h-4 w-4 text-gray-400 dark:text-gray-500" />
              </div>
              {fieldErrors.confirmPassword && (
                <p className="text-xs text-red-600 dark:text-red-400 mt-1 font-medium">
                  {fieldErrors.confirmPassword}
                </p>
              )}
            </div>

            <button
              type="submit"
              disabled={loading}
              className="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-xs transition-colors hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:focus:ring-offset-gray-900"
            >
              <span>{loading ? "Creating account..." : "Create Account"}</span>
              <ArrowRight className="h-4 w-4" />
            </button>
          </form>

          {/* Footer Navigation */}
          <div className="mt-6 border-t border-gray-100 pt-5 text-center text-xs text-gray-500 dark:border-gray-800 dark:text-gray-400">
            Already have an account?{" "}
            <Link
              to="/login"
              className="font-semibold text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300 transition-colors"
            >
              Sign in here
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}