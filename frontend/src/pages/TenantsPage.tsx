import { apiFetch } from "../lib/session";
import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import { ROLE_LABELS } from "../lib/permissions";
import { acceptDashboardInvitation, listMyInvitations, type DashboardInvitation } from "../services/memberService";
import {
  BuildingIcon,
  PlusIcon,
  SettingsIcon,
  ShieldIcon,
  ChevronRightIcon,
} from "../components/icons";
import { StatusBadge } from "../components/ui/StatusBadge";

interface TenantItem {
  id?: number | string;
  name?: string;
  tenantName?: string;
  slug?: string;
  tenantSlug?: string;
  role?: string;
  status?: string;
}

export function TenantsPage() {
  const { token, openSettingsModal } = useAuth();
  const navigate = useNavigate();
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [invitations, setInvitations] = useState<DashboardInvitation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [invitationError, setInvitationError] = useState<string | null>(null);
  const [acceptingId, setAcceptingId] = useState<string | null>(null);

  useEffect(() => {
    async function loadTenants() {
      if (!token) return;
      try {
        const apiUrl = import.meta.env.VITE_API_URL || "/api";
        const res = await apiFetch(`${apiUrl}/users/list-tenants`, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        if (res.ok) {
          const data = await res.json();
          if (Array.isArray(data)) {
            setTenants(data);
          }
        } else {
          setError("Unable to load organizations");
        }
      } catch {
        setError("Network connection issue while loading organizations");
      } finally {
        setLoading(false);
      }
    }

    async function loadInvitations() {
      if (!token) return;
      try {
        setInvitations(await listMyInvitations(token));
      } catch (err) {
        setInvitationError(err instanceof Error ? err.message : "Unable to load invitations");
      }
    }

    void loadTenants();
    void loadInvitations();
  }, [token]);

  async function acceptInvitation(invitation: DashboardInvitation) {
    if (!token) return;
    try {
      setAcceptingId(invitation.id);
      setInvitationError(null);
      const accepted = await acceptDashboardInvitation(invitation.id, token);
      setInvitations((current) => current.filter((item) => item.id !== invitation.id));
      navigate(`/workspace/${encodeURIComponent(accepted.tenantSlug)}`, {
        state: { tenantName: accepted.tenantName },
      });
    } catch (err) {
      setInvitationError(err instanceof Error ? err.message : "Could not accept the invitation");
    } finally {
      setAcceptingId(null);
    }
  }

  return (
    <div className="w-full px-4 py-8 sm:px-6 lg:px-8 bg-white text-[#1D1C1D]">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-[26px] font-bold tracking-tight text-[#1D1C1D]">
            Organizations & Tenants
          </h1>
          <p className="mt-1 text-[14px] text-[#616061]">
            Select a tenant workspace to review dockets and manage documents.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={openSettingsModal}
            className="inline-flex items-center gap-2 px-3 py-1.5 text-[14px] font-medium border border-[#DDDDDD] hover:bg-[#F8F8F8] rounded-[8px] text-[#1D1C1D] transition-colors"
          >
            <SettingsIcon className="w-4 h-4 text-[#616061]" />
            Account Settings
          </button>
          <Link
            to="/create-first-tenant"
            className="inline-flex items-center gap-2 px-3.5 py-1.5 text-[14px] font-semibold text-white bg-[#007A5A] hover:bg-[#00664B] rounded-[8px] shadow-[0_1px_2px_rgba(0,0,0,0.1)] transition-colors"
          >
            <PlusIcon className="w-4 h-4" />
            New Tenant
          </Link>
        </div>
      </div>

      {invitations.length > 0 && (
        <section aria-labelledby="pending-invitations-heading" className="mb-8">
          <h2 id="pending-invitations-heading" className="mb-3 text-[16px] font-bold text-[#1D1C1D]">
            Pending invitations
          </h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {invitations.map((invitation) => (
              <div key={invitation.id} className="flex items-center justify-between gap-4 rounded-[8px] border border-[#DDDDDD] bg-white p-5 shadow-[0_1px_2px_rgba(0,0,0,0.05)]">
                <div>
                  <h3 className="font-bold text-[15px] text-[#1D1C1D]">{invitation.tenantName}</h3>
                  <p className="mt-0.5 text-[12px] text-[#616061]">
                    {invitation.invitedByName ? `${invitation.invitedByName} invited you` : "You were invited"} as {ROLE_LABELS[invitation.role]}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void acceptInvitation(invitation)}
                  disabled={acceptingId !== null}
                  className="shrink-0 rounded-[8px] bg-[#007A5A] px-3.5 py-1.5 text-[13px] font-semibold text-white transition-colors hover:bg-[#00664B] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {acceptingId === invitation.id ? "Joining..." : "Accept"}
                </button>
              </div>
            ))}
          </div>
        </section>
      )}
      {invitationError && (
        <div role="alert" className="mb-6 rounded-[4px] border border-[#E01E5A]/30 bg-[#E01E5A]/10 p-3.5 text-[12px] font-medium text-[#E01E5A]">
          {invitationError}
        </div>
      )}

      {/* Content */}
      {loading ? (
        <div className="flex items-center justify-center p-16 text-sm text-[#616061]">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#1164A3] mr-3" />
          Loading your workspaces...
        </div>
      ) : error ? (
        <div className="p-3.5 rounded-[4px] border border-[#E01E5A]/30 bg-[#E01E5A]/10 text-[#E01E5A] text-[12px] font-medium">
          {error}
        </div>
      ) : tenants.length === 0 ? (
        <div className="text-center py-16 px-4 bg-white border border-dashed border-[#DDDDDD] rounded-[8px]">
          <div className="w-12 h-12 rounded-[8px] bg-[#4A154B] text-white flex items-center justify-center mx-auto mb-4">
            <BuildingIcon className="w-6 h-6" />
          </div>
          <h3 className="text-[18px] font-bold text-[#1D1C1D]">
            No tenants yet
          </h3>
          <p className="text-[13px] text-[#616061] max-w-sm mx-auto mt-1 mb-6">
            You don't have any workspaces yet. Accept a pending invitation above
            or create your first tenant to start managing your legal dockets.
          </p>
          <Link
            to="/create-first-tenant"
            className="inline-flex items-center gap-2 px-4 py-2 text-[14px] font-semibold text-white bg-[#007A5A] hover:bg-[#00664B] rounded-[8px] shadow-[0_1px_2px_rgba(0,0,0,0.1)] transition-colors"
          >
            <PlusIcon className="w-4 h-4" />
            Create Your First Tenant
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {tenants.map((t, index) => {
            const name =
              t.tenantName || t.name || `Tenant #${t.id || index + 1}`;
            const slug = t.tenantSlug || t.slug || "";
            return (
              <Link
                key={t.id || index}
                to={`/workspace/${encodeURIComponent(String(t.slug || t.tenantSlug ))}`}
                state={{ tenantName: name }}
                className="group relative bg-white border border-[#DDDDDD] rounded-[8px] p-5 shadow-[0_1px_2px_rgba(0,0,0,0.05)] hover:bg-[#F8F8F8] hover:border-[#C1C1C1] transition-all flex flex-col justify-between focus:outline-none focus-visible:ring-2 focus-visible:ring-[#36C5F0]"
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-3.5">
                    <div className="w-9 h-9 rounded-[8px] bg-[#4A154B] text-white flex items-center justify-center font-bold">
                      <BuildingIcon className="w-4 h-4" />
                    </div>
                    <StatusBadge status={t.status || "Inactive"} />
                  </div>
                  <h3 className="text-[16px] font-bold text-[#1D1C1D] group-hover:text-[#1164A3] transition-colors">
                    {name}
                  </h3>
                  {slug && (
                    <p className="text-[12px] text-[#616061] mt-0.5 font-mono">
                      {slug}
                    </p>
                  )}
                </div>

                <div className="pt-4 mt-4 border-t border-[#DDDDDD] flex items-center justify-between text-[12px] text-[#616061]">
                  <span className="flex items-center gap-1">
                    <ShieldIcon className="w-3.5 h-3.5" />
                    {t.role || "Member"}
                  </span>
                  <span className="flex items-center gap-1 text-[#1164A3] font-semibold group-hover:translate-x-0.5 transition-transform">
                    Enter Workspace <ChevronRightIcon className="w-3.5 h-3.5" />
                  </span>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
