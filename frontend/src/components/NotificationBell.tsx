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
    // Older invitation notifications had no link, but their dashboard acceptance
    // action is available there even when the invitation email could not be sent.
    const link = item.link?.startsWith("/")
      ? item.link
      : item.type === "INVITED"
        ? "/tenants"
        : null;
    if (link) navigate(link);
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
        className="relative rounded-[8px] border border-[#DDDDDD] p-2 text-[#616061] transition-colors hover:bg-[#F8F8F8] hover:text-[#1D1C1D] focus:outline-none focus:ring-2 focus:ring-[#36C5F0]"
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}
        aria-haspopup="true"
        aria-expanded={open}
      >
        <Bell className="h-5 w-5" />
        {unread > 0 && (
          <span aria-hidden="true" className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-[4px] bg-[#E01E5A] px-1 text-[10px] font-bold text-white shadow-[0_1px_2px_rgba(0,0,0,0.2)]">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-80 max-w-[calc(100vw-2rem)] overflow-hidden rounded-[8px] border border-[#DDDDDD] bg-white shadow-[0_4px_12px_rgba(0,0,0,0.15)]">
          <div className="flex items-center justify-between border-b border-[#DDDDDD] px-3.5 py-2.5">
            <span className="text-[14px] font-bold text-[#1D1C1D]">Notifications</span>
            <div className="flex items-center gap-1.5">
              {unread > 0 && (
                <button type="button" onClick={handleMarkAll} className="rounded-[4px] px-2 py-0.5 text-[12px] font-medium text-[#1164A3] hover:bg-[#F8F8F8]">
                  Mark all read
                </button>
              )}
              <button type="button" onClick={togglePreferences} aria-label="Notification preferences" aria-expanded={showPreferences} className="rounded-[4px] p-1 text-[#616061] hover:bg-[#F8F8F8] hover:text-[#1D1C1D]">
                <Settings2 className="h-4 w-4" />
              </button>
            </div>
          </div>

          {showPreferences && preferences && (
            <div className="space-y-2 border-b border-[#DDDDDD] px-3.5 py-3 bg-[#F8F8F8]">
              {PREFERENCES.map(({ key, label, hint }) => (
                <label key={key} className="flex items-start gap-2 text-xs text-[#1D1C1D]">
                  <input
                    type="checkbox"
                    checked={preferences[key]}
                    onChange={(e) => changePreference(key, e.target.checked)}
                    className="mt-0.5"
                  />
                  <span>
                    <span className="font-semibold">{label}</span>
                    <span className="block text-[#616061]">{hint}</span>
                  </span>
                </label>
              ))}
            </div>
          )}

          <ul className="max-h-96 divide-y divide-[#DDDDDD] overflow-y-auto">
            {items.length === 0 ? (
              <li className="px-3 py-6 text-center text-xs text-[#616061]">You're all caught up.</li>
            ) : (
              items.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => handleSelect(item)}
                    className={`block w-full px-3.5 py-2.5 text-left transition-colors hover:bg-[#F8F8F8] ${item.read ? "" : "bg-[#ECB22E]/10"}`}
                  >
                    <span className="block text-[13px] font-semibold text-[#1D1C1D]">{item.title}</span>
                    {item.body && <span className="mt-0.5 block text-[12px] text-[#616061]">{item.body}</span>}
                    <span className="mt-1 block text-[12px] text-[#616061]">{new Date(item.createdAt).toLocaleString()}</span>
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
