import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

type AuthCardProps = {
  icon: LucideIcon;
  title: string;
  subtitle: string;
  children: ReactNode;
};

// The centred card shared by the account recovery and auth pages.
export function AuthCard({ icon: Icon, title, subtitle, children }: AuthCardProps) {
  return (
    <div className="flex min-h-[calc(100svh-4rem)] flex-1 items-center justify-center px-4 py-10 sm:px-6 lg:px-8 bg-white">
      <div className="w-full max-w-md overflow-hidden rounded-[8px] border border-[#DDDDDD] bg-white p-7 shadow-[0_4px_12px_rgba(0,0,0,0.1)] sm:p-8">
        <div className="text-center mb-6">
          <div className="mx-auto mb-3.5 flex h-11 w-11 items-center justify-center rounded-[8px] border border-[#DDDDDD] bg-[#F8F8F8] text-[#1D1C1D] shadow-[0_1px_2px_rgba(0,0,0,0.05)]">
            <Icon className="h-5 w-5" strokeWidth={1.8} />
          </div>
          <h1 className="text-[22px] font-bold tracking-tight text-[#1D1C1D] leading-tight">{title}</h1>
          <p className="mt-1 text-[13px] text-[#616061]">{subtitle}</p>
        </div>
        {children}
      </div>
    </div>
  );
}

export function AuthError({ message }: { message: string }) {
  return (
    <div role="alert" className="mb-4 rounded-[4px] border border-[#E01E5A]/30 bg-[#E01E5A]/10 p-3 text-[12px] font-medium text-[#E01E5A]">
      {message}
    </div>
  );
}

export function AuthNotice({ message }: { message: string }) {
  return (
    <div role="status" className="mb-4 rounded-[4px] border border-[#2EB67D]/30 bg-[#2EB67D]/10 p-3 text-[12px] font-medium text-[#1b7a51]">
      {message}
    </div>
  );
}
