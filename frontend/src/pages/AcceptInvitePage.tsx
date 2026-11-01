import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { MailPlus } from "lucide-react";
import { AuthCard, AuthError } from "../components/AuthCard";
import { useAuth } from "../context/AuthContext";
import { ROLE_LABELS } from "../lib/permissions";
import { refreshAccessToken } from "../lib/session";
import { acceptInvitation, previewInvitation, type InvitationPreview } from "../services/memberService";

const BUTTON_CLASS = "inline-flex w-full items-center justify-center rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-xs transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50";

// Opens from the link in an invite email, whether or not someone is signed in.
export function AcceptInvitePage() {
  const [searchParams] = useSearchParams();
  const inviteToken = searchParams.get("token");
  const { user, token, isAuthenticated, isInitializing, logout } = useAuth();
  const navigate = useNavigate();

  const [preview, setPreview] = useState<InvitationPreview | null>(null);
  const [error, setError] = useState(inviteToken ? "" : "This invitation is invalid or has expired. Ask for a new one.");
  const [accepting, setAccepting] = useState(false);

  useEffect(() => {
    if (!inviteToken) return;
    let cancelled = false;
    previewInvitation(inviteToken)
      .then((loaded) => {
        if (!cancelled) setPreview(loaded);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Could not open this invitation");
      });
    return () => {
      cancelled = true;
    };
  }, [inviteToken]);

  async function handleAccept() {
    if (!inviteToken || !token) return;
    try {
      setAccepting(true);
      setError("");
      const accepted = await acceptInvitation(inviteToken, token);
      // Accepting proves the email address, so get a token that knows that.
      if (user?.emailVerified === false) {
        await refreshAccessToken();
      }
      navigate(`/workspace/${encodeURIComponent(accepted.tenantSlug)}`, {
        replace: true,
        state: { tenantName: accepted.tenantName },
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not accept the invitation");
      setAccepting(false);
    }
  }

  const here = `/invite?token=${encodeURIComponent(inviteToken ?? "")}`;
  const isForThisUser = preview && user?.email.toLowerCase() === preview.email.toLowerCase();

  return (
    <AuthCard icon={MailPlus} title="You're invited" subtitle="Join an organization on Docket">
      {error && <AuthError message={error} />}

      {preview && !isInitializing && (
        <>
          <p className="mb-5 text-center text-sm text-gray-600 dark:text-gray-300">
            {preview.inviterName ? `${preview.inviterName} invited you` : "You've been invited"} to join{" "}
            <span className="font-semibold text-gray-900 dark:text-white">{preview.tenantName}</span> as{" "}
            <span className="font-semibold text-gray-900 dark:text-white">{ROLE_LABELS[preview.role]}</span>.
          </p>

          {isAuthenticated && isForThisUser && (
            <button type="button" onClick={handleAccept} disabled={accepting} className={BUTTON_CLASS}>
              {accepting ? "Joining..." : `Join ${preview.tenantName}`}
            </button>
          )}

          {isAuthenticated && !isForThisUser && (
            <>
              <AuthError message={`This invitation is for ${preview.email}, but you're signed in as ${user?.email}.`} />
              <button type="button" onClick={logout} className={BUTTON_CLASS}>
                Sign out to continue
              </button>
            </>
          )}

          {!isAuthenticated && preview.hasAccount && (
            <Link to="/login" state={{ redirectTo: here, email: preview.email }} className={BUTTON_CLASS}>
              Sign in to accept
            </Link>
          )}

          {!isAuthenticated && !preview.hasAccount && (
            <Link to={`/register?invite=${encodeURIComponent(inviteToken ?? "")}`} className={BUTTON_CLASS}>
              Create an account to accept
            </Link>
          )}
        </>
      )}

      {!preview && !error && <p role="status" className="text-center text-sm text-gray-500 dark:text-gray-400">Opening your invitation...</p>}
    </AuthCard>
  );
}
