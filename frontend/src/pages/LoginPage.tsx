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
    <div className="flex min-h-[calc(100svh-4rem)] flex-1 items-center justify-center px-4 py-10 sm:px-6 lg:px-8 bg-white">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="overflow-hidden rounded-[8px] border border-[#DDDDDD] bg-white p-7 shadow-[0_4px_12px_rgba(0,0,0,0.1)] sm:p-8">
          {/* Header Icon & Title */}
          <div className="text-center mb-6">
            <div className="mx-auto mb-3.5 flex h-11 w-11 items-center justify-center rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] text-[#1D1C1D] shadow-[0_1px_2px_rgba(0,0,0,0.05)]">
              {twoFactorTempToken ? (
                <ShieldCheck className="h-5 w-5 text-[#2EB67D]" strokeWidth={1.8} />
              ) : (
                <Lock className="h-5 w-5 text-[#1D1C1D]" strokeWidth={1.8} />
              )}
            </div>
            <h1 className="text-[22px] font-bold tracking-tight text-[#1D1C1D] leading-tight">
              {twoFactorTempToken ? "Two-Factor Verification" : "Sign in to Docket"}
            </h1>
            <p className="mt-1 text-[13px] text-[#616061]">
              {twoFactorTempToken
                ? "Enter the 6-digit code from your authenticator app or an 8-character backup code"
                : "Enter your credentials to access your tenant workspaces"}
            </p>
          </div>

          {/* Success Banner from Registration */}
          {successMessage && !twoFactorTempToken && (
            <div className="mb-4 flex items-start gap-2.5 rounded-[4px] border border-[#2EB67D]/30 bg-[#2EB67D]/10 p-3 text-[12px] font-medium text-[#1b7a51]">
              <CheckCircle2 className="h-4 w-4 shrink-0 text-[#2EB67D] mt-0.5" />
              <span>{successMessage}</span>
            </div>
          )}

          {/* Error Message */}
          {error && (
            <div className="mb-4 rounded-[4px] border border-[#E01E5A]/30 bg-[#E01E5A]/10 p-3 text-[12px] font-medium text-[#E01E5A]">
              {error}
            </div>
          )}

          {/* 2FA Form */}
          {twoFactorTempToken ? (
            <form onSubmit={handleVerify2fa} className="space-y-4">
              <div>
                <label
                  htmlFor="two-factor-code"
                  className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-1.5"
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
                  className="w-full text-center font-mono tracking-widest text-lg font-bold rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3.5 py-2 text-[#1D1C1D] placeholder-[#616061] transition-all focus:border-[#36C5F0] focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#36C5F0]"
                />
              </div>

              <button
                type="submit"
                disabled={loading || !twoFactorCode.trim()}
                className="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-[8px] bg-[#007A5A] px-4 py-2 text-[14px] font-bold text-white shadow-[0_1px_2px_rgba(0,0,0,0.1)] transition-colors hover:bg-[#00664B] focus:outline-none focus:ring-2 focus:ring-[#36C5F0] focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
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
                className="w-full inline-flex items-center justify-center gap-1 text-[12px] text-[#616061] hover:text-[#1D1C1D] py-1"
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
                className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-1.5"
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
                  className="w-full rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3.5 py-2 pl-10 text-[14px] text-[#1D1C1D] placeholder-[#616061] transition-all focus:border-[#36C5F0] focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#36C5F0]"
                />
                <Mail className="pointer-events-none absolute left-3.5 top-2.5 h-4 w-4 text-[#616061]" />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label
                  htmlFor="login-password"
                  className="block text-[11px] font-bold uppercase tracking-wider text-[#616061]"
                >
                  Password
                </label>
                <Link
                  to="/forgot-password"
                  className="text-[12px] font-semibold text-[#1164A3] hover:underline transition-colors"
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
                  className="w-full rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3.5 py-2 pl-10 text-[14px] text-[#1D1C1D] placeholder-[#616061] transition-all focus:border-[#36C5F0] focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#36C5F0]"
                />
                <Lock className="pointer-events-none absolute left-3.5 top-2.5 h-4 w-4 text-[#616061]" />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-[8px] bg-[#007A5A] px-4 py-2 text-[14px] font-bold text-white shadow-[0_1px_2px_rgba(0,0,0,0.1)] transition-colors hover:bg-[#00664B] focus:outline-none focus:ring-2 focus:ring-[#36C5F0] focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <span>{loading ? "Signing in..." : "Sign In"}</span>
              <ArrowRight className="h-4 w-4" />
            </button>
          </form>
          )}

          {/* Footer Navigation */}
          <div className="mt-6 border-t border-[#DDDDDD] pt-4 text-center text-[12px] text-[#616061]">
            Don’t have an account yet?{" "}
            <Link
              to="/register"
              className="font-semibold text-[#1164A3] hover:underline transition-colors"
            >
              Create an account
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
