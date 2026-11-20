import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Globe, Link2, Loader2, ShieldCheck } from "lucide-react";
import {
  addDomain,
  createInviteLink,
  getInviteLinkSettings,
  listDomains,
  listInviteLinks,
  removeDomain,
  revokeInviteLink,
  setInviteLinksEnabled,
  txtRecordName,
  txtRecordValue,
  updateDomain,
  verifyDomain,
  type InviteLink,
  type TenantDomain,
} from "../services/enterpriseService";
import { getSsoProviders, SSO_PROVIDER_LABELS, type SsoProvider } from "../services/ssoService";
import { ROLE_LABELS, type MemberRole } from "../lib/permissions";
import { CopyButton, ErrorText, OneTimeSecret, SettingsSection } from "./SettingsSection";
import { DANGER_BUTTON, INPUT_CLASS, PRIMARY_BUTTON, SECONDARY_BUTTON, formatDate } from "../lib/settingsUi";

type Props = { slug: string; token: string };

// Roles an invite link may grant. Owner and Admin are deliberately left out: a link can be
// forwarded, so it shouldn't hand out control of the organization.
const LINK_ROLES: MemberRole[] = ["MEMBER", "MANAGER", "GUEST"];

function message(err: unknown, fallback: string) {
  return err instanceof Error ? err.message : fallback;
}

export function AccessSettingsPanel({ slug, token }: Props) {
  return (
    <div className="space-y-6">
      <DomainsSection slug={slug} token={token} />
      <InviteLinksSection slug={slug} token={token} />
    </div>
  );
}

