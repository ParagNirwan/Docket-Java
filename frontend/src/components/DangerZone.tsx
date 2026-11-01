import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, LogOut } from "lucide-react";
import { DeleteConfirmationModal } from "./DeleteConfirmationModal";
import { notify } from "../lib/notifications";
import { hasPermission, ROLE_LABELS } from "../lib/permissions";
import { leaveOrganization, listMembers, transferOwnership, type Member } from "../services/memberService";

type DangerZoneProps = {
  slug: string;
  token: string;
  tenantName: string;
  permissions: ReadonlySet<string>;
  // The Owner has just become an Admin, so what they can do here has changed.
  onOwnershipTransferred: () => void;
};

export function DangerZone({ slug, token, tenantName, permissions, onOwnershipTransferred }: DangerZoneProps) {
  const canTransfer = hasPermission(permissions, "tenant.transfer_ownership");
  const navigate = useNavigate();
  const [isLeaveOpen, setIsLeaveOpen] = useState(false);

  return (
    <div className="space-y-4">
      {canTransfer ? (
        <TransferOwnership
          slug={slug}
          token={token}
          tenantName={tenantName}
          onTransferred={onOwnershipTransferred}
        />
      ) : (
        <section className="rounded-2xl border border-red-200 bg-white p-5 shadow-xs dark:border-red-900/60 dark:bg-gray-900">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Leave this organization</h3>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            You'll lose access straight away. To come back, someone will need to invite you again.
          </p>
          <button
            type="button"
            onClick={() => setIsLeaveOpen(true)}
            className="mt-4 inline-flex items-center gap-2 rounded-xl border border-red-200 px-3.5 py-2 text-xs font-semibold text-red-600 transition-colors hover:bg-red-50 dark:border-red-900/60 dark:text-red-400 dark:hover:bg-red-950/40"
          >
            <LogOut className="h-4 w-4" />
            Leave organization
          </button>
        </section>
      )}

      <DeleteConfirmationModal
        isOpen={isLeaveOpen}
        title="Leave organization"
        itemName={tenantName}
        description={`You'll lose access to ${tenantName} straight away.`}
        confirmLabel="Leave"
        onClose={() => setIsLeaveOpen(false)}
        onConfirm={async () => {
          await leaveOrganization(slug, token);
          notify.success("You left the organization", tenantName);
          navigate("/tenants");
        }}
      />
    </div>
  );
}

type TransferOwnershipProps = {
  slug: string;
  token: string;
  tenantName: string;
  onTransferred: () => void;
};

function TransferOwnership({ slug, token, tenantName, onTransferred }: TransferOwnershipProps) {
  const [candidates, setCandidates] = useState<Member[]>([]);
  const [newOwnerId, setNewOwnerId] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    listMembers(slug, token)
      .then((members) => {
        if (!cancelled) setCandidates(members.filter((member) => !member.currentUser));
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Could not load members");
      });
    return () => {
      cancelled = true;
    };
  }, [slug, token]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    try {
      setIsSubmitting(true);
      setError("");
      await transferOwnership(slug, newOwnerId, password, token);
      const newOwner = candidates.find((member) => member.membershipId === newOwnerId);
      notify.success("Ownership transferred", `${newOwner?.name} now owns ${tenantName}. You are now an Admin.`);
      onTransferred();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not transfer ownership");
      setIsSubmitting(false);
    }
    setPassword("");
  }

  return (
    <section className="rounded-2xl border border-red-200 bg-white p-5 shadow-xs dark:border-red-900/60 dark:bg-gray-900">
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-red-600 dark:text-red-400" />
        <div>
          <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Transfer ownership</h3>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            The person you choose becomes the Owner and you become an Admin. Only they can change this back.
            You must transfer ownership before you can leave {tenantName}.
          </p>
        </div>
      </div>

      {error && (
        <div role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-600 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
          {error}
        </div>
      )}

      {candidates.length === 0 ? (
        <p className="mt-4 text-xs text-gray-500 dark:text-gray-400">Invite someone first: there's no one else to transfer to.</p>
      ) : (
        <form onSubmit={handleSubmit} className="mt-4 grid gap-3 sm:grid-cols-2">
          <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
            New owner
            <select
              value={newOwnerId}
              onChange={(e) => setNewOwnerId(e.target.value)}
              required
              className="mt-1 w-full rounded-xl border border-gray-200 bg-gray-50/50 px-3 py-2 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-800/50 dark:text-white"
            >
              <option value="">Choose a member</option>
              {candidates.map((member) => (
                <option key={member.membershipId} value={member.membershipId}>
                  {member.name} ({ROLE_LABELS[member.role]})
                </option>
              ))}
            </select>
          </label>
          <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
            Your password
            <input
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full rounded-xl border border-gray-200 bg-gray-50/50 px-3 py-2 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-800/50 dark:text-white"
            />
          </label>
          <div className="sm:col-span-2">
            <button
              type="submit"
              disabled={isSubmitting || !newOwnerId || !password}
              className="rounded-xl bg-red-600 px-3.5 py-2 text-xs font-semibold text-white shadow-xs transition-colors hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isSubmitting ? "Transferring..." : "Transfer ownership"}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
