import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "./ThemeProvider";
import { useTheme } from "./ThemeContext";

function ThemeProbe() {
  const { preference, resolvedTheme, cycleTheme } = useTheme();
  return (
    <div>
      <span data-testid="theme">{preference}:{resolvedTheme}</span>
      <button onClick={cycleTheme}>Change theme</button>
    </div>
  );
}

describe("ThemeProvider", () => {
  let changeListener: ((event: MediaQueryListEvent) => void) | undefined;
  let matches = true;

  beforeEach(() => {
    localStorage.clear();
    matches = true;
    changeListener = undefined;
    vi.stubGlobal("matchMedia", vi.fn(() => ({
      matches,
      addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => {
        changeListener = listener;
      },
      removeEventListener: vi.fn(),
    })));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("uses and tracks the operating system theme by default", () => {
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );

    expect(screen.getByTestId("theme").textContent).toBe("system:dark");
    expect(document.documentElement.dataset.theme).toBe("dark");

    matches = false;
    act(() => changeListener?.({ matches: false } as MediaQueryListEvent));

    expect(screen.getByTestId("theme").textContent).toBe("system:light");
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(localStorage.getItem("docket-theme")).toBeNull();
  });

  it("cycles through explicit themes and returns to system preference", () => {
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Change theme" }));
    expect(screen.getByTestId("theme").textContent).toBe("light:light");
    expect(localStorage.getItem("docket-theme")).toBe("light");

    fireEvent.click(screen.getByRole("button", { name: "Change theme" }));
    expect(screen.getByTestId("theme").textContent).toBe("dark:dark");
    expect(localStorage.getItem("docket-theme")).toBe("dark");

    fireEvent.click(screen.getByRole("button", { name: "Change theme" }));
    expect(screen.getByTestId("theme").textContent).toBe("system:dark");
    expect(localStorage.getItem("docket-theme")).toBeNull();
  });
});
