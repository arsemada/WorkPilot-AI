import { apiRequest } from "@/lib/api/client";
import type { Membership, OrganizationRole } from "@/lib/api/types";

export type OrganizationRecord = {
  id: string;
  name: string;
  slug: string;
};

export function updateOrganization(organizationId: string, name: string) {
  return apiRequest<OrganizationRecord>(`/api/organizations/${organizationId}`, {
    method: "PATCH",
    body: { name },
    session: "required",
  });
}

export function addMember(organizationId: string, email: string, role: OrganizationRole) {
  return apiRequest<Membership>(`/api/organizations/${organizationId}/members`, {
    method: "POST",
    body: { email, role },
    session: "required",
  });
}

export function updateMemberRole(organizationId: string, userId: string, role: OrganizationRole) {
  return apiRequest<Membership>(`/api/organizations/${organizationId}/members/${userId}`, {
    method: "PATCH",
    body: { role },
    session: "required",
  });
}

export function removeMember(organizationId: string, userId: string) {
  return apiRequest<void>(`/api/organizations/${organizationId}/members/${userId}`, {
    method: "DELETE",
    session: "required",
  });
}