function DomainsSection({ slug, token }: Props) {
  const [domains, setDomains] = useState<TenantDomain[] | null>(null);
  const [providers, setProviders] = useState<SsoProvider[]>([]);
  const [newDomain, setNewDomain] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setDomains(await listDomains(slug, token));
    } catch (err) {
      setError(message(err, "Couldn't load domains"));
    }
  }, [slug, token]);

  useEffect(() => {
    let active = true;
    listDomains(slug, token)
      .then((loaded) => active && setDomains(loaded))
      .catch((err: unknown) => active && setError(message(err, "Couldn't load domains")));
    void getSsoProviders().then((loaded) => active && setProviders(loaded));
    return () => {
      active = false;
    };
  }, [slug, token]);

  async function run(key: string, action: () => Promise<unknown>) {
    setBusy(key);
    setError(null);
    try {
      await action();
      await load();
    } catch (err) {
      setError(message(err, "Something went wrong"));
    } finally {
      setBusy(null);
    }
  }

  async function handleAdd(event: FormEvent) {
    event.preventDefault();
    const domain = newDomain.trim();
    if (!domain) return;
    await run("add", async () => {
      await addDomain(slug, domain, token);
      setNewDomain("");
    });
  }

  const ssoAvailable = providers.length > 0;

  return (
    <SettingsSection
      icon={Globe}
      title="Verified domains & single sign-on"
      description="Prove you own your email domain with a DNS record. Verified domains can let colleagues join automatically and can require Google or Microsoft sign-in."
    >
      <form onSubmit={handleAdd} className="mb-4 flex gap-2">
        <label htmlFor="new-domain" className="sr-only">Domain</label>
        <input
          id="new-domain"
          className={INPUT_CLASS}
          placeholder="example.com"
          value={newDomain}
          onChange={(e) => setNewDomain(e.target.value)}
        />
        <button type="submit" className={PRIMARY_BUTTON} disabled={busy === "add" || !newDomain.trim()}>
          Add domain
        </button>
      </form>

      {domains === null && !error && (
        <div className="flex items-center gap-2 text-sm text-gray-500"><Loader2 className="h-4 w-4 animate-spin" />Loading…</div>
      )}
      {domains?.length === 0 && <p className="text-sm text-gray-500 dark:text-gray-400">No domains yet.</p>}

      <ul className="space-y-3">
        {domains?.map((d) => (
          <li key={d.domain} className="rounded-xl border border-gray-200 p-4 dark:border-gray-800">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-gray-900 dark:text-white">{d.domain}</span>
                {d.verifiedAt ? (
                  <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-950/70 dark:text-emerald-300">
                    <ShieldCheck className="h-3 w-3" /> Verified
                  </span>
                ) : (
                  <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-950/60 dark:text-amber-300">
                    Pending
                  </span>
                )}
              </div>
              <button
                type="button"
                className={DANGER_BUTTON}
                disabled={busy !== null}
                onClick={() => {
                  if (window.confirm(`Remove ${d.domain}? Automatic joining and SSO enforcement for it will stop.`)) {
                    void run(`rm:${d.domain}`, () => removeDomain(slug, d.domain, token));
                  }
                }}
              >
                Remove
              </button>
            </div>

            {!d.verifiedAt && (
              <div className="mt-3 space-y-2 text-xs text-gray-600 dark:text-gray-300">
                <p>Add this TXT record at your DNS provider, then select Verify. DNS changes can take a few minutes.</p>
                <dl className="grid gap-2 sm:grid-cols-[6rem_1fr_auto] sm:items-center">
                  <dt className="font-medium">Name</dt>
                  <dd><code className="break-all font-mono">{txtRecordName(d.domain)}</code></dd>
                  <dd><CopyButton value={txtRecordName(d.domain)} /></dd>
                  <dt className="font-medium">Value</dt>
                  <dd><code className="break-all font-mono">{txtRecordValue(d.verificationToken)}</code></dd>
                  <dd><CopyButton value={txtRecordValue(d.verificationToken)} /></dd>
                </dl>
                <button
                  type="button"
                  className={PRIMARY_BUTTON}
                  disabled={busy !== null}
                  onClick={() => void run(`verify:${d.domain}`, () => verifyDomain(slug, d.domain, token))}
                >
                  {busy === `verify:${d.domain}` ? "Checking DNS…" : "Verify"}
                </button>
              </div>
            )}

            {d.verifiedAt && (
              <div className="mt-3 space-y-2">
                <label className="flex items-start gap-3 text-sm">
                  <input
                    type="checkbox"
                    className="mt-0.5 h-4 w-4 rounded border-gray-300 text-indigo-600"
                    checked={d.joinMode === "AUTO"}
                    disabled={busy !== null || d.ssoRequired}
                    onChange={(e) =>
                      void run(`join:${d.domain}`, () =>
                        updateDomain(slug, d.domain, { joinMode: e.target.checked ? "AUTO" : "REQUEST" }, token))
                    }
                  />
                  <span>
                    <span className="block font-medium text-gray-900 dark:text-white">Let colleagues join automatically</span>
                    <span className="block text-xs text-gray-500 dark:text-gray-400">
                      Anyone who verifies an @{d.domain} email address joins as a Member.
                    </span>
                  </span>
                </label>
                <label className="flex items-start gap-3 text-sm">
                  <input
                    type="checkbox"
                    className="mt-0.5 h-4 w-4 rounded border-gray-300 text-indigo-600"
                    checked={d.ssoRequired}
                    disabled={busy !== null || (!ssoAvailable && !d.ssoRequired)}
                    onChange={(e) => {
                      const next = e.target.checked;
                      if (next && !window.confirm(
                        `Require single sign-on for @${d.domain}? Password sign-in will stop working for those addresses, including yours if it's on this domain.`,
                      )) return;
                      void run(`sso:${d.domain}`, () => updateDomain(slug, d.domain, { ssoRequired: next }, token));
                    }}
                  />
                  <span>
                    <span className="block font-medium text-gray-900 dark:text-white">Require single sign-on</span>
                    <span className="block text-xs text-gray-500 dark:text-gray-400">
                      {ssoAvailable
                        ? `@${d.domain} users must sign in with ${providers.map((p) => SSO_PROVIDER_LABELS[p]).join(" or ")}.`
                        : "No SSO provider is configured on this server. Ask the service administrator to set one up."}
                    </span>
                  </span>
                </label>
              </div>
            )}
          </li>
        ))}
      </ul>
      <ErrorText message={error} />
    </SettingsSection>
  );
}

