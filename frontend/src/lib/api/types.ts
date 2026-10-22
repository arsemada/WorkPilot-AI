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
  storageKey: string;
  processingStatus: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
  processingError: string | null;
  indexingStatus: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "SKIPPED" | "STALE";
  indexingError: string | null;
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
  | "TASK_DELETED"
  | "WORKFLOW_SUCCEEDED"
  | "WORKFLOW_FAILED";

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

export type WorkflowEventType =
  | "TASK_ASSIGNED"
  | "TASK_OVERDUE"
  | "DOCUMENT_UPLOADED"
  | "DOCUMENT_PROCESSED"
  | "SCHEDULED_ACTIVITY";

export type WorkflowActionType =
  | "NOTIFY_ASSIGNEE"
  | "NOTIFY_RELEVANT_USERS"
  | "PROCESS_DOCUMENT"
  | "SUMMARIZE_DOCUMENT"
  | "SUMMARIZE_ACTIVITY";

export type WorkflowCondition = "ALWAYS" | "HAS_ASSIGNEE" | "TEXT_EXTRACTED";

export type WorkflowExecutionStatus = "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";

export type WorkflowRule = {
  id: string;
  name: string;
  eventType: WorkflowEventType;
  conditionType: WorkflowCondition;
  actionType: WorkflowActionType;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type WorkflowExecution = {
  id: string;
  ruleId: string;
  ruleName: string;
  eventType: WorkflowEventType;
  status: WorkflowExecutionStatus;
  attemptCount: number;
  errorMessage: string | null;
  resultMessage: string | null;
  createdAt: string;
  finishedAt: string | null;
};

export type AppNotification = {
  id: string;
  organizationId: string;
  organizationName: string;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
};

export type UnreadCount = {
  count: number;
};

export type MessageRole = "USER" | "ASSISTANT";

export type Conversation = {
  id: string;
  organizationId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
};

export type MessageSource = {
  documentId: string | null;
  documentTitle: string;
  chunkIndex: number;
  pageNumber: number | null;
  sectionLabel: string | null;
  snippet: string;
};

export type ToolCallStatus = "PROPOSED" | "EXECUTING" | "COMPLETED" | "REJECTED" | "FAILED";

export type AssistantToolCall = {
  id: string;
  name: string;
  status: ToolCallStatus;
  arguments: {
    title?: string;
    description?: string | null;
    priority?: string | null;
    dueDate?: string | null;
    overdue?: boolean;
    mine?: boolean;
    query?: string | null;
    status?: string | null;
  } | null;
  result: {
    task?: { id: string; title: string; status: string; priority: string; dueDate: string | null };
    tasks?: Array<{ id: string; title: string; status: string; priority: string; dueDate: string | null }>;
  } | null;
  errorMessage: string | null;
};

export type ConversationMessage = {
  id: string;
  role: MessageRole;
  content: string;
  documentSupport: boolean | null;
  sources: MessageSource[];
  toolCalls: AssistantToolCall[];
  createdAt: string;
};

export type ConversationDetail = Conversation & {
  messages: ConversationMessage[];
};
