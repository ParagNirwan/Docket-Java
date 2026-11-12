import { useEffect, useState } from "react";
import { MailWarning } from "lucide-react";
import { useAuth } from "../context/useAuth";
import { apiFetch } from "../lib/session";
import { notify } from "../lib/notifications";
import {
  EMAIL_VERIFICATION_SETTINGS_CHANGED_EVENT,
  isEmailVerificationEnabled,
} from "../services/emailVerificationService";

// Reminds logged-in users who haven't verified their address yet.
export function EmailVerificationBanner() {
  const { isAuthenticated, user, token } = useAuth();
  const [sending, setSending] = useState(false);
  const [check, setCheck] = useState<{ token: string; status: "enabled" | "disabled" | "error" } | null>(null);

  useEffect(() => {
    let active = true;
    if (!isAuthenticated || !token || user?.emailVerified !== false) return;
    const refresh = () => {
      void isEmailVerificationEnabled(token)
        .then((isEnabled) => {
          if (active) setCheck({ token, status: isEnabled ? "enabled" : "disabled" });
        })
        .catch(() => {
          if (active) setCheck({ token, status: "error" });
        });
    };
    refresh();
    window.addEventListener(EMAIL_VERIFICATION_SETTINGS_CHANGED_EVENT, refresh);
    return () => {
      active = false;
      window.removeEventListener(EMAIL_VERIFICATION_SETTINGS_CHANGED_EVENT, refresh);
    };
  }, [isAuthenticated, token, user?.emailVerified]);

  if (!isAuthenticated || user?.emailVerified !== false || !token) return null;
  const status = check?.token === token ? check.status : "disabled";
  if (status === "disabled") return null;
  if (status === "error") {
    return (
      <div role="status" className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-center text-xs text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
        Could not load email verification settings.
      </div>
    );
  }

  async function resend() {
    setSending(true);
    try {
      const apiUrl = import.meta.env.VITE_API_URL || "/api";
      const response = await apiFetch(`${apiUrl}/auth/resend-verification`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
      if (response.ok) {
        notify.success("Verification email sent.");
      } else if (response.status === 429) {
        notify.error("Too many emails requested. Please try again later.");
      } else {
        notify.error("Could not send the email. Please try again.");
      }
    } catch {
      notify.error("Could not send the email. Please try again.");
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="flex flex-wrap items-center justify-center gap-2 border-b border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
      <MailWarning className="h-4 w-4 shrink-0" />
      <span>Please verify your email address.</span>
      <button type="button" onClick={resend} disabled={sending} className="font-semibold underline disabled:opacity-50">
        {sending ? "Sending..." : "Resend email"}
      </button>
    </div>
  );
}
