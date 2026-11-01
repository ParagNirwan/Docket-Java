import { useEffect, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { UserPlus, X } from "lucide-react";
import { z } from "zod";
import { ROLE_LABELS, type MemberRole } from "../lib/permissions";
import { inviteMember } from "../services/memberService";

interface InviteMembersModalProps {
  slug: string;
  token: string;
  roles: MemberRole[];
  onClose: () => void;
  // Called after any invitation went out, even if others in the batch didn't.
  onInvited: () => void;
}

const emailSchema = z.string().email();

interface Rejection {
  email: string;
  reason: string;
}

// Rendered only while open, so each time starts empty.
export function InviteMembersModal({ slug, token, roles, onClose, onInvited }: InviteMembersModalProps) {
  const [emails, setEmails] = useState<string[]>([]);
  const [draft, setDraft] = useState("");
  const [role, setRole] = useState<MemberRole>(roles[roles.length - 1]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [draftError, setDraftError] = useState<string | null>(null);
  const [rejections, setRejections] = useState<Rejection[]>([]);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  // Accepts one address or a pasted list. Returns the full list for use before state
  // updates, or null if something typed still needs fixing.
  function commitDraft(): string[] | null {
    const typed = draft.split(/[\s,;]+/).map((part) => part.trim().toLowerCase()).filter(Boolean);
    const invalid = typed.filter((email) => !emailSchema.safeParse(email).success);
    const next = [...new Set([...emails, ...typed.filter((email) => !invalid.includes(email))])];
    setEmails(next);
    setDraft(invalid.join(" "));
    setDraftError(invalid.length > 0 ? `Check this address: ${invalid[0]}` : null);
    return invalid.length > 0 ? null : next;
  }

  function handleKeyDown(e: ReactKeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter" || e.key === "," || e.key === ";") {
      e.preventDefault();
      commitDraft();
    } else if (e.key === "Backspace" && !draft && emails.length > 0) {
      setEmails(emails.slice(0, -1));
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const recipients = commitDraft();
    if (!recipients || recipients.length === 0) return;

    setIsSubmitting(true);
    setRejections([]);
    const results = await Promise.allSettled(recipients.map((email) => inviteMember(slug, email, role, token)));
    const failed = recipients.flatMap((email, i) => {
      const result = results[i];
      return result.status === "rejected"
        ? [{ email, reason: result.reason instanceof Error ? result.reason.message : "Could not send" }]
        : [];
    });

    if (failed.length < recipients.length) onInvited();
    setIsSubmitting(false);
    if (failed.length === 0) {
      onClose();
    } else {
      // What's left in the box is what still needs attention.
      setEmails(failed.map((rejection) => rejection.email));
      setRejections(failed);
    }
  }

  return (
    <div role="dialog" aria-modal="true" aria-labelledby="invite-title" className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6">
      <div className="fixed inset-0 bg-black/50 backdrop-blur-xs" onClick={onClose} />
      <form onSubmit={handleSubmit} noValidate className="relative w-full max-w-lg bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-2xl shadow-2xl z-10 overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 dark:border-gray-800">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 flex items-center justify-center text-indigo-600 dark:text-indigo-400">
              <UserPlus className="w-5 h-5" />
            </div>
            <h2 id="invite-title" className="text-lg font-bold text-gray-900 dark:text-white">Invite people</h2>
          </div>
          <button type="button" onClick={onClose} aria-label="Close dialog" className="p-1.5 rounded-lg text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          {rejections.length > 0 && (
            <ul role="alert" className="rounded-xl border border-red-200 dark:border-red-900/60 bg-red-50 dark:bg-red-950/30 p-3 text-xs text-red-600 dark:text-red-400 space-y-1">
              {rejections.map((rejection) => (
                <li key={rejection.email}><span className="font-semibold">{rejection.email}:</span> {rejection.reason}</li>
              ))}
            </ul>
          )}

          <div>
            <label htmlFor="invite-emails" className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5">
              Email addresses
            </label>
            <div className="flex flex-wrap items-center gap-1.5 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 px-2.5 py-2 focus-within:border-indigo-500 focus-within:ring-1 focus-within:ring-indigo-500">
              {emails.map((email) => (
                <span key={email} className="inline-flex items-center gap-1 rounded-lg bg-indigo-50 dark:bg-indigo-950/60 px-2 py-0.5 text-xs font-medium text-indigo-700 dark:text-indigo-300">
                  {email}
                  <button type="button" onClick={() => setEmails(emails.filter((e) => e !== email))} aria-label={`Remove ${email}`} className="hover:text-indigo-900 dark:hover:text-white">
                    <X className="h-3 w-3" />
                  </button>
                </span>
              ))}
              <input
                id="invite-emails"
                type="text"
                autoFocus
                value={draft}
                onChange={(e) => {
                  setDraft(e.target.value);
                  setDraftError(null);
                }}
                onKeyDown={handleKeyDown}
                onBlur={() => draft.trim() && commitDraft()}
                placeholder={emails.length === 0 ? "name@company.com, another@company.com" : ""}
                className="min-w-[120px] flex-1 bg-transparent py-0.5 text-sm text-gray-900 dark:text-white placeholder-gray-400 outline-none"
              />
            </div>
            {draftError && <p className="mt-1 text-xs font-medium text-red-600 dark:text-red-400">{draftError}</p>}
          </div>

          <div>
            <label htmlFor="invite-role" className="block text-xs font-semibold uppercase tracking-wider text-gray-700 dark:text-gray-300 mb-1.5">
              Role
            </label>
            <select
              id="invite-role"
              value={role}
              onChange={(e) => setRole(e.target.value as MemberRole)}
              className="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 px-3.5 py-2.5 text-sm text-gray-900 dark:text-white focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            >
              {roles.map((option) => (
                <option key={option} value={option}>{ROLE_LABELS[option]}</option>
              ))}
            </select>
          </div>

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-gray-100 dark:border-gray-800">
            <button type="button" onClick={onClose} disabled={isSubmitting} className="px-4 py-2 text-xs font-medium rounded-xl border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors">
              Cancel
            </button>
            <button type="submit" disabled={isSubmitting || (emails.length === 0 && !draft.trim())} className="px-4 py-2 text-xs font-medium rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
              {isSubmitting ? "Sending..." : "Send invitations"}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}
