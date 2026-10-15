export const TENANT_STATUS = {
  ACTIVE: {
    label: 'Active',
    colorClass: 'bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800',
  },
  INACTIVE: {
    label: 'Inactive',
    colorClass: 'bg-gray-50 dark:bg-gray-950/50 text-gray-700 dark:text-gray-400 border-gray-200 dark:border-gray-800',
  },
} as const;

export type TenantStatus = keyof typeof TENANT_STATUS;
