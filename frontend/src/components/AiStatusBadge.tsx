import { useState, useEffect } from "react";
import { Sparkles, Loader2, AlertCircle, MinusCircle } from "lucide-react";
import { getIndexingStatus, type IndexingStatusResponse } from "../services/aiService";

type IndexingStatus = IndexingStatusResponse["status"];

interface AiStatusBadgeProps {
  slug: string;
  docId: string;
  token: string | null;
  initialStatus?: IndexingStatus;
  onStatusChange?: (status: IndexingStatus) => void;
}

const STATUS_CONFIG: Record<IndexingStatus, {
  label: string;
  icon: typeof Sparkles;
  className: string;
  pulse?: boolean;
}> = {
  PENDING: {
    label: "Indexing...",
    icon: Loader2,
    className: "bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400",
    pulse: true,
  },
  PROCESSING: {
    label: "Indexing...",
    icon: Loader2,
    className: "bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400",
    pulse: true,
  },
  COMPLETED: {
    label: "AI Ready",
    icon: Sparkles,
    className: "bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40 dark:text-emerald-400",
  },
  NOT_PROCESSABLE: {
    label: "Not supported",
    icon: MinusCircle,
    className: "bg-gray-100 text-gray-400 dark:bg-gray-800 dark:text-gray-500",
  },
  FAILED: {
    label: "Failed",
    icon: AlertCircle,
    className: "bg-red-50 text-red-500 dark:bg-red-950/40 dark:text-red-400",
  },
};

export function AiStatusBadge({
  slug,
  docId,
  token,
  initialStatus,
  onStatusChange,
}: AiStatusBadgeProps) {
  const [polledStatus, setPolledStatus] = useState<IndexingStatus | null>(null);
  const status = polledStatus ?? initialStatus ?? "PENDING";

  useEffect(() => {
    if (!token) return;
    const shouldPoll = status === "PENDING" || status === "PROCESSING";
    if (!shouldPoll) return;

    const interval = setInterval(async () => {
      try {
        const result = await getIndexingStatus(slug, docId, token);
        setPolledStatus(result.status);
        onStatusChange?.(result.status);
      } catch {
        // silently ignore polling errors
      }
    }, 5000);

    return () => clearInterval(interval);
  }, [slug, docId, token, status, onStatusChange]);

  const config = STATUS_CONFIG[status];
  const Icon = config.icon;

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[9px] font-semibold ${config.className} ${config.pulse ? "animate-pulse" : ""}`}
      title={config.label}
    >
      <Icon className={`h-2.5 w-2.5 ${status === "PENDING" || status === "PROCESSING" ? "animate-spin" : ""}`} />
      <span className="hidden sm:inline">{config.label}</span>
    </span>
  );
}
