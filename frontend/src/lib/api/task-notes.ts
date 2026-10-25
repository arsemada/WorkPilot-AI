import { apiRequest } from "@/lib/api/client";
import type { TaskComment, TaskDocumentLink } from "@/lib/api/types";

export function listTaskComments(organizationId: string, taskId: string) {
  return apiRequest<TaskComment[]>(`/api/organizations/${organizationId}/tasks/${taskId}/comments`, {
    session: "required",
  });
}

export function addTaskComment(organizationId: string, taskId: string, body: string) {
  return apiRequest<TaskComment>(`/api/organizations/${organizationId}/tasks/${taskId}/comments`, {
    method: "POST",
    body: { body },
    session: "required",
  });
}

export function deleteTaskComment(organizationId: string, taskId: string, commentId: string) {
  return apiRequest<void>(`/api/organizations/${organizationId}/tasks/${taskId}/comments/${commentId}`, {
    method: "DELETE",
    session: "required",
  });
}

export function listTaskDocuments(organizationId: string, taskId: string) {
  return apiRequest<TaskDocumentLink[]>(`/api/organizations/${organizationId}/tasks/${taskId}/documents`, {
    session: "required",
  });
}

export function linkTaskDocument(organizationId: string, taskId: string, documentId: string) {
  return apiRequest<TaskDocumentLink>(`/api/organizations/${organizationId}/tasks/${taskId}/documents`, {
    method: "POST",
    body: { documentId },
    session: "required",
  });
}

export function unlinkTaskDocument(organizationId: string, taskId: string, documentId: string) {
  return apiRequest<void>(`/api/organizations/${organizationId}/tasks/${taskId}/documents/${documentId}`, {
    method: "DELETE",
    session: "required",
  });
}
