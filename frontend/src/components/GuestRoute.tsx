import { Navigate, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuth } from "../context/AuthContext";

type GuestRouteProps = { children: ReactNode };

export function GuestRoute({ children }: GuestRouteProps) {
  const { isAuthenticated, isInitializing } = useAuth();
  // Set by pages sending someone to sign in, such as an invite link. It lives in router
  // state rather than the URL, so an outside link can't redirect someone elsewhere.
  const redirectTo = (useLocation().state as { redirectTo?: string } | null)?.redirectTo;

  if (isInitializing) {
    return null;
  }

  if (isAuthenticated) {
    return <Navigate to={redirectTo || "/tenants"} replace />;
  }

  return children;
}
