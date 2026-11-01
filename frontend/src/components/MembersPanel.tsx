import { useCallback, useEffect, useMemo, useState } from "react";
import { Mail, RefreshCw, Search, Trash2, UserPlus, X } from "lucide-react";
import { DeleteConfirmationModal } from "./DeleteConfirmationModal";
import { InviteMembersModal } from "./InviteMembersModal";
import { notify } from "../lib/notifications";
import { assignableRoles, hasPermission, ROLE_LABELS, type MemberRole } from "../lib/permissions";
import {
  listInvitations,
  listMembers,
  removeMember,
  resendInvitation,
  revokeInvitation,
  updateMemberRole,
  type Invitation,
  type Member,
} from "../services/memberService";

type MembersPanelProps = {
  slug: string;
  token: string;
  permissions: ReadonlySet<string>;
};

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleDateString() : "—";
}

export function MembersPanel({ slug, token, permissions }: MembersPanelProps) {
  const [members, setMembers] = useState<Member[]>([]);
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [isInviteOpen, setIsInviteOpen] = useState(false);
  const [pendingRemoval, setPendingRemoval] = useState<Member | null>(null);
  const [pendingRevoke, setPendingRevoke] = useState<Invitation | null>(null);

  const load = useCallback(async () => {
    const [loadedMembers, loadedInvitations] = await Promise.all([
      listMembers(slug, token),
      listInvitations(slug, token),
    ]);
    setMembers(loadedMembers);
    setInvitations(loadedInvitations);
  }, [slug, token]);

  useEffect(() => {
    load()
      .catch((err: unknown) => setError(err instanceof Error ? err.message : "Could not load members"))
      .finally(() => setLoading(false));
  }, [load]);

  const myRole = members.find((member) => member.currentUser)?.role;
  const reachableRoles = assignableRoles(myRole);
  const canInvite = hasPermission(permissions, "members.invite");
  const canChangeRoles = hasPermission(permissions, "members.update_role");
  const canRemove = hasPermission(permissions, "members.remove");

  const visibleMembers = useMemo(() => {
    const term = search.trim().toLowerCase();
    return members.filter((member) =>
      !term || member.name.toLowerCase().includes(term) || member.email.toLowerCase().includes(term));
  }, [members, search]);

  async function handleRoleChange(member: Member, role: MemberRole) {
    try {
      await updateMemberRole(slug, member.membershipId, role, token);
      setMembers((previous) => previous.map((m) => m.membershipId === member.membershipId ? { ...m, role } : m));
      notify.success("Role updated", `${member.name} is now ${ROLE_LABELS[role]}`);
    } catch (err) {
      notify.error("Could not change the role", err instanceof Error ? err.message : undefined);
    }
  }

  async function handleResend(invitation: Invitation) {
    try {
      await resendInvitation(slug, invitation.id, token);
      notify.success("Invitation sent again", invitation.email);
    } catch (err) {
      notify.error("Could not resend", err instanceof Error ? err.message : undefined);
    }
  }

  if (loading) {
    return <p role="status" className="text-sm text-gray-500 dark:text-gray-400">Loading members...</p>;
  }
  if (error) {
    return (
      <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
        {error}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 items-center gap-2 rounded-xl border border-gray-200 bg-white px-3 py-2 dark:border-gray-700 dark:bg-gray-900 sm:max-w-sm">
          <Search className="h-4 w-4 shrink-0 text-gray-400" />
          <input
            type="search"
            aria-label="Search members"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name or email"
            className="w-full bg-transparent text-sm text-gray-900 placeholder-gray-400 outline-none dark:text-white"
          />
        </div>
        {canInvite && reachableRoles.length > 0 && (
          <button
            type="button"
            onClick={() => setIsInviteOpen(true)}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-indigo-600 px-3.5 py-2 text-xs font-semibold text-white shadow-xs transition-colors hover:bg-indigo-700"
          >
            <UserPlus className="h-4 w-4" />
            Invite people
          </button>
        )}
      </div>

      <section className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-xs dark:border-gray-800 dark:bg-gray-900">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="border-b border-gray-100 bg-gray-50/70 text-[10px] font-bold uppercase tracking-wider text-gray-500 dark:border-gray-800 dark:bg-gray-800/40 dark:text-gray-400">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Role</th>
                <th className="hidden px-4 py-3 sm:table-cell">Joined</th>
                <th className="hidden px-4 py-3 md:table-cell">Last active</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {visibleMembers.map((member) => {
                // The Owner and yourself never appear in reach: see MemberService.
                const inReach = !member.currentUser && reachableRoles.includes(member.role);
                return (
                  <tr key={member.membershipId}>
                    <td className="px-4 py-3">
                      <p className="font-semibold text-gray-900 dark:text-white">
                        {member.name}
                        {member.currentUser && <span className="ml-1.5 font-normal text-gray-400">(you)</span>}
                      </p>
                      <p className="text-gray-500 dark:text-gray-400">{member.email}</p>
                    </td>
                    <td className="px-4 py-3">
                      {canChangeRoles && inReach ? (
                        <select
                          aria-label={`Role for ${member.name}`}
                          value={member.role}
                          onChange={(e) => void handleRoleChange(member, e.target.value as MemberRole)}
                          className="rounded-lg border border-gray-200 bg-gray-50 px-2 py-1 text-xs font-medium text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-200"
                        >
                          {reachableRoles.map((role) => (
                            <option key={role} value={role}>{ROLE_LABELS[role]}</option>
                          ))}
                        </select>
                      ) : (
                        <span className="font-medium text-gray-700 dark:text-gray-200">{ROLE_LABELS[member.role]}</span>
                      )}
                    </td>
                    <td className="hidden px-4 py-3 text-gray-500 dark:text-gray-400 sm:table-cell">{formatDate(member.joinedAt)}</td>
                    <td className="hidden px-4 py-3 text-gray-500 dark:text-gray-400 md:table-cell">{formatDate(member.lastActiveAt)}</td>
                    <td className="px-4 py-3 text-right">
                      {canRemove && inReach && (
                        <button
                          type="button"
                          onClick={() => setPendingRemoval(member)}
                          aria-label={`Remove ${member.name}`}
                          title="Remove from organization"
                          className="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/40"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
              {visibleMembers.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-gray-500 dark:text-gray-400">No one matches that search.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {invitations.length > 0 && (
        <section>
          <h3 className="mb-3 text-xs font-bold uppercase tracking-wider text-gray-500 dark:text-gray-400">
            Pending invitations ({invitations.length})
          </h3>
          <div className="divide-y divide-gray-100 overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-xs dark:divide-gray-800 dark:border-gray-800 dark:bg-gray-900">
            {invitations.map((invitation) => (
              <div key={invitation.id} className="flex flex-col gap-2 px-4 py-3 text-xs sm:flex-row sm:items-center sm:justify-between">
                <div className="flex min-w-0 items-center gap-3">
                  <Mail className="h-4 w-4 shrink-0 text-gray-400" />
                  <div className="min-w-0">
                    <p className="truncate font-semibold text-gray-900 dark:text-white">{invitation.email}</p>
                    <p className="text-gray-500 dark:text-gray-400">
                      {ROLE_LABELS[invitation.role]}
                      {invitation.invitedByName && ` · Invited by ${invitation.invitedByName}`}
                      {` · Expires ${formatDate(invitation.expiresAt)}`}
                    </p>
                  </div>
                </div>
                {canInvite && reachableRoles.includes(invitation.role) && (
                  <div className="flex shrink-0 items-center gap-1">
                    <button type="button" onClick={() => void handleResend(invitation)} className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 font-medium text-indigo-600 transition-colors hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-950/40">
                      <RefreshCw className="h-3.5 w-3.5" />
                      Resend
                    </button>
                    <button type="button" onClick={() => setPendingRevoke(invitation)} className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 font-medium text-red-600 transition-colors hover:bg-red-50 dark:hover:bg-red-950/40">
                      <X className="h-3.5 w-3.5" />
                      Revoke
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      {isInviteOpen && (
        <InviteMembersModal
          slug={slug}
          token={token}
          roles={reachableRoles}
          onClose={() => setIsInviteOpen(false)}
          onInvited={() => {
            notify.success("Invitations sent");
            void load();
          }}
        />
      )}

      <DeleteConfirmationModal
        isOpen={pendingRemoval !== null}
        title="Remove from organization"
        itemName={pendingRemoval?.name || ""}
        description={`${pendingRemoval?.name} will lose access to this organization straight away. Their documents stay.`}
        confirmLabel="Remove"
        onClose={() => setPendingRemoval(null)}
        onConfirm={async () => {
          if (!pendingRemoval) return;
          await removeMember(slug, pendingRemoval.membershipId, token);
          setMembers((previous) => previous.filter((m) => m.membershipId !== pendingRemoval.membershipId));
          notify.success("Removed", `${pendingRemoval.name} no longer has access`);
        }}
      />

      <DeleteConfirmationModal
        isOpen={pendingRevoke !== null}
        title="Revoke invitation"
        itemName={pendingRevoke?.email || ""}
        description={`The link sent to ${pendingRevoke?.email} will stop working.`}
        confirmLabel="Revoke"
        onClose={() => setPendingRevoke(null)}
        onConfirm={async () => {
          if (!pendingRevoke) return;
          await revokeInvitation(slug, pendingRevoke.id, token);
          setInvitations((previous) => previous.filter((i) => i.id !== pendingRevoke.id));
          notify.success("Invitation revoked", pendingRevoke.email);
        }}
      />
    </div>
  );
}
