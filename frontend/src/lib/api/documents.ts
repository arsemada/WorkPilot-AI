import { apiDownload, apiFormRequest, apiRequest } from "@/lib/api/client";
import type { DocumentListQuery, DocumentPage, OrganizationDocument } from "@/lib/api/types";

function queryString(query: DocumentListQuery) {
  const params = new URLSearchParams();
  if (query.q) params.set("q", query.q);
  params.set("page", String(query.page ?? 0));
  params.set("size", String(query.size ?? 10));
  params.set("sort", query.sort ?? "createdAt");
  params.set("direction", query.direction ?? "desc");
  return params.toString();
}

export function listDocuments(organizationId: string, query: DocumentListQuery) {
  return apiRequest<DocumentPage>(`/api/organizations/${organizationId}/documents?${queryString(query)}`, {
    session: "required",
  });
}

export function uploadDocument(organizationId: string, input: { title: string; description: string; file: File }) {
  const body = new FormData();
  body.set("title", input.title);
  if (input.description.trim()) {
    body.set("description", input.description.trim());
  }
  body.set("file", input.file);
  return apiFormRequest<OrganizationDocument>(`/api/organizations/${organizationId}/documents`, body);
}

export function updateDocument(
  organizationId: string,
  documentId: string,
  body: { title?: string; description?: string | null },
) {
  return apiRequest<OrganizationDocument>(`/api/organizations/${organizationId}/documents/${documentId}`, {
    method: "PATCH",
    body,
    session: "required",
  });
}

export function deleteDocument(organizationId: string, documentId: string) {
  return apiRequest<void>(`/api/organizations/${organizationId}/documents/${documentId}`, {
    method: "DELETE",
    session: "required",
  });
}

export function retryDocument(organizationId: string, documentId: string) {
  return apiRequest<OrganizationDocument>(`/api/organizations/${organizationId}/documents/${documentId}/retry`, {
    method: "POST",
    session: "required",
  });
}

export async function downloadDocument(organizationId: string, item: OrganizationDocument) {
  const blob = await apiDownload(`/api/organizations/${organizationId}/documents/${item.id}/content`);
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = item.originalFileName;
  anchor.click();
  URL.revokeObjectURL(url);
}
