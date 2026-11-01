// Node 25 defines its own global localStorage, which has no working methods unless
// Node is given a storage file, and it hides the one jsdom provides. Browsers are fine.
if (typeof globalThis.localStorage?.getItem !== "function") {
  const items = new Map<string, string>();
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: {
      getItem: (key: string) => items.get(key) ?? null,
      setItem: (key: string, value: string) => void items.set(key, String(value)),
      removeItem: (key: string) => void items.delete(key),
      clear: () => items.clear(),
    },
  });
}
