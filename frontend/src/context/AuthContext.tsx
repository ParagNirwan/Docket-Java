import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from "react";
import { type AuthUser, parseJwt, isTokenExpired } from "../lib/jwt";

export type { AuthUser };

export interface AuthContextType {
  token: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  isSettingsModalOpen: boolean;
  openSettingsModal: () => void;
  closeSettingsModal: () => void;
  login: (newToken: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => {
    return localStorage.getItem("token");
  });

  const [user, setUser] = useState<AuthUser | null>(() => {
    const initialToken = localStorage.getItem("token");
    if (!initialToken) return null;
    const parsed = parseJwt(initialToken);
    if (isTokenExpired(parsed)) {
      localStorage.removeItem("token");
      return null;
    }
    return parsed;
  });

  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);

  const openSettingsModal = useCallback(() => {
    setIsSettingsModalOpen(true);
  }, []);

  const closeSettingsModal = useCallback(() => {
    setIsSettingsModalOpen(false);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem("token");
    setToken(null);
    setUser(null);
    setIsSettingsModalOpen(false);
  }, []);

  const login = useCallback((newToken: string) => {
    localStorage.setItem("token", newToken);
    setToken(newToken);
    const parsed = parseJwt(newToken);
    setUser(parsed);
  }, []);

  // Synchronize across browser tabs and handle token expiration
  useEffect(() => {
    function handleStorageChange(event: StorageEvent) {
      if (event.key === "token") {
        const nextToken = event.newValue;
        if (nextToken) {
          const parsed = parseJwt(nextToken);
          if (isTokenExpired(parsed)) {
            logout();
          } else {
            setToken(nextToken);
            setUser(parsed);
          }
        } else {
          setToken(null);
          setUser(null);
        }
      }
    }

    window.addEventListener("storage", handleStorageChange);
    return () => window.removeEventListener("storage", handleStorageChange);
  }, [logout]);

  const isAuthenticated = !!token && !isTokenExpired(user);

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        isAuthenticated,
        isSettingsModalOpen,
        openSettingsModal,
        closeSettingsModal,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

