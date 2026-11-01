import { API_BASE } from "./documentService";
import { apiFetch } from "../lib/session";

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  body: string | null;
  link: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationPreferences {
  inApp: boolean;
  email: boolean;
  dailyDigest: boolean;
}

function send(path: string, token: string, method = "GET", body?: unknown): Promise<Response> {
  return apiFetch(`${API_BASE}/me/${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export async function getUnreadCount(token: string): Promise<number> {
  const response = await send("notifications/unread-count", token);
  if (!response.ok) throw new Error("Could not load notifications");
  return (await response.json()).count;
}

export async function listNotifications(token: string): Promise<AppNotification[]> {
  const response = await send("notifications?size=20", token);
  if (!response.ok) throw new Error("Could not load notifications");
  return (await response.json()).content;
}

export async function markNotificationRead(id: string, token: string): Promise<void> {
  const response = await send(`notifications/${id}/read`, token, "POST");
  if (!response.ok) throw new Error("Could not update this notification");
}

export async function markAllNotificationsRead(token: string): Promise<void> {
  const response = await send("notifications/read-all", token, "POST");
  if (!response.ok) throw new Error("Could not update notifications");
}

export async function getNotificationPreferences(token: string): Promise<NotificationPreferences> {
  const response = await send("notification-preferences", token);
  if (!response.ok) throw new Error("Could not load your preferences");
  return response.json();
}

export async function updateNotificationPreferences(preferences: NotificationPreferences, token: string): Promise<void> {
  const response = await send("notification-preferences", token, "PUT", preferences);
  if (!response.ok) throw new Error("Could not save your preferences");
}
