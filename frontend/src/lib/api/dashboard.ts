import { apiRequest } from "@/lib/api/client";
import type { ActivityEvent, DashboardSummary, DashboardTrend } from "@/lib/api/types";

export function getDashboardSummary(organizationId: string) {
  return apiRequest<DashboardSummary>(`/api/organizations/${organizationId}/dashboard`, {
    session: "required",
  });
}

export function getDashboardTrends(organizationId: string, from: string, to: string) {
  const params = new URLSearchParams({ from, to });
  return apiRequest<DashboardTrend>(
    `/api/organizations/${organizationId}/dashboard/trends?${params.toString()}`,
    { session: "required" },
  );
}

export function getDashboardActivity(organizationId: string) {
  return apiRequest<ActivityEvent[]>(`/api/organizations/${organizationId}/dashboard/activity`, {
    session: "required",
  });
}