function InviteLinksSection({ slug, token }: Props) {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [links, setLinks] = useState<InviteLink[] | null>(null);
  const [role, setRole] = useState<MemberRole>("MEMBER");
  const [maxUses, setMaxUses] = useState("");
  const [expiresInDays, setExpiresInDays] = useState("7");
  const [created, setCreated] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchAll = useCallback(
    () => Promise.all([getInviteLinkSettings(slug, token), listInviteLinks(slug, token)]),
    [slug, token],
  );

  const load = useCallback(async () => {
    const [settings, list] = await fetchAll();
    setEnabled(settings.enabled);
    setLinks(list);
  }, [fetchAll]);

  useEffect(() => {
    let active = true;
    fetchAll()
      .then(([settings, list]) => {
        if (!active) return;
        setEnabled(settings.enabled);
        setLinks(list);
      })
      .catch((err: unknown) => active && setError(message(err, "Couldn't load invite links")));
    return () => {
      active = false;
    };
  }, [fetchAll]);

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await load();
    } catch (err) {
      setError(message(err, "Something went wrong"));
    } finally {
      setBusy(false);
    }
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    await run(async () => {
      const link = await createInviteLink(slug, {
        role,
        maxUses: maxUses ? Number(maxUses) : undefined,
        expiresInDays: expiresInDays ? Number(expiresInDays) : undefined,
      }, token);
      setCreated(link.url);
    });
  }

  return (
    <SettingsSection
      icon={Link2}
      title="Invite links"
      description="Reusable links anyone can open to join. Limit how long they last and how many people can use them."
      action={
        enabled !== null && (
          <label className="flex items-center gap-2 text-xs font-medium text-gray-700 dark:text-gray-300">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border-gray-300 text-indigo-600"
              checked={enabled}
              disabled={busy}
              onChange={(e) => void run(() => setInviteLinksEnabled(slug, e.target.checked, token))}
            />
            Enabled
          </label>
        )
      }
    >
      {created && <OneTimeSecret title="Invite link created" value={created} onDismiss={() => setCreated(null)} />}

      {enabled === false && (
        <p className="mb-4 text-xs text-gray-500 dark:text-gray-400">
          Invite links are off. Existing links stop working until you switch them back on.
        </p>
      )}

      {enabled && (
        <form onSubmit={handleCreate} className="mb-4 grid gap-2 sm:grid-cols-[1fr_1fr_1fr_auto] sm:items-end">
          <label className="text-xs font-medium text-gray-600 dark:text-gray-300">
            Role
            <select className={`${INPUT_CLASS} mt-1`} value={role} onChange={(e) => setRole(e.target.value as MemberRole)}>
              {LINK_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
            </select>
          </label>
          <label className="text-xs font-medium text-gray-600 dark:text-gray-300">
            Max uses
            <input className={`${INPUT_CLASS} mt-1`} type="number" min={1} max={10000} placeholder="Unlimited"
              value={maxUses} onChange={(e) => setMaxUses(e.target.value)} />
          </label>
          <label className="text-xs font-medium text-gray-600 dark:text-gray-300">
            Expires after (days)
            <input className={`${INPUT_CLASS} mt-1`} type="number" min={1} max={365} placeholder="Never"
              value={expiresInDays} onChange={(e) => setExpiresInDays(e.target.value)} />
          </label>
          <button type="submit" className={PRIMARY_BUTTON} disabled={busy}>Create link</button>
        </form>
      )}

      {links?.length === 0 && <p className="text-sm text-gray-500 dark:text-gray-400">No active invite links.</p>}
      {links && links.length > 0 && (
        <table className="w-full text-left text-sm">
          <thead className="text-xs text-gray-500 dark:text-gray-400">
            <tr><th className="py-2">Role</th><th>Uses</th><th>Expires</th><th>Created by</th><th /></tr>
          </thead>
          <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
            {links.map((link) => (
              <tr key={link.id}>
                <td className="py-2 text-gray-900 dark:text-white">{ROLE_LABELS[link.role]}</td>
                <td>{link.uses}{link.maxUses ? ` / ${link.maxUses}` : ""}</td>
                <td>{link.expiresAt ? formatDate(link.expiresAt) : "Never"}</td>
                <td>{link.createdByName ?? "—"}</td>
                <td className="text-right">
                  <button type="button" className={SECONDARY_BUTTON} disabled={busy}
                    onClick={() => void run(() => revokeInviteLink(slug, link.id, token))}>
                    Revoke
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <ErrorText message={error} />
    </SettingsSection>
  );
}
