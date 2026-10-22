import { apiRequest } from "@/lib/api/client";
import type { AppNotification, UnreadCount } from "@/lib/api/types";

export function listNotifications() {
  return apiRequest<AppNotification[]>("/api/notifications", { session: "required" });
}

export function unreadNotificationCount() {
  return apiRequest<UnreadCount>("/api/notifications/unread-count", { session: "required" });
}

export function markNotificationRead(notificationId: string) {
  return apiRequest<AppNotification>(`/api/notifications/${notificationId}/read`, {
    method: "POST",
    body: {},
    session: "required",
  });
}

export function markAllNotificationsRead() {
  return apiRequest<UnreadCount>("/api/notifications/read-all", {
    method: "POST",
    body: {},
    session: "required",
  });
}
