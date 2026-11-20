import { useEffect, useState } from "react";
import { getSsoProviders, SSO_PROVIDER_LABELS, startSso, type SsoProvider } from "../services/ssoService";

// Shows nothing unless the server has an SSO provider configured.
export function SsoButtons({ onError }: { onError: (message: string) => void }) {
  const [providers, setProviders] = useState<SsoProvider[]>([]);
  const [starting, setStarting] = useState<SsoProvider | null>(null);

  useEffect(() => {
    let active = true;
    void getSsoProviders().then((loaded) => {
      if (active) setProviders(loaded);
    });
    return () => {
      active = false;
    };
  }, []);

  if (providers.length === 0) return null;

  async function handleStart(provider: SsoProvider) {
    setStarting(provider);
    try {
      await startSso(provider);
    } catch (err) {
      onError(err instanceof Error ? err.message : "Couldn't start single sign-on");
      setStarting(null);
    }
  }

  return (
    <div className="mt-5">
      <div className="mb-3 flex items-center gap-3 text-[12px] text-[#616061]">
        <span className="h-px flex-1 bg-[#DDDDDD]" />
        or
        <span className="h-px flex-1 bg-[#DDDDDD]" />
      </div>
      <div className="space-y-2">
        {providers.map((provider) => (
          <button
            key={provider}
            type="button"
            disabled={starting !== null}
            onClick={() => void handleStart(provider)}
            className="inline-flex w-full items-center justify-center gap-2 rounded-[8px] border border-[#DDDDDD] bg-white px-4 py-2 text-[14px] font-semibold text-[#1D1C1D] transition-colors hover:bg-[#F8F8F8] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {starting === provider ? "Redirecting…" : `Continue with ${SSO_PROVIDER_LABELS[provider]}`}
          </button>
        ))}
      </div>
    </div>
  );
}
