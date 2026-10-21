import { apiRequest } from "@/lib/api/client";
import type { Membership, Task, TaskListQuery, TaskPage, TaskWriteRequest } from "@/lib/api/types";

function queryString(query: TaskListQuery) {
  const params = new URLSearchParams();
  if (query.status) params.set("status", query.status);
  if (query.priority) params.set("priority", query.priority);
  if (query.unassigned) params.set("unassigned", "true");
  else if (query.assigneeId) params.set("assigneeId", query.assigneeId);
  if (query.dueAfter) params.set("dueAfter", query.dueAfter);
  if (query.dueBefore) params.set("dueBefore", query.dueBefore);
  if (query.q) params.set("q", query.q);
  params.set("page", String(query.page ?? 0));
  params.set("size", String(query.size ?? 10));
  params.set("sort", query.sort ?? "createdAt");
  params.set("direction", query.direction ?? "desc");
  return params.toString();
}

export function listTasks(organizationId: string, query: TaskListQuery) {
  return apiRequest<TaskPage>(`/api/organizations/${organizationId}/tasks?${queryString(query)}`, {
    session: "required",
  });
}

export function createTask(organizationId: string, body: TaskWriteRequest) {
  return apiRequest<Task>(`/api/organizations/${organizationId}/tasks`, {
    method: "POST",
    body,
    session: "required",
  });
}

export function updateTask(organizationId: string, taskId: string, body: Partial<TaskWriteRequest>) {
  return apiRequest<Task>(`/api/organizations/${organizationId}/tasks/${taskId}`, {
    method: "PATCH",
    body,
    session: "required",
  });
}

export function deleteTask(organizationId: string, taskId: string) {
  return apiRequest<void>(`/api/organizations/${organizationId}/tasks/${taskId}`, {
    method: "DELETE",
    session: "required",
  });
}

export function listMembers(organizationId: string) {
  return apiRequest<Membership[]>(`/api/organizations/${organizationId}/members`, {
    session: "required",
  });
}
