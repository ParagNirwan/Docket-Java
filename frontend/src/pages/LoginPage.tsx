import { useState } from "react";
import { useNavigate, Link, useLocation } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import { apiFetch, retryAfterMessage } from "../lib/session";
import { Lock, Mail, ArrowRight, CheckCircle2, ShieldCheck, ArrowLeft } from "lucide-react";
import { verify2faLogin } from "../services/twoFactorService";

type LoginLocationState = {
  message?: string;
  email?: string;
  redirectTo?: string;
} | null;

export function LoginPage() {
  const location = useLocation();
  const routeState = location.state as LoginLocationState;
  const [email, setEmail] = useState(routeState?.email ?? "");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [twoFactorTempToken, setTwoFactorTempToken] = useState<string | null>(null);
  const [twoFactorCode, setTwoFactorCode] = useState("");
  const navigate = useNavigate();
  const { login } = useAuth();

  const successMessage = routeState?.message;

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const apiUrl = import.meta.env.VITE_API_URL || "/api";
      const response = await fetch(`${apiUrl}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        setError(
          response.status === 429
            ? retryAfterMessage(response)
            : "Invalid email or password. Please try again."
        );
        setLoading(false);
        return;
      }

      const data = await response.json();

      if (data.twoFactorRequired) {
        setTwoFactorTempToken(data.twoFactorTempToken);
        setTwoFactorCode("");
        setLoading(false);
        return;
      }

      await finishLogin(data.token);
    } catch {
      setError("Network error occurred. Please check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }

  async function handleVerify2fa(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!twoFactorTempToken || !twoFactorCode.trim()) return;

    setError("");
    setLoading(true);

    try {
      const { token } = await verify2faLogin(twoFactorTempToken, twoFactorCode.trim());
      await finishLogin(token);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Verification failed");
      setLoading(false);
    }
  }

  async function finishLogin(authToken: string) {
    login(authToken);

    if (routeState?.redirectTo) {
      return;
    }

    const apiUrl = import.meta.env.VITE_API_URL || "/api";
    const tenantsResp = await apiFetch(`${apiUrl}/users/list-tenants`, {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${authToken}`,
      },
    });

    const tenants = await tenantsResp.json();

    if (!tenants || tenants.length === 0) {
      navigate("/create-first-tenant");
      return;
    }

    navigate("/tenants");
  }

  return (
    <div className="flex min-h-[calc(100svh-4rem)] flex-1 items-center justify-center px-4 py-12 sm:px-6 lg:px-8">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white p-7 shadow-xs dark:border-gray-800 dark:bg-gray-900 sm:p-8">
          {/* Header Icon & Title */}
          <div className="text-center mb-6">
            <div className="mx-auto mb-3.5 flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
              {twoFactorTempToken ? (
                <ShieldCheck className="h-6 w-6" strokeWidth={1.8} />
              ) : (
                <Lock className="h-6 w-6" strokeWidth={1.8} />
              )}
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">
              {twoFactorTempToken ? "Two-Factor Verification" : "Sign in to Docket"}
            </h1>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              {twoFactorTempToken
                ? "Enter the 6-digit code from your authenticator app or an 8-character backup code"
                : "Enter your credentials to access your tenant workspaces"}
            </p>
          </div>

          {/* Success Banner from Registration */}
          {successMessage && !twoFactorTempToken && (
            <div className="mb-5 flex items-start gap-2.5 rounded-xl border border-emerald-200 bg-emerald-50/60 p-3.5 text-xs text-emerald-800 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-300">
              <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400 mt-0.5" />
              <span>{successMessage}</span>
            </div>
          )}

          {/* Error Message */}
          {error && (
            <div className="mb-5 rounded-xl border border-red-200 bg-red-50/60 p-3.5 text-xs text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
              {error}
            </div>
          )}

          {/* 2FA Form */}
          {twoFactorTempToken ? (
            <form onSubmit={handleVerify2fa} className="space-y-4">
              <div>
                <label
                  htmlFor="two-factor-code"
                  className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
                >
                  Verification Code / Backup Code
                </label>
                <input
                  id="two-factor-code"
                  type="text"
                  required
                  autoFocus
                  placeholder="e.g. 123456 or A1B2C3D4"
                  value={twoFactorCode}
                  onChange={(e) => setTwoFactorCode(e.target.value)}
                  className="w-full text-center font-mono tracking-widest text-lg font-bold rounded-xl border border-gray-200 bg-gray-50/50 px-3.5 py-2.5 text-gray-900 placeholder-gray-400 transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-700 dark:bg-gray-800/50 dark:text-white dark:placeholder-gray-500 dark:focus:bg-gray-900"
                />
              </div>

              <button
                type="submit"
                disabled={loading || !twoFactorCode.trim()}
                className="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-xs transition-colors hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:focus:ring-offset-gray-900"
              >
                <span>{loading ? "Verifying..." : "Verify & Sign In"}</span>
                <ArrowRight className="h-4 w-4" />
              </button>

              <button
                type="button"
                onClick={() => {
                  setTwoFactorTempToken(null);
                  setError("");
                }}
                className="w-full inline-flex items-center justify-center gap-1 text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 py-1"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                Back to email & password
              </button>
            </form>
          ) : (
            /* Login Form */
            <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label
                htmlFor="login-email"
                className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5"
              >
                Email Address
              </label>
              <div className="relative">
                <input
                  id="login-email"
                  type="email"
                  required
                  autoComplete="email"
                  placeholder="you@company.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full rounded-xl border border-gray-200 bg-gray-50/50 px-3.5 py-2.5 pl-10 text-sm text-gray-900 placeholder-gray-400 transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-700 dark:bg-gray-800/50 dark:text-white dark:placeholder-gray-500 dark:focus:bg-gray-900"
                />
                <Mail className="pointer-events-none absolute left-3.5 top-3 h-4 w-4 text-gray-400 dark:text-gray-500" />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label
                  htmlFor="login-password"
                  className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300"
                >
                  Password
                </label>
                <Link
                  to="/forgot-password"
                  className="text-xs font-semibold text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300 transition-colors"
                >
                  Forgot password?
                </Link>
              </div>
              <div className="relative">
                <input
                  id="login-password"
                  type="password"
                  required
                  autoComplete="current-password"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full rounded-xl border border-gray-200 bg-gray-50/50 px-3.5 py-2.5 pl-10 text-sm text-gray-900 placeholder-gray-400 transition-all focus:border-indigo-500 focus:bg-white focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-700 dark:bg-gray-800/50 dark:text-white dark:placeholder-gray-500 dark:focus:bg-gray-900"
                />
                <Lock className="pointer-events-none absolute left-3.5 top-3 h-4 w-4 text-gray-400 dark:text-gray-500" />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-xs transition-colors hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:focus:ring-offset-gray-900"
            >
              <span>{loading ? "Signing in..." : "Sign In"}</span>
              <ArrowRight className="h-4 w-4" />
            </button>
          </form>
          )}

          {/* Footer Navigation */}
          <div className="mt-6 border-t border-gray-100 pt-5 text-center text-xs text-gray-500 dark:border-gray-800 dark:text-gray-400">
            Don’t have an account yet?{" "}
            <Link
              to="/register"
              className="font-semibold text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300 transition-colors"
            >
              Create an account
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
