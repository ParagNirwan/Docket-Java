import { Navigate } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuth } from "../context/AuthContext";

type GuestRouteProps = { children: ReactNode };

export function GuestRoute({ children }: GuestRouteProps) {
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <Navigate to="/tenants" replace />;
  }

  return children;
}
