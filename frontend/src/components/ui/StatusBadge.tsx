import type { TenantStatus } from "../../constants/status";
import { TENANT_STATUS } from "../../constants/status";

interface StatusBadgeProps {
  status: TenantStatus | string;
}

export function StatusBadge({ status }: StatusBadgeProps) {
  // Normalize status to uppercase to match backend Enum (ACTIVE, INACTIVE)
  const normalizedStatus = (status || "").toUpperCase() as TenantStatus;
  const config = TENANT_STATUS[normalizedStatus] || TENANT_STATUS.INACTIVE;

  return (
    <span className={`inline-flex items-center gap-1.5 text-[11px] font-bold px-2 py-0.5 rounded-[4px] border ${config.colorClass}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${config.dotClass}`} />
      {config.label}
    </span>
  );
}
