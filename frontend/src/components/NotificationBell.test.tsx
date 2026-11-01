import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { NotificationBell } from "./NotificationBell";
import {
  getNotificationPreferences,
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  updateNotificationPreferences,
  type AppNotification,
} from "../services/notificationService";

const navigate = vi.hoisted(() => vi.fn());

vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}));

vi.mock("../services/notificationService", () => ({
  getUnreadCount: vi.fn(),
  listNotifications: vi.fn(),
  markNotificationRead: vi.fn(async () => undefined),
  markAllNotificationsRead: vi.fn(async () => undefined),
  getNotificationPreferences: vi.fn(),
  updateNotificationPreferences: vi.fn(async () => undefined),
}));

const NOTE: AppNotification = {
  id: "n1",
  type: "LEGAL_HOLD_APPLIED",
  title: "Legal hold applied",
  body: "3 documents",
  link: "/workspace/acme?section=settings&tab=legal-holds",
  read: false,
  createdAt: "2026-01-01T00:00:00Z",
};

function setUp(items: AppNotification[] = [NOTE], unread = 1) {
  vi.mocked(getUnreadCount).mockResolvedValue(unread);
  vi.mocked(listNotifications).mockResolvedValue(items);
  render(<MemoryRouter><NotificationBell token="t" /></MemoryRouter>);
}

describe("NotificationBell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(cleanup);

  it("shows how many are unread", async () => {
    setUp([NOTE], 3);

    expect(await screen.findByRole("button", { name: "Notifications, 3 unread" })).toBeTruthy();
  });

  it("marks a notification read and follows its link", async () => {
    setUp();

    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 1 unread" }));
    fireEvent.click(await screen.findByText("Legal hold applied"));

    expect(markNotificationRead).toHaveBeenCalledWith("n1", "t");
    expect(navigate).toHaveBeenCalledWith("/workspace/acme?section=settings&tab=legal-holds");
    await screen.findByRole("button", { name: "Notifications" });
  });

  it("won't navigate anywhere outside the app", async () => {
    setUp([{ ...NOTE, link: "https://evil.example" }]);

    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 1 unread" }));
    fireEvent.click(await screen.findByText("Legal hold applied"));

    expect(navigate).not.toHaveBeenCalled();
  });

  it("marks everything read", async () => {
    setUp();

    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 1 unread" }));
    fireEvent.click(await screen.findByRole("button", { name: "Mark all read" }));

    await waitFor(() => expect(markAllNotificationsRead).toHaveBeenCalledWith("t"));
    expect(await screen.findByRole("button", { name: "Notifications" })).toBeTruthy();
  });

  it("changes preferences, and puts them back if saving fails", async () => {
    setUp();
    vi.mocked(getNotificationPreferences).mockResolvedValue({ inApp: true, email: true, dailyDigest: false });

    fireEvent.click(await screen.findByRole("button", { name: "Notifications, 1 unread" }));
    fireEvent.click(screen.getByRole("button", { name: "Notification preferences" }));

    const digest = (await screen.findByLabelText(/Daily digest/)) as HTMLInputElement;
    fireEvent.click(digest);
    await waitFor(() => expect(updateNotificationPreferences).toHaveBeenCalledWith({ inApp: true, email: true, dailyDigest: true }, "t"));

    vi.mocked(updateNotificationPreferences).mockRejectedValueOnce(new Error("nope"));
    const email = screen.getByLabelText(/By email/) as HTMLInputElement;
    fireEvent.click(email);
    await waitFor(() => expect(email.checked).toBe(true));
  });

  it("stays quiet when the count can't be loaded", async () => {
    vi.mocked(getUnreadCount).mockRejectedValue(new Error("offline"));
    vi.mocked(listNotifications).mockResolvedValue([]);
    render(<MemoryRouter><NotificationBell token="t" /></MemoryRouter>);

    expect(await screen.findByRole("button", { name: "Notifications" })).toBeTruthy();
  });
});
