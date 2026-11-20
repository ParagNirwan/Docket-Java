import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Bot, KeyRound, Webhook } from "lucide-react";
import {
  API_SCOPES,
  WEBHOOK_EVENTS,
  createApiKey,
  createServiceAccount,
  createWebhook,
  deleteWebhook,
  listApiKeys,
  listServiceAccounts,
  listWebhooks,
  revokeApiKey,
  revokeServiceAccount,
  setWebhookActive,
  type ApiKey,
  type ServiceAccount,
  type WebhookEndpoint,
} from "../services/developerService";
import { ErrorText, OneTimeSecret, SettingsSection } from "./SettingsSection";
import { DANGER_BUTTON, INPUT_CLASS, PRIMARY_BUTTON, SECONDARY_BUTTON, formatDate } from "../lib/settingsUi";

type Props = { slug: string; token: string };

function message(err: unknown, fallback: string) {
  return err instanceof Error ? err.message : fallback;
}

// Credentials unused for this long get a nudge to rotate or revoke them.
const STALE_DAYS = 90;

function isStale(credential: { createdAt: string; lastUsedAt: string | null }) {
  const reference = new Date(credential.lastUsedAt ?? credential.createdAt).getTime();
  return Date.now() - reference > STALE_DAYS * 24 * 60 * 60 * 1000;
}

function toggle(list: string[], value: string) {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
}

function CheckboxGroup({
  legend,
  options,
  selected,
  onChange,
}: {
  legend: string;
  options: readonly { code: string; label: string; description: string }[];
  selected: string[];
  onChange: (next: string[]) => void;
}) {
  return (
    <fieldset className="space-y-1.5">
      <legend className="mb-1 text-xs font-medium text-gray-600 dark:text-gray-300">{legend}</legend>
      {options.map((option) => (
        <label key={option.code} className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            className="mt-0.5 h-4 w-4 rounded border-gray-300 text-indigo-600"
            checked={selected.includes(option.code)}
            onChange={() => onChange(toggle(selected, option.code))}
          />
          <span>
            <span className="font-medium text-gray-900 dark:text-white">{option.label}</span>{" "}
            <code className="text-xs text-gray-500">{option.code}</code>
            <span className="block text-xs text-gray-500 dark:text-gray-400">{option.description}</span>
          </span>
        </label>
      ))}
    </fieldset>
  );
}

function useList<T>(loader: () => Promise<T[]>) {
  const [items, setItems] = useState<T[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setItems(await loader());
  }, [loader]);

  useEffect(() => {
    let active = true;
    loader()
      .then((loaded) => active && setItems(loaded))
      .catch((err: unknown) => active && setError(message(err, "Couldn't load")));
    return () => {
      active = false;
    };
  }, [loader]);

  const run = useCallback(async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      await load();
      return true;
    } catch (err) {
      setError(message(err, "Something went wrong"));
      return false;
    } finally {
      setBusy(false);
    }
  }, [load]);

  return { items, error, busy, run };
}

export function DeveloperSettingsPanel({ slug, token }: Props) {
  return (
    <div className="space-y-6">
      <ApiKeysSection slug={slug} token={token} />
      <ServiceAccountsSection slug={slug} token={token} />
      <WebhooksSection slug={slug} token={token} />
    </div>
  );
}

