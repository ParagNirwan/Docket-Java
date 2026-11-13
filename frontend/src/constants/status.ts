export const TENANT_STATUS = {
  ACTIVE: {
    label: 'Active',
    colorClass: 'bg-[#2EB67D]/15 text-[#1b7a51] border-[#2EB67D]/40',
    dotClass: 'bg-[#2EB67D]',
  },
  INACTIVE: {
    label: 'Inactive',
    colorClass: 'bg-[#F8F8F8] text-[#616061] border-[#DDDDDD]',
    dotClass: 'bg-[#616061]',
  },
} as const;

export type TenantStatus = keyof typeof TENANT_STATUS;
