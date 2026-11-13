import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { KeyRound } from "lucide-react";
import { AuthCard, AuthError } from "../components/AuthCard";
import { retryAfterMessage, storeToken } from "../lib/session";
import { resetPasswordSchema } from "../lib/validation";

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(token ? "" : "This link is invalid or has expired.");
  const navigate = useNavigate();

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");

    const result = resetPasswordSchema.safeParse({ password, confirmPassword });
    if (!result.success) {
      setError(result.error.issues[0].message);
      return;
    }

    setLoading(true);
    try {
      const apiUrl = import.meta.env.VITE_API_URL || "/api";
      const response = await fetch(`${apiUrl}/auth/reset-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, newPassword: password }),
      });

      if (response.ok) {
        // Every session just ended on the server, including any in this browser.
        storeToken(null);
        navigate("/login", { replace: true, state: { message: "Password changed. Please sign in." } });
      } else if (response.status === 429) {
        setError(retryAfterMessage(response));
      } else {
        setError("This link is invalid or has expired. Please request a new one.");
      }
    } catch {
      setError("Network error occurred. Please check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthCard icon={KeyRound} title="Choose a new password" subtitle="This will sign you out on all your devices">
      {error && <AuthError message={error} />}
      {token && (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="reset-password" className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-1.5">New Password</label>
            <input
              id="reset-password"
              type="password"
              required
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3.5 py-2 text-[14px] text-[#1D1C1D] placeholder-[#616061] transition-all focus:border-[#36C5F0] focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#36C5F0]"
            />
            <p className="mt-1 text-[12px] text-[#616061]">At least 8 characters, with a letter and a number.</p>
          </div>
          <div>
            <label htmlFor="reset-confirm" className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-1.5">Confirm Password</label>
            <input
              id="reset-confirm"
              type="password"
              required
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3.5 py-2 text-[14px] text-[#1D1C1D] placeholder-[#616061] transition-all focus:border-[#36C5F0] focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#36C5F0]"
            />
          </div>
          <button type="submit" disabled={loading} className="inline-flex w-full items-center justify-center gap-2 rounded-[8px] bg-[#007A5A] px-4 py-2 text-[14px] font-bold text-white shadow-[0_1px_2px_rgba(0,0,0,0.1)] transition-colors hover:bg-[#00664B] focus:outline-none focus:ring-2 focus:ring-[#36C5F0] focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50">
            {loading ? "Saving..." : "Change password"}
          </button>
        </form>
      )}
      <div className="mt-6 border-t border-[#DDDDDD] pt-4 text-center text-[12px] text-[#616061]">
        <Link to="/forgot-password" className="font-semibold text-[#1164A3] hover:underline transition-colors">Request a new link</Link>
      </div>
    </AuthCard>
  );
}
