import type { ApiErrorResponse } from "../lib/apiError";
import { CloseIcon } from "./icons";


interface ErrorAlertProps {
  error: ApiErrorResponse;
  className?: string;
  onDismiss: () => void;
}

export function ErrorAlert({ error, className = "", onDismiss }: ErrorAlertProps) {
  return (
    <div className={`p-3 border border-[var(--color-danger)] bg-red-50 text-[var(--color-danger)] rounded-md flex justify-between items-start gap-3 ${className}`}>
      <div className="flex flex-col gap-1">
        <span className="text-sm font-semibold">{error.code}</span>
        <p className="text-xs">{error.message}</p>
      </div>
      <button 
        onClick={onDismiss}
        className="text-[var(--color-danger)] hover:opacity-70 transition-colors"
        aria-label="Dismiss alert"
      >
        <CloseIcon className="w-4 h-4" />
      </button>
    </div>
  );
}
