import { useState } from "react";
import { Link } from "react-router-dom";
import { KeyRound } from "lucide-react";
import { AuthCard, AuthError, AuthNotice } from "../components/AuthCard";
import { retryAfterMessage } from "../lib/session";

export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const apiUrl = import.meta.env.VITE_API_URL || "/api";
      const response = await fetch(`${apiUrl}/auth/forgot-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });

      if (response.ok) {
        setSent(true);
      } else {
        setError(response.status === 429 ? retryAfterMessage(response) : "Something went wrong. Please try again.");
      }
    } catch {
      setError("Network error occurred. Please check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthCard icon={KeyRound} title="Reset your password" subtitle="We'll email you a link to choose a new one">
      {error && <AuthError message={error} />}
      {sent ? (
        <AuthNotice message="If an account exists for that address, a reset link is on its way. It expires in 30 minutes." />
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="forgot-email" className="block text-[11px] font-bold uppercase tracking-wider text-[#616061] mb-1.5">Email Address</label>
            <input
              id="forgot-email"
              type="email"
              required
              autoComplete="email"
              placeholder="you@company.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] px-3.5 py-2 text-[14px] text-[#1D1C1D] placeholder-[#616061] transition-all focus:border-[#36C5F0] focus:bg-white focus:outline-none focus:ring-1 focus:ring-[#36C5F0]"
            />
          </div>
          <button type="submit" disabled={loading} className="inline-flex w-full items-center justify-center gap-2 rounded-[8px] bg-[#007A5A] px-4 py-2 text-[14px] font-bold text-white shadow-[0_1px_2px_rgba(0,0,0,0.1)] transition-colors hover:bg-[#00664B] focus:outline-none focus:ring-2 focus:ring-[#36C5F0] focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50">
            {loading ? "Sending..." : "Send reset link"}
          </button>
        </form>
      )}
      <div className="mt-6 border-t border-[#DDDDDD] pt-4 text-center text-[12px] text-[#616061]">
        <Link to="/login" className="font-semibold text-[#1164A3] hover:underline transition-colors">Back to sign in</Link>
      </div>
    </AuthCard>
  );
}
