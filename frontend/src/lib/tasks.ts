import type { TaskPriority, TaskStatus } from "@/lib/api/types";
import type { Tone } from "@/components/ui/status-badge";

export const TASK_STATUSES: TaskStatus[] = ["TODO", "IN_PROGRESS", "DONE", "CANCELLED"];
export const TASK_PRIORITIES: TaskPriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const statusLabels: Record<TaskStatus, string> = {
  TODO: "To do",
  IN_PROGRESS: "In progress",
  DONE: "Done",
  CANCELLED: "Cancelled",
};

const priorityLabels: Record<TaskPriority, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  URGENT: "Urgent",
};

export function statusLabel(status: TaskStatus) {
  return statusLabels[status];
}

export function priorityLabel(priority: TaskPriority) {
  return priorityLabels[priority];
}

export function statusTone(status: TaskStatus): Tone {
  if (status === "IN_PROGRESS") return "info";
  if (status === "DONE") return "success";
  if (status === "CANCELLED") return "neutral";
  return "planned";
}

export function priorityTone(priority: TaskPriority): Tone {
  if (priority === "URGENT") return "danger";
  if (priority === "HIGH") return "warning";
  if (priority === "MEDIUM") return "info";
  return "neutral";
}

export function formatDueDate(value: string | null) {
  if (!value) {
    return "No due date";
  }
  const [year, month, day] = value.split("-").map(Number);
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, month - 1, day)));
}

export function isOverdue(dueDate: string | null, status: TaskStatus) {
  if (!dueDate || status === "DONE" || status === "CANCELLED") {
    return false;
  }
  const today = new Date();
  const iso = today.toISOString().slice(0, 10);
  return dueDate < iso;
}
