import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { UserPlus } from "lucide-react";
import { AuthCard, AuthError } from "../components/AuthCard";
import { useAuth } from "../context/useAuth";
import { ROLE_LABELS } from "../lib/permissions";
import { joinWithInviteLink, previewInviteLink, type InviteLinkPreview } from "../services/enterpriseService";

const BUTTON_CLASS =
  "inline-flex w-full items-center justify-center rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-xs transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50";

// Opens from a reusable invite link (/join/<token>). Anyone with the link can join, so
// they only need to be signed in; there's no email to match.
export function JoinInviteLinkPage() {
  const { token: linkToken = "" } = useParams();
  const { token, isAuthenticated, isInitializing } = useAuth();
  const navigate = useNavigate();
  const [preview, setPreview] = useState<InviteLinkPreview | null>(null);
  const [error, setError] = useState("");
  const [joining, setJoining] = useState(false);

  useEffect(() => {
    let cancelled = false;
    previewInviteLink(linkToken)
      .then((loaded) => {
        if (!cancelled) setPreview(loaded);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "This invite link is invalid");
      });
    return () => {
      cancelled = true;
    };
  }, [linkToken]);

  async function handleJoin() {
    if (!token) return;
    setJoining(true);
    setError("");
    try {
      const joined = await joinWithInviteLink(linkToken, token);
      navigate(`/workspace/${encodeURIComponent(joined.tenantSlug)}`, {
        replace: true,
        state: { tenantName: joined.tenantName },
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't join");
      setJoining(false);
    }
  }

  const here = `/join/${encodeURIComponent(linkToken)}`;

  return (
    <AuthCard icon={UserPlus} title="Join an organization" subtitle="You've been sent an invite link">
      {error && <AuthError message={error} />}
      {preview && !isInitializing && (
        <>
          <p className="mb-5 text-center text-sm text-gray-600 dark:text-gray-300">
            Join <span className="font-semibold text-gray-900 dark:text-white">{preview.tenantName}</span> as{" "}
            <span className="font-semibold text-gray-900 dark:text-white">{ROLE_LABELS[preview.role]}</span>.
          </p>
          {isAuthenticated ? (
            <button type="button" className={BUTTON_CLASS} onClick={() => void handleJoin()} disabled={joining}>
              {joining ? "Joining..." : `Join ${preview.tenantName}`}
            </button>
          ) : (
            <div className="space-y-2">
              <Link to="/login" state={{ redirectTo: here }} className={BUTTON_CLASS}>
                Sign in to join
              </Link>
              <Link to="/register" state={{ redirectTo: here }}
                className="block text-center text-sm font-semibold text-indigo-600 hover:underline">
                Create an account
              </Link>
            </div>
          )}
        </>
      )}
    </AuthCard>
  );
}
