import type { Task, TaskStatus } from "@/lib/api/types";
import {
  formatDueDate,
  isOverdue,
  priorityLabel,
  priorityTone,
  recurrenceLabel,
  statusLabel,
  statusTone,
} from "@/lib/tasks";
import { StatusBadge } from "@/components/ui/status-badge";

export function TaskWorkCard({
  task,
  canUpdate,
  onOpen,
  onStatusChange,
}: {
  task: Task;
  canUpdate: boolean;
  onOpen?: (task: Task) => void;
  onStatusChange: (task: Task, status: TaskStatus) => void;
}) {
  const overdue = isOverdue(task.dueDate, task.status);

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
      {onOpen ? (
        <button
          type="button"
          className="text-left text-sm font-semibold text-blue-800 underline-offset-2 hover:underline"
          onClick={() => onOpen(task)}
        >
          {task.title}
        </button>
      ) : (
        <h3 className="text-sm font-semibold text-slate-950">{task.title}</h3>
      )}
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <StatusBadge tone={statusTone(task.status)}>{statusLabel(task.status)}</StatusBadge>
        <StatusBadge tone={priorityTone(task.priority)}>{priorityLabel(task.priority)}</StatusBadge>
        {task.recurrence && task.recurrence !== "NONE" ? (
          <StatusBadge tone="info">{recurrenceLabel(task.recurrence)}</StatusBadge>
        ) : null}
      </div>
      <p className={`mt-2 text-sm ${overdue ? "font-medium text-red-800" : "text-slate-700"}`}>
        {formatDueDate(task.dueDate)}
      </p>
      <p className="mt-1 text-sm text-slate-600">{task.assigneeName ?? "Unassigned"}</p>
      {canUpdate ? (
        <label className="mt-3 block text-sm text-slate-700">
          <span className="sr-only">Status for {task.title}</span>
          <select
            aria-label={`Status for ${task.title}`}
            className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm"
            value={task.status}
            onChange={(event) => onStatusChange(task, event.target.value as TaskStatus)}
          >
            {task.permittedStatuses.map((status) => (
              <option key={status} value={status}>
                {statusLabel(status)}
              </option>
            ))}
          </select>
        </label>
      ) : null}
    </article>
  );
}
