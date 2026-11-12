import {
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from "react";
import { type AuthUser, parseJwt, isTokenExpired } from "../lib/jwt";
import { endServerSession, getStoredToken, onTokenChange, refreshAccessToken, storeToken } from "../lib/session";
import { AuthContext } from "./AuthContextValue";

export type { AuthUser };

// Refresh this long before the access token expires.
const REFRESH_MARGIN_MS = 60_000;
const MIN_REFRESH_DELAY_MS = 5_000;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(getStoredToken);
  const [user, setUser] = useState<AuthUser | null>(() => {
    const initialToken = getStoredToken();
    return initialToken ? parseJwt(initialToken) : null;
  });

  // An expired token means this browser was logged in earlier, so the refresh cookie
  // may still be good. With no token at all there's no session worth asking about.
  const [isInitializing, setIsInitializing] = useState(() => {
    const initialToken = getStoredToken();
    return !!initialToken && isTokenExpired(parseJwt(initialToken));
  });

  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);

  const openSettingsModal = useCallback(() => {
    setIsSettingsModalOpen(true);
  }, []);

  const closeSettingsModal = useCallback(() => {
    setIsSettingsModalOpen(false);
  }, []);

  const applyToken = useCallback((nextToken: string | null) => {
    const parsed = nextToken ? parseJwt(nextToken) : null;
    setUser(parsed);
    // Pages reload their data when `token` changes, so it must stay the same across a
    // silent refresh or the workspace would blank out every few minutes. It's fine for
    // this value to go stale: apiFetch always sends the newest token from storage.
    setToken((current) => {
      const sameUser = current && nextToken && parseJwt(current)?.email === parsed?.email;
      return sameUser ? current : nextToken;
    });
  }, []);

  const logout = useCallback(() => {
    void endServerSession();
    storeToken(null);
    setIsSettingsModalOpen(false);
  }, []);

  const login = useCallback((newToken: string) => {
    storeToken(newToken);
  }, []);

  useEffect(() => onTokenChange(applyToken), [applyToken]);

  // Restore the session on page load.
  useEffect(() => {
    if (!isInitializing) return;
    refreshAccessToken().then((refreshed) => {
      if (!refreshed) {
        // Couldn't be restored right now. Without a usable token the app has to log in.
        storeToken(null);
      }
      setIsInitializing(false);
    });
  }, [isInitializing]);

  // Synchronize across browser tabs. Another tab logging out only clears this tab's
  // state, because that tab has already ended the session on the server.
  useEffect(() => {
    function handleStorageChange(event: StorageEvent) {
      if (event.key === "token") {
        applyToken(event.newValue);
      }
    }

    window.addEventListener("storage", handleStorageChange);
    return () => window.removeEventListener("storage", handleStorageChange);
  }, [applyToken]);

  // Refresh shortly before the token expires. Browsers slow down timers in background
  // tabs, so this can run late: apiFetch's retry on 401 covers those cases.
  const expiresAt = user?.exp;
  useEffect(() => {
    if (!expiresAt) return;
    const delay = Math.max(expiresAt * 1000 - Date.now() - REFRESH_MARGIN_MS, MIN_REFRESH_DELAY_MS);
    const timer = setTimeout(() => void refreshAccessToken(), delay);
    return () => clearTimeout(timer);
  }, [expiresAt]);

  // Whether a token is still good is the server's call. Checking the clock here would
  // send a laptop waking from sleep to the login page despite a valid refresh cookie.
  const isAuthenticated = !!token && !!user && !isInitializing;

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        isAuthenticated,
        isInitializing,
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
