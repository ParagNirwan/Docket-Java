import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Bell, Settings2 } from "lucide-react";
import { notify } from "../lib/notifications";
import {
  getNotificationPreferences,
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  updateNotificationPreferences,
  type AppNotification,
  type NotificationPreferences,
} from "../services/notificationService";

const POLL_MS = 60_000;

const PREFERENCES: { key: keyof NotificationPreferences; label: string; hint: string }[] = [
  { key: "inApp", label: "In the app", hint: "Show them here" },
  { key: "email", label: "By email", hint: "As they happen" },
  { key: "dailyDigest", label: "Daily digest", hint: "One email a day, instead of one each" },
];

export function NotificationBell({ token }: { token: string }) {
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [preferences, setPreferences] = useState<NotificationPreferences | null>(null);
  const [showPreferences, setShowPreferences] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const refreshCount = useCallback(() => {
    // The bell isn't worth an error message: the next poll will try again.
    getUnreadCount(token).then(setUnread).catch(() => {});
  }, [token]);

  useEffect(() => {
    refreshCount();
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") refreshCount();
    }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [refreshCount]);

  useEffect(() => {
    if (!open) return;
    function handleOutside(e: MouseEvent) {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", handleOutside);
    window.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleOutside);
      window.removeEventListener("keydown", handleEscape);
    };
  }, [open]);

  function toggle() {
    if (!open) {
      listNotifications(token).then(setItems).catch(() => notify.error("Could not load notifications"));
      refreshCount();
    }
    setOpen(!open);
  }

  async function handleSelect(item: AppNotification) {
    if (!item.read) {
      setItems((previous) => previous.map((other) => (other.id === item.id ? { ...other, read: true } : other)));
      setUnread((previous) => Math.max(0, previous - 1));
      markNotificationRead(item.id, token).catch(() => {});
    }
    setOpen(false);
    // Links come from the server and are always paths inside the app.
    if (item.link?.startsWith("/")) navigate(item.link);
  }

  async function handleMarkAll() {
    await markAllNotificationsRead(token);
    setItems((previous) => previous.map((item) => ({ ...item, read: true })));
    setUnread(0);
  }

  async function togglePreferences() {
    if (!showPreferences && !preferences) {
      try {
        setPreferences(await getNotificationPreferences(token));
      } catch (err) {
        notify.error(err instanceof Error ? err.message : "Could not load your preferences");
        return;
      }
    }
    setShowPreferences(!showPreferences);
  }

  async function changePreference(key: keyof NotificationPreferences, value: boolean) {
    if (!preferences) return;
    const previous = preferences;
    const next = { ...preferences, [key]: value };
    setPreferences(next);
    try {
      await updateNotificationPreferences(next, token);
    } catch (err) {
      setPreferences(previous);
      notify.error(err instanceof Error ? err.message : "Could not save your preferences");
    }
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={toggle}
        className="relative rounded-xl border border-gray-200 p-2 text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900 focus:outline-none focus:ring-2 focus:ring-indigo-500 dark:border-gray-800 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-white"
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}
        aria-haspopup="true"
        aria-expanded={open}
      >
        <Bell className="h-5 w-5" />
        {unread > 0 && (
          <span aria-hidden="true" className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-80 max-w-[calc(100vw-2rem)] overflow-hidden rounded-xl border border-gray-200 bg-white shadow-xl dark:border-gray-700 dark:bg-gray-900">
          <div className="flex items-center justify-between border-b border-gray-100 px-3 py-2 dark:border-gray-800">
            <span className="text-sm font-semibold text-gray-900 dark:text-white">Notifications</span>
            <div className="flex items-center gap-1">
              {unread > 0 && (
                <button type="button" onClick={handleMarkAll} className="rounded-md px-2 py-1 text-xs font-medium text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-950/40">
                  Mark all read
                </button>
              )}
              <button type="button" onClick={togglePreferences} aria-label="Notification preferences" aria-expanded={showPreferences} className="rounded-md p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200">
                <Settings2 className="h-4 w-4" />
              </button>
            </div>
          </div>

          {showPreferences && preferences && (
            <div className="space-y-2 border-b border-gray-100 px-3 py-3 dark:border-gray-800">
              {PREFERENCES.map(({ key, label, hint }) => (
                <label key={key} className="flex items-start gap-2 text-xs text-gray-700 dark:text-gray-300">
                  <input
                    type="checkbox"
                    checked={preferences[key]}
                    onChange={(e) => changePreference(key, e.target.checked)}
                    className="mt-0.5"
                  />
                  <span>
                    <span className="font-medium">{label}</span>
                    <span className="block text-gray-500 dark:text-gray-400">{hint}</span>
                  </span>
                </label>
              ))}
            </div>
          )}

          <ul className="max-h-96 divide-y divide-gray-100 overflow-y-auto dark:divide-gray-800">
            {items.length === 0 ? (
              <li className="px-3 py-6 text-center text-xs text-gray-500 dark:text-gray-400">You're all caught up.</li>
            ) : (
              items.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => handleSelect(item)}
                    className={`block w-full px-3 py-2.5 text-left transition-colors hover:bg-gray-50 dark:hover:bg-gray-800 ${item.read ? "" : "bg-indigo-50/60 dark:bg-indigo-950/20"}`}
                  >
                    <span className="block text-xs font-medium text-gray-900 dark:text-white">{item.title}</span>
                    {item.body && <span className="mt-0.5 block text-xs text-gray-500 dark:text-gray-400">{item.body}</span>}
                    <span className="mt-1 block text-[10px] text-gray-400 dark:text-gray-500">{new Date(item.createdAt).toLocaleString()}</span>
                  </button>
                </li>
              ))
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
