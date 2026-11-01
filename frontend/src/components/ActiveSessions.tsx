import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { notify } from "../lib/notifications";
import { type ActiveSession, listSessions, revokeAllSessions, revokeSession } from "../services/sessionService";

// A rough, readable name for a browser. Only ever used as a label.
function describeDevice(userAgent: string | null): string {
  if (!userAgent) return "Unknown device";
  const browser = /Edg\//.test(userAgent) ? "Edge"
    : /Chrome\//.test(userAgent) ? "Chrome"
    : /Firefox\//.test(userAgent) ? "Firefox"
    : /Safari\//.test(userAgent) ? "Safari"
    : "Browser";
  const system = /Windows/.test(userAgent) ? "Windows"
    : /Android/.test(userAgent) ? "Android"
    : /iPhone|iPad/.test(userAgent) ? "iOS"
    : /Mac OS/.test(userAgent) ? "macOS"
    : /Linux/.test(userAgent) ? "Linux"
    : null;
  return system ? `${browser} on ${system}` : browser;
}

export function ActiveSessions() {
  const { token, logout } = useAuth();
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<ActiveSession[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    listSessions(token)
      .then((loaded) => {
        if (!cancelled) setSessions(loaded);
      })
      .catch(() => {
        if (!cancelled) setError("Could not load your sessions.");
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  async function handleRevoke(session: ActiveSession) {
    if (!token) return;
    try {
      await revokeSession(session.id, token);
      if (session.current) {
        logout();
        navigate("/login");
        return;
      }
      setSessions((current) => current?.filter((s) => s.id !== session.id) ?? null);
    } catch {
      notify.error("Could not end that session.");
    }
  }

  async function handleRevokeAll() {
    if (!token) return;
    try {
      await revokeAllSessions(token);
      logout();
      navigate("/login");
    } catch {
      notify.error("Could not end your sessions.");
    }
  }

  if (error) {
    return <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>;
  }
  if (!sessions) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading sessions...</p>;
  }

  return (
    <div className="space-y-3">
      <ul className="space-y-2">
        {sessions.map((session) => (
          <li
            key={session.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-100 bg-gray-50/50 p-4 dark:border-gray-800 dark:bg-gray-800/30"
          >
            <div className="min-w-0">
              <div className="flex items-center gap-2 text-sm font-semibold text-gray-900 dark:text-white">
                {describeDevice(session.userAgent)}
                {session.current && (
                  <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-medium text-emerald-700 dark:bg-emerald-950/80 dark:text-emerald-300">
                    This device
                  </span>
                )}
              </div>
              <div className="text-xs text-gray-500 dark:text-gray-400">
                {session.ip ?? "Unknown address"} · Last active{" "}
                {new Date(session.lastActiveAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}
              </div>
            </div>
            <button
              type="button"
              onClick={() => handleRevoke(session)}
              className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800"
            >
              {session.current ? "Sign out" : "End session"}
            </button>
          </li>
        ))}
      </ul>
      {sessions.length > 1 && (
        <button
          type="button"
          onClick={handleRevokeAll}
          className="rounded-lg border border-red-200 px-3 py-1.5 text-xs font-medium text-red-600 transition-colors hover:bg-red-50 dark:border-red-900/60 dark:text-red-400 dark:hover:bg-red-950/40"
        >
          Sign out everywhere
        </button>
      )}
    </div>
  );
}
