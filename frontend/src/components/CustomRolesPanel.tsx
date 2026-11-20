import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ShieldPlus } from "lucide-react";
import {
  createCustomRole,
  deleteCustomRole,
  listCustomRoles,
  permissionLabel,
  updateCustomRole,
  type CustomRole,
} from "../services/roleService";
import { ErrorText, SettingsSection } from "./SettingsSection";
import { DANGER_BUTTON, INPUT_CLASS, PRIMARY_BUTTON, SECONDARY_BUTTON, errorMessage } from "../lib/settingsUi";

type Draft = { id: string | null; name: string; description: string; permissions: string[] };
const EMPTY: Draft = { id: null, name: "", description: "", permissions: [] };

// Roles add permissions on top of a member's built-in role. Only permissions you hold
// yourself are offered; the server enforces the same rule.
export function CustomRolesPanel({ slug, token, myPermissions }: {
  slug: string;
  token: string;
  myPermissions: ReadonlySet<string>;
}) {
  const [roles, setRoles] = useState<CustomRole[] | null>(null);
  const [grantable, setGrantable] = useState<string[]>([]);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const list = await listCustomRoles(slug, token);
    setRoles(list.roles);
    setGrantable(list.grantablePermissions);
  }, [slug, token]);

  useEffect(() => {
    let active = true;
    listCustomRoles(slug, token)
      .then((list) => {
        if (!active) return;
        setRoles(list.roles);
        setGrantable(list.grantablePermissions);
      })
      .catch((err: unknown) => active && setError(errorMessage(err, "Couldn't load roles")));
    return () => {
      active = false;
    };
  }, [slug, token]);

  const offered = grantable.filter((code) => myPermissions.has(code));

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      await reload();
      return true;
    } catch (err) {
      setError(errorMessage(err, "Something went wrong"));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function handleSave(event: FormEvent) {
    event.preventDefault();
    const body = { name: draft.name.trim(), description: draft.description.trim() || undefined, permissions: draft.permissions };
    const ok = await run(() => draft.id ? updateCustomRole(slug, draft.id, body, token) : createCustomRole(slug, body, token));
    if (ok) setDraft(EMPTY);
  }

  function toggle(code: string) {
    setDraft((d) => ({
      ...d,
      permissions: d.permissions.includes(code) ? d.permissions.filter((c) => c !== code) : [...d.permissions, code],
    }));
  }

  return (
    <SettingsSection icon={ShieldPlus} title="Custom roles"
      description="Give people extra permissions on top of their built-in role. Tray visibility still follows the built-in role.">
      <form onSubmit={handleSave} className="mb-5 space-y-3 rounded-xl border border-gray-200 p-4 dark:border-gray-800">
        <div className="grid gap-2 sm:grid-cols-2">
          <input className={INPUT_CLASS} aria-label="Role name" placeholder="Role name, e.g. Records clerk"
            value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
          <input className={INPUT_CLASS} aria-label="Role description" placeholder="Description (optional)"
            value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
        </div>
        <fieldset>
          <legend className="mb-2 text-xs font-medium text-gray-600 dark:text-gray-300">Permissions</legend>
          <div className="grid gap-1.5 sm:grid-cols-2 lg:grid-cols-3">
            {offered.map((code) => (
              <label key={code} className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-200">
                <input type="checkbox" className="h-4 w-4 rounded border-gray-300 text-indigo-600"
                  checked={draft.permissions.includes(code)} onChange={() => toggle(code)} />
                {permissionLabel(code)}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="flex gap-2">
          <button type="submit" className={PRIMARY_BUTTON} disabled={busy || !draft.name.trim() || draft.permissions.length === 0}>
            {draft.id ? "Save role" : "Create role"}
          </button>
          {draft.id && (
            <button type="button" className={SECONDARY_BUTTON} onClick={() => setDraft(EMPTY)}>Cancel</button>
          )}
        </div>
      </form>

      {roles?.length === 0 && <p className="text-sm text-gray-500 dark:text-gray-400">No custom roles yet.</p>}
      <ul className="space-y-2">
        {roles?.map((role) => (
          <li key={role.id} className="flex flex-wrap items-start justify-between gap-2 rounded-xl border border-gray-200 p-3 dark:border-gray-800">
            <div className="min-w-0">
              <div className="text-sm font-semibold text-gray-900 dark:text-white">
                {role.name} <span className="font-normal text-gray-500">· {role.memberCount} member{role.memberCount === 1 ? "" : "s"}</span>
              </div>
              {role.description && <p className="text-xs text-gray-500">{role.description}</p>}
              <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">{role.permissions.map(permissionLabel).join(", ")}</p>
            </div>
            <div className="flex gap-2">
              <button type="button" className={SECONDARY_BUTTON} disabled={busy}
                onClick={() => setDraft({ id: role.id, name: role.name, description: role.description ?? "", permissions: role.permissions })}>
                Edit
              </button>
              <button type="button" className={DANGER_BUTTON} disabled={busy}
                onClick={() => {
                  if (window.confirm(`Delete "${role.name}"? Its ${role.memberCount} member(s) keep only their built-in role.`)) {
                    void run(() => deleteCustomRole(slug, role.id, token));
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
