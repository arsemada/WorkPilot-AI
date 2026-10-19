export type OrganizationRole = "OWNER" | "ADMIN" | "MEMBER";

export type Permission =
  | "ORG_READ"
  | "ORG_UPDATE"
  | "ORG_DELETE"
  | "ORG_TRANSFER"
  | "MEMBER_READ"
  | "MEMBER_INVITE"
  | "MEMBER_UPDATE"
  | "MEMBER_REMOVE"
  | "RESOURCE_CREATE"
  | "RESOURCE_READ"
  | "RESOURCE_UPDATE"
  | "RESOURCE_DELETE";

export type Membership = {
  id: string;
  organizationId: string;
  organizationName: string;
  organizationSlug: string;
  userId: string;
  userEmail: string;
  userFullName: string;
  role: OrganizationRole;
  permissions: Permission[];
  createdAt: string;
};

export type UserProfile = {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  memberships: Membership[];
};

export type RegisterRequest = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  organizationName?: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type CsrfResponse = {
  token: string;
  headerName: string;
  parameterName: string;
};

export type ErrorBody = {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  details?: Record<string, string>;
  path?: string;
};

export type MessageResponse = {
  message: string;
};