function CredentialTable<T extends ApiKey | ServiceAccount>({
  items,
  identifier,
  busy,
  onRevoke,
}: {
  items: T[];
  identifier: (item: T) => string;
  busy: boolean;
  onRevoke: (item: T) => void;
}) {
  if (items.length === 0) return <p className="text-sm text-gray-500 dark:text-gray-400">None yet.</p>;
  return (
    <table className="w-full text-left text-sm">
      <thead className="text-xs text-gray-500 dark:text-gray-400">
        <tr><th className="py-2">Name</th><th>Scopes</th><th>Created</th><th>Last used</th><th /></tr>
      </thead>
      <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
        {items.map((item) => (
          <tr key={item.id} className={item.revokedAt ? "opacity-50" : undefined}>
            <td className="py-2">
              <span className="font-medium text-gray-900 dark:text-white">{item.name}</span>
              <code className="block text-xs text-gray-500">{identifier(item)}</code>
            </td>
            <td className="text-xs">{item.scopes.length ? item.scopes.join(", ") : "none"}</td>
            <td className="text-xs">{formatDate(item.createdAt)}</td>
            <td className="text-xs">
              {item.lastUsedAt ? formatDate(item.lastUsedAt) : "Never"}
              {!item.revokedAt && isStale(item) && (
                <span className="ml-1 rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium text-amber-800 dark:bg-amber-950/60 dark:text-amber-300"
                  title={`Not used in ${STALE_DAYS} days. Consider rotating or revoking it.`}>
                  Rotate?
                </span>
              )}
            </td>
            <td className="text-right">
              {item.revokedAt ? (
                <span className="text-xs text-gray-500">Revoked</span>
              ) : (
                <button type="button" className={DANGER_BUTTON} disabled={busy} onClick={() => onRevoke(item)}>Revoke</button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function ApiKeysSection({ slug, token }: Props) {
  const loader = useCallback(() => listApiKeys(slug, token), [slug, token]);
  const { items, error, busy, run } = useList(loader);
  const [name, setName] = useState("");
  const [scopes, setScopes] = useState<string[]>(["documents:read"]);
  const [secret, setSecret] = useState<string | null>(null);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    const ok = await run(async () => {
      const created = await createApiKey(slug, name.trim(), scopes, token);
      setSecret(created.value);
    });
    if (ok) setName("");
  }

  return (
    <SettingsSection icon={KeyRound} title="API keys"
      description="For scripts and integrations. Send the key in the X-API-Key header to /api/public/…">
      {secret && <OneTimeSecret title="API key created" value={secret} onDismiss={() => setSecret(null)} />}
      <form onSubmit={handleCreate} className="mb-4 space-y-3">
        <input className={INPUT_CLASS} placeholder="Key name, e.g. Nightly export" value={name}
          onChange={(e) => setName(e.target.value)} aria-label="API key name" />
        <CheckboxGroup legend="Scopes" options={API_SCOPES} selected={scopes} onChange={setScopes} />
        <button type="submit" className={PRIMARY_BUTTON} disabled={busy || !name.trim() || scopes.length === 0}>
          Create API key
        </button>
      </form>
      {items && (
        <CredentialTable items={items} identifier={(k) => `${k.prefix}…`} busy={busy}
          onRevoke={(k) => {
            if (window.confirm(`Revoke "${k.name}"? Anything using it will stop working immediately.`)) {
              void run(() => revokeApiKey(slug, k.id, token));
            }
          }} />
      )}
      <ErrorText message={error} />
    </SettingsSection>
  );
}

function ServiceAccountsSection({ slug, token }: Props) {
  const loader = useCallback(() => listServiceAccounts(slug, token), [slug, token]);
  const { items, error, busy, run } = useList(loader);
  const [name, setName] = useState("");
  const [scopes, setScopes] = useState<string[]>(["documents:read"]);
  const [secret, setSecret] = useState<string | null>(null);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    const ok = await run(async () => {
      const created = await createServiceAccount(slug, name.trim(), scopes, token);
      setSecret(`client_id=${created.serviceAccount.clientId}\nclient_secret=${created.clientSecret}`);
    });
    if (ok) setName("");
  }

  return (
    <SettingsSection icon={Bot} title="Service accounts"
      description="Machine identities for server-to-server integrations, using a client ID and secret.">
      {secret && <OneTimeSecret title="Service account created" value={secret} onDismiss={() => setSecret(null)} />}
      <form onSubmit={handleCreate} className="mb-4 space-y-3">
        <input className={INPUT_CLASS} placeholder="Account name, e.g. ERP sync" value={name}
          onChange={(e) => setName(e.target.value)} aria-label="Service account name" />
        <CheckboxGroup legend="Scopes" options={API_SCOPES} selected={scopes} onChange={setScopes} />
        <button type="submit" className={PRIMARY_BUTTON} disabled={busy || !name.trim() || scopes.length === 0}>
          Create service account
        </button>
      </form>
      {items && (
        <CredentialTable items={items} identifier={(a) => a.clientId} busy={busy}
          onRevoke={(a) => {
            if (window.confirm(`Revoke "${a.name}"? Its tokens stop working immediately.`)) {
              void run(() => revokeServiceAccount(slug, a.id, token));
            }
          }} />
      )}
      <ErrorText message={error} />
    </SettingsSection>
  );
}

function WebhooksSection({ slug, token }: Props) {
  const loader = useCallback(() => listWebhooks(slug, token), [slug, token]);
  const { items, error, busy, run } = useList<WebhookEndpoint>(loader);
  const [url, setUrl] = useState("");
  const [events, setEvents] = useState<string[]>(["document.uploaded"]);
  const [secret, setSecret] = useState<string | null>(null);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    const ok = await run(async () => {
      const created = await createWebhook(slug, url.trim(), events, token);
      setSecret(created.signingSecret);
    });
    if (ok) setUrl("");
  }

  return (
    <SettingsSection icon={Webhook} title="Webhooks"
      description="Docket POSTs a signed JSON event to your HTTPS endpoint. Check the X-Docket-Signature header with the signing secret.">
      {secret && <OneTimeSecret title="Signing secret" value={secret} onDismiss={() => setSecret(null)} />}
      <form onSubmit={handleCreate} className="mb-4 space-y-3">
        <input className={INPUT_CLASS} type="url" placeholder="https://example.com/hooks/docket" value={url}
          onChange={(e) => setUrl(e.target.value)} aria-label="Webhook URL" />
        <CheckboxGroup legend="Events" options={WEBHOOK_EVENTS} selected={events} onChange={setEvents} />
        <button type="submit" className={PRIMARY_BUTTON} disabled={busy || !url.trim() || events.length === 0}>
          Add webhook
        </button>
      </form>
      {items?.length === 0 && <p className="text-sm text-gray-500 dark:text-gray-400">No webhooks yet.</p>}
      <ul className="space-y-2">
        {items?.map((endpoint) => (
          <li key={endpoint.id} className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-gray-200 p-3 dark:border-gray-800">
            <div className="min-w-0">
              <div className="truncate text-sm font-medium text-gray-900 dark:text-white">{endpoint.url}</div>
              <div className="text-xs text-gray-500">{endpoint.events.join(", ")}</div>
            </div>
            <div className="flex items-center gap-2">
              <button type="button" className={SECONDARY_BUTTON} disabled={busy}
                onClick={() => void run(() => setWebhookActive(slug, endpoint.id, !endpoint.active, token))}>
                {endpoint.active ? "Pause" : "Resume"}
              </button>
              <button type="button" className={DANGER_BUTTON} disabled={busy}
                onClick={() => {
                  if (window.confirm(`Delete the webhook to ${endpoint.url}?`)) {
                    void run(() => deleteWebhook(slug, endpoint.id, token));
                  }
                }}>
                Delete
              </button>
            </div>
          </li>
        ))}
      </ul>
      <ErrorText message={error} />
    </SettingsSection>
  );
}
