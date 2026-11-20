import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { KeyRound } from "lucide-react";
import { AuthCard, AuthError } from "../components/AuthCard";
import { useAuth } from "../context/useAuth";
import { completeSso } from "../services/ssoService";

// Where Google or Microsoft sends the browser back after sign-in.
export function SsoCallbackPage() {
  const [searchParams] = useSearchParams();
  const { login } = useAuth();
  const navigate = useNavigate();

  const code = searchParams.get("code");
  const providerError = searchParams.get("error_description") ?? searchParams.get("error");
  const initialError = providerError ?? (code ? "" : "Sign-in was cancelled.");
  const [error, setError] = useState(initialError);
  // React StrictMode runs effects twice in development; an auth code can only be used once.
  const started = useRef(false);

  useEffect(() => {
    if (started.current || !code || providerError) return;
    started.current = true;

    completeSso(code, searchParams.get("state"))
      .then((token) => {
        login(token);
        navigate("/tenants", { replace: true });
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Sign-in failed"));
  }, [code, providerError, searchParams, login, navigate]);

  return (
    <AuthCard icon={KeyRound} title="Signing you in" subtitle="Finishing single sign-on">
      {error ? (
        <>
          <AuthError message={error} />
          <Link to="/login" className="mt-4 block text-center text-sm font-semibold text-indigo-600 hover:underline">
            Back to sign in
          </Link>
        </>
      ) : (
        <p className="text-center text-sm text-gray-500">One moment…</p>
      )}
    </AuthCard>
  );
}
