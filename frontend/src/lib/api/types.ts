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

export type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE" | "CANCELLED";

export type TaskPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export type Task = {
  id: string;
  organizationId: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  createdById: string;
  createdByName: string;
  assigneeId: string | null;
  assigneeName: string | null;
  createdAt: string;
  updatedAt: string;
  permittedStatuses: TaskStatus[];
};

export type TaskPage = {
  items: Task[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type TaskWriteRequest = {
  title: string;
  description?: string | null;
  status?: TaskStatus;
  priority?: TaskPriority;
  dueDate?: string | null;
  assigneeId?: string | null;
};

export type OrganizationDocument = {
  id: string;
  organizationId: string;
  title: string;
  description: string | null;
  originalFileName: string;
  contentType: string;
  sizeBytes: number;
  textIndexed: boolean;
  textPreview: string | null;
  uploadedById: string;
  uploadedByName: string;
  createdAt: string;
  updatedAt: string;
};

export type DocumentPage = {
  items: OrganizationDocument[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type DocumentListQuery = {
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: "asc" | "desc";
};

export type TaskListQuery = {
  status?: TaskStatus | "";
  priority?: TaskPriority | "";
  assigneeId?: string;
  unassigned?: boolean;
  dueAfter?: string;
  dueBefore?: string;
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: "asc" | "desc";
};

export type ActivityType =
  | "TASK_CREATED"
  | "TASK_UPDATED"
  | "TASK_STATUS_CHANGED"
  | "TASK_COMPLETED"
  | "TASK_DELETED";

export type DashboardSummary = {
  organizationId: string;
  organizationName: string;
  memberCount: number;
  totalTasks: number;
  todoTasks: number;
  inProgressTasks: number;
  completedTasks: number;
  cancelledTasks: number;
  overdueTasks: number;
  recentTasks: Task[];
};

export type DashboardTrendPoint = {
  date: string;
  completedCount: number;
};

export type DashboardTrend = {
  from: string;
  to: string;
  points: DashboardTrendPoint[];
};

export type ActivityEvent = {
  id: string;
  type: ActivityType;
  summary: string;
  taskId: string | null;
  actorId: string;
  actorName: string;
  occurredAt: string;
};
