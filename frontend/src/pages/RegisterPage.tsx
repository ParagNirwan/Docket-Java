import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { registerSchema } from "../lib/validation";
import { parseApiError, getFieldError, type ApiErrorResponse } from "../lib/apiError";
import { ErrorAlert } from "../components/ErrorAlert";

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
      const apiUrl = import.meta.env.VITE_API_URL || '/api';
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
              if (key === 'firstName' || key === 'lastName') {
                backendFieldErrors['fullName'] = err;
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
    <div className="w-full max-w-md mx-auto px-4 py-8 sm:px-6 lg:px-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-[var(--color-ink)]">Create an Account</h1>
        <p className="text-xs text-[var(--color-muted)] mt-1">
          Join Docket to securely archive, stage, and review documents.
        </p>
      </div>

      {apiError && (
        <ErrorAlert
          error={apiError}
          className="mb-5"
          onDismiss={() => setApiError(null)}
        />
      )}

      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {/* Full Name */}
        <div>
          <label className="block text-xs font-semibold text-[var(--color-ink)] mb-1">
            Full Name
          </label>
          <input
            type="text"
            placeholder="Jane Doe"
            value={fullName}
            onChange={(e) => {
              setFullName(e.target.value);
              if (fieldErrors.fullName) {
                setFieldErrors((prev) => ({ ...prev, fullName: "" }));
              }
            }}
            className={`w-full border rounded-md px-3 py-2 text-sm bg-transparent focus:outline-none focus:ring-2 ${
              fieldErrors.fullName
                ? "border-red-500 focus:ring-red-300"
                : "border-[var(--color-line)] focus:ring-[var(--color-vault)]"
            }`}
          />
          {fieldErrors.fullName && (
            <p className="text-red-600 text-xs mt-1 font-medium">{fieldErrors.fullName}</p>
          )}
        </div>

        {/* Email */}
        <div>
          <label className="block text-xs font-semibold text-[var(--color-ink)] mb-1">
            Email Address
          </label>
          <input
            type="email"
            placeholder="jane@example.com"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              if (fieldErrors.email) {
                setFieldErrors((prev) => ({ ...prev, email: "" }));
              }
            }}
            className={`w-full border rounded-md px-3 py-2 text-sm bg-transparent focus:outline-none focus:ring-2 ${
              fieldErrors.email
                ? "border-red-500 focus:ring-red-300"
                : "border-[var(--color-line)] focus:ring-[var(--color-vault)]"
            }`}
          />
          {fieldErrors.email && (
            <p className="text-red-600 text-xs mt-1 font-medium">{fieldErrors.email}</p>
          )}
        </div>

        {/* Password */}
        <div>
          <label className="block text-xs font-semibold text-[var(--color-ink)] mb-1">
            Password
          </label>
          <input
            type="password"
            placeholder="At least 8 characters"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              if (fieldErrors.password) {
                setFieldErrors((prev) => ({ ...prev, password: "" }));
              }
            }}
            className={`w-full border rounded-md px-3 py-2 text-sm bg-transparent focus:outline-none focus:ring-2 ${
              fieldErrors.password
                ? "border-red-500 focus:ring-red-300"
                : "border-[var(--color-line)] focus:ring-[var(--color-vault)]"
            }`}
          />
          {fieldErrors.password ? (
            <p className="text-red-600 text-xs mt-1 font-medium">{fieldErrors.password}</p>
          ) : (
            <p className="text-[var(--color-muted)] text-[11px] mt-1">
              Minimum 8 characters with at least one letter and one number or symbol.
            </p>
          )}
        </div>

        {/* Confirm Password */}
        <div>
          <label className="block text-xs font-semibold text-[var(--color-ink)] mb-1">
            Confirm Password
          </label>
          <input
            type="password"
            placeholder="Re-type your password"
            value={confirmPassword}
            onChange={(e) => {
              setConfirmPassword(e.target.value);
              if (fieldErrors.confirmPassword) {
                setFieldErrors((prev) => ({ ...prev, confirmPassword: "" }));
              }
            }}
            className={`w-full border rounded-md px-3 py-2 text-sm bg-transparent focus:outline-none focus:ring-2 ${
              fieldErrors.confirmPassword
                ? "border-red-500 focus:ring-red-300"
                : "border-[var(--color-line)] focus:ring-[var(--color-vault)]"
            }`}
          />
          {fieldErrors.confirmPassword && (
            <p className="text-red-600 text-xs mt-1 font-medium">{fieldErrors.confirmPassword}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={loading}
          className="mt-2 bg-[var(--color-vault)] text-white rounded-md px-4 py-2 hover:opacity-90 transition-opacity font-medium text-sm disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? "Creating account..." : "Create Account"}
        </button>
      </form>

      <p className="mt-5 text-center text-xs text-[var(--color-muted)]">
        Already have an account?{" "}
        <Link to="/login" className="text-[var(--color-vault)] font-medium hover:underline">
          Log in here.
        </Link>
      </p>
    </div>
  );
}