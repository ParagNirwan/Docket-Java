import {
  useEffect,
  useLayoutEffect,
  useState,
  type PropsWithChildren,
} from "react";
import {
  ThemeContext,
  type ResolvedTheme,
  type ThemePreference,
} from "./ThemeContext";

const STORAGE_KEY = "docket-theme";

function readPreference(): ThemePreference {
  const saved = localStorage.getItem(STORAGE_KEY);
  return saved === "light" || saved === "dark" ? saved : "system";
}

function readSystemTheme(): ResolvedTheme {
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function ThemeProvider({ children }: PropsWithChildren) {
  const [preference, setPreference] = useState<ThemePreference>(readPreference);
  const [systemTheme, setSystemTheme] = useState<ResolvedTheme>(readSystemTheme);
  const resolvedTheme = preference === "system" ? systemTheme : preference;

  useLayoutEffect(() => {
    document.documentElement.dataset.theme = resolvedTheme;
    document.documentElement.style.colorScheme = resolvedTheme;
  }, [resolvedTheme]);

  useEffect(() => {
    const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
    const handleChange = (event: MediaQueryListEvent) => {
      setSystemTheme(event.matches ? "dark" : "light");
    };

    mediaQuery.addEventListener("change", handleChange);
    return () => mediaQuery.removeEventListener("change", handleChange);
  }, []);

  function cycleTheme() {
    const nextPreference =
      preference === "system"
        ? resolvedTheme === "light" ? "dark" : "light"
        : preference === "light" ? "dark" : "system";

    if (nextPreference === "system") {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, nextPreference);
    }

    setPreference(nextPreference);
  }

  return (
    <ThemeContext.Provider value={{ preference, resolvedTheme, cycleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}
