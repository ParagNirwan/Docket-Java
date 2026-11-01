import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

type AuthCardProps = {
  icon: LucideIcon;
  title: string;
  subtitle: string;
  children: ReactNode;
};

// The centred card shared by the account recovery pages.
export function AuthCard({ icon: Icon, title, subtitle, children }: AuthCardProps) {
  return (
    <div className="flex min-h-[calc(100svh-4rem)] flex-1 items-center justify-center px-4 py-12 sm:px-6 lg:px-8">
      <div className="w-full max-w-md overflow-hidden rounded-2xl border border-gray-200 bg-white p-7 shadow-xs dark:border-gray-800 dark:bg-gray-900 sm:p-8">
        <div className="text-center mb-6">
          <div className="mx-auto mb-3.5 flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600 dark:bg-indigo-950/60 dark:text-indigo-400">
            <Icon className="h-6 w-6" strokeWidth={1.8} />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{title}</h1>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{subtitle}</p>
        </div>
        {children}
      </div>
    </div>
  );
}

export function AuthError({ message }: { message: string }) {
  return (
    <div role="alert" className="mb-5 rounded-xl border border-red-200 bg-red-50/60 p-3.5 text-xs text-red-700 dark:border-red-900/60 dark:bg-red-950/30 dark:text-red-400">
      {message}
    </div>
  );
}

export function AuthNotice({ message }: { message: string }) {
  return (
    <div role="status" className="mb-5 rounded-xl border border-emerald-200 bg-emerald-50/60 p-3.5 text-xs text-emerald-800 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-300">
      {message}
    </div>
  );
}
