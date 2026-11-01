import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { MailCheck } from "lucide-react";
import { AuthCard, AuthError, AuthNotice } from "../components/AuthCard";
import { getStoredToken, refreshAccessToken } from "../lib/session";

type VerifyState = "verifying" | "verified" | "failed";

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const [state, setState] = useState<VerifyState>(token ? "verifying" : "failed");
  // The token is single-use, and StrictMode runs effects twice in development.
  const submitted = useRef(false);

  useEffect(() => {
    if (!token || submitted.current) return;
    submitted.current = true;

    const apiUrl = import.meta.env.VITE_API_URL || "/api";
    fetch(`${apiUrl}/auth/verify-email`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token }),
    })
      .then(async (response) => {
        if (!response.ok) {
          setState("failed");
          return;
        }
        // If they're logged in here, get a token that knows they're verified now.
        if (getStoredToken()) {
          await refreshAccessToken();
        }
        setState("verified");
      })
      .catch(() => setState("failed"));
  }, [token]);

  return (
    <AuthCard icon={MailCheck} title="Verify your email" subtitle="Confirming your address">
      {state === "verifying" && <p className="text-center text-sm text-gray-500 dark:text-gray-400">Verifying...</p>}
      {state === "verified" && <AuthNotice message="Your email address is verified." />}
      {state === "failed" && (
        <AuthError message="This link is invalid or has expired. Sign in and use the banner at the top of the page to get a new one." />
      )}
      <div className="mt-6 border-t border-gray-100 pt-5 text-center text-xs text-gray-500 dark:border-gray-800 dark:text-gray-400">
        <Link to="/tenants" className="font-semibold text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300 transition-colors">Continue to Docket</Link>
      </div>
    </AuthCard>
  );
}
