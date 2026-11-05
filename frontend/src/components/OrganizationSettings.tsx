import { useState } from "react";
import { DangerZone } from "./DangerZone";
import { LegalHoldsPanel } from "./LegalHoldsPanel";
import { MembersPanel } from "./MembersPanel";
import { RetentionPanel } from "./RetentionPanel";
import { StorageQuotaPanel } from "./StorageQuotaPanel";
import { hasPermission } from "../lib/permissions";
import type { Tray } from "../services/trayService";

type OrganizationSettingsProps = {
  slug: string;
  token: string;
  tenantName: string;
  permissions: ReadonlySet<string>;
  trays?: Tray[];
  // Open on a particular tab, such as from a notification.
  initialTab?: string | null;
  onOwnershipTransferred: () => void;
};

type Tab = "members" | "storage" | "retention" | "legal-holds" | "danger";

export function OrganizationSettings({ slug, token, tenantName, permissions, trays = [], initialTab, onOwnershipTransferred }: OrganizationSettingsProps) {
  const canSeeMembers = hasPermission(permissions, "members.read");
  const canSeeRetention = hasPermission(permissions, "retention.read");
  const canManageQuota = hasPermission(permissions, "tenant.update") || hasPermission(permissions, "retention.manage");

  const resolvedInitialTab = initialTab === "general" ? "storage" : initialTab;

  const tabs: { id: Tab; label: string }[] = [
    ...(canSeeMembers ? [{ id: "members" as const, label: "Members" }] : []),
    { id: "storage" as const, label: "Storage & Quotas" },
    ...(canSeeRetention ? [
      { id: "retention" as const, label: "Retention" },
      { id: "legal-holds" as const, label: "Legal holds" },
    ] : []),
    { id: "danger", label: "Danger zone" },
  ];

  const [tab, setTab] = useState<Tab>(
    tabs.find(({ id }) => id === resolvedInitialTab)?.id ?? (canSeeMembers ? "members" : "storage"),
  );

  return (
    <div>
      <div role="tablist" aria-label="Organization settings" className="mb-6 flex gap-1 border-b border-gray-200 dark:border-gray-800">
        {tabs.map(({ id, label }) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={tab === id}
            onClick={() => setTab(id)}
            className={`-mb-px border-b-2 px-3.5 py-2 text-sm font-medium transition-colors ${
              tab === id
                ? "border-indigo-600 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400"
                : "border-transparent text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "members" && canSeeMembers && <MembersPanel slug={slug} token={token} permissions={permissions} />}
      {tab === "storage" && <StorageQuotaPanel slug={slug} token={token} canManageQuota={canManageQuota} />}
      {tab === "retention" && canSeeRetention && <RetentionPanel slug={slug} token={token} trays={trays} permissions={permissions} />}
      {tab === "legal-holds" && canSeeRetention && <LegalHoldsPanel slug={slug} token={token} trays={trays} permissions={permissions} />}
      {tab === "danger" && (
        <DangerZone
          slug={slug}
          token={token}
          tenantName={tenantName}
          permissions={permissions}
          onOwnershipTransferred={onOwnershipTransferred}
        />
      )}
    </div>
  );
}
