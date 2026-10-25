"use client";

import { useCallback, useEffect, useMemo, useState, useSyncExternalStore, type FormEvent } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import { ApiError } from "@/lib/api/client";
import { createTask, deleteTask, listMembers, listTasks, updateTask } from "@/lib/api/tasks";
import type { Membership, Permission, Task, TaskPage, TaskPriority, TaskStatus } from "@/lib/api/types";
import {
  TASK_PRIORITIES,
  TASK_STATUSES,
  formatDueDate,
  isOverdue,
  priorityLabel,
  priorityTone,
  recurrenceLabel,
  statusLabel,
  statusTone,
} from "@/lib/tasks";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { FormBanner, SelectField, TextField } from "@/components/ui/field";
import { Spinner } from "@/components/ui/loading";
import { StatusBadge } from "@/components/ui/status-badge";
import { DataTable } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { TaskForm, emptyTaskForm, type TaskFormValues } from "@/components/tasks/task-form";
import { TaskNotesPanel } from "@/components/tasks/task-notes-panel";
import {
  readSelectedOrganization,
  subscribeSelectedOrganization,
  writeSelectedOrganization,
} from "@/lib/selected-organization";

const PAGE_SIZE = 10;

type Filters = {
  q: string;
  status: "" | TaskStatus;
  priority: "" | TaskPriority;
  assignee: string;
  dueAfter: string;
  dueBefore: string;
};

const emptyFilters: Filters = {
  q: "",
  status: "",
  priority: "",
  assignee: "",
  dueAfter: "",
  dueBefore: "",
};

function hasPermission(membership: Membership | undefined, permission: Permission) {
  return membership?.permissions.includes(permission) ?? false;
}

export function TasksWorkspace() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const memberships = useMemo(
    () => [...(user?.memberships ?? [])].sort((a, b) => a.organizationName.localeCompare(b.organizationName)),
    [user],
  );
  const [chosenOrganizationId, setChosenOrganizationId] = useState<string | null>(null);
  const storedOrganizationId = useSyncExternalStore(
    subscribeSelectedOrganization,
    readSelectedOrganization,
    () => null,
  );
  const organizationId = useMemo(() => {
    const preferred = chosenOrganizationId ?? storedOrganizationId;
    const match = memberships.find((membership) => membership.organizationId === preferred);
    return (match ?? memberships[0])?.organizationId ?? "";
  }, [memberships, chosenOrganizationId, storedOrganizationId]);
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [searchInput, setSearchInput] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<TaskPage | null>(null);
  const [members, setMembers] = useState<Membership[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editor, setEditor] = useState<"create" | "edit" | null>(null);
  const [selected, setSelected] = useState<Task | null>(null);
  const [form, setForm] = useState<TaskFormValues>(emptyTaskForm);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Task | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      setFilters((current) => (current.q === searchInput ? current : { ...current, q: searchInput }));
      setPage(0);
    }, 300);
    return () => window.clearTimeout(handle);
  }, [searchInput]);

  const membership = memberships.find((item) => item.organizationId === organizationId);
  const canCreate = hasPermission(membership, "RESOURCE_CREATE");
  const canUpdate = hasPermission(membership, "RESOURCE_UPDATE");
  const canDelete = hasPermission(membership, "RESOURCE_DELETE");

  const load = useCallback(async () => {
    if (!organizationId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [tasks, people] = await Promise.all([
        listTasks(organizationId, {
          q: filters.q,
          status: filters.status,
          priority: filters.priority,
          assigneeId: filters.assignee.startsWith("user:") ? filters.assignee.slice(5) : undefined,
          unassigned: filters.assignee === "unassigned",
          dueAfter: filters.dueAfter,
          dueBefore: filters.dueBefore,
          page,
          size: PAGE_SIZE,
          sort: "createdAt",
          direction: "desc",
        }),
        listMembers(organizationId),
      ]);
      setResult(tasks);
      setMembers(people);
      if (tasks.totalPages > 0 && page > tasks.totalPages - 1) {
        setPage(tasks.totalPages - 1);
      }
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Tasks could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [organizationId, filters, page]);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      void load();
    }, 0);
    return () => window.clearTimeout(handle);
  }, [load]);

  function chooseOrganization(nextId: string) {
    writeSelectedOrganization(nextId);
    setChosenOrganizationId(nextId);
    setPage(0);
    setSelected(null);
  }

  function openCreate() {
    setSelected(null);
    setForm(emptyTaskForm);
    setFieldErrors({});
    setFormError(null);
    setEditor("create");
  }

  function openTask(task: Task) {
    setSelected(task);
    setForm({
      title: task.title,
      description: task.description ?? "",
      status: task.status,
      priority: task.priority,
      dueDate: task.dueDate ?? "",
      assigneeId: task.assigneeId ?? "",
      recurrence: task.recurrence ?? "NONE",
    });
    setFieldErrors({});
    setFormError(null);
    setEditor(null);
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!form.title.trim()) {
      nextErrors.title = "Enter a title.";
    }
    setFieldErrors(nextErrors);
    setFormError(null);
    if (Object.keys(nextErrors).length > 0 || !organizationId) {
      return;
    }

    const body = {
      title: form.title.trim(),
      description: form.description.trim() ? form.description.trim() : null,
      status: form.status,
      priority: form.priority,
      dueDate: form.dueDate || null,
      assigneeId: form.assigneeId || null,
      recurrence: form.recurrence,
    };

    setSubmitting(true);
    try {
      if (editor === "edit" && selected) {
        const updated = await updateTask(organizationId, selected.id, body);
        setSelected(updated);
        showToast({ tone: "success", message: "Task updated." });
      } else {
        await createTask(organizationId, body);
        showToast({ tone: "success", message: "Task created." });
        setPage(0);
      }
      setEditor(null);
      await load();
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFieldErrors(caught.details ?? {});
        setFormError(caught.message);
      } else {
        setFormError("The task could not be saved.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function changeStatus(task: Task, status: TaskStatus) {
    if (!organizationId || status === task.status) {
      return;
    }
    try {
      await updateTask(organizationId, task.id, { status });
      showToast({ tone: "success", message: "Status updated." });
      await load();
    } catch (caught) {
      showToast({
        tone: "error",
        message: caught instanceof ApiError ? caught.message : "Status could not be changed.",
      });
    }
  }

  async function confirmDelete() {
    if (!pendingDelete || !organizationId) {
      return;
    }
    setDeleting(true);
    try {
      await deleteTask(organizationId, pendingDelete.id);
      showToast({ tone: "success", message: "Task deleted." });
      if (selected?.id === pendingDelete.id) {
        setSelected(null);
      }
      setPendingDelete(null);
      await load();
    } catch (caught) {
      showToast({
        tone: "error",
        message: caught instanceof ApiError ? caught.message : "The task could not be deleted.",
      });
    } finally {
      setDeleting(false);
    }
  }

  if (memberships.length === 0) {
    return (
      <section className="mx-auto max-w-3xl">
        <h1 className="page-title">Tasks</h1>
        <p className="mt-4 text-base leading-7 text-slate-700">
          You are not a member of an organization yet. Register with an organization name, or ask an
          owner to add your account.
        </p>
      </section>
    );
  }

  const tasks = result?.items ?? [];
  const filtersActive = Boolean(
    filters.q || filters.status || filters.priority || filters.assignee || filters.dueAfter || filters.dueBefore,
  );

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="page-title">Tasks</h1>
          <p className="mt-1 text-sm text-slate-700">Work assigned inside {membership?.organizationName}.</p>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          {memberships.length > 1 ? (
            <SelectField
              id="task-organization"
              label="Organization"
              value={organizationId}
              onChange={(event) => chooseOrganization(event.target.value)}
            >
              {memberships.map((item) => (
                <option key={item.organizationId} value={item.organizationId}>
                  {item.organizationName}
                </option>
              ))}
            </SelectField>
          ) : null}
          {canCreate ? <Button onClick={openCreate}>New task</Button> : null}
        </div>
      </div>

      <div className="panel grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-3">
        <TextField
          id="task-search"
          label="Search"
          value={searchInput}
          placeholder="Title or description"
          onChange={(event) => setSearchInput(event.target.value)}
        />
        <SelectField
          id="task-status-filter"
          label="Status"
          value={filters.status}
          onChange={(event) => {
            setFilters((current) => ({ ...current, status: event.target.value as Filters["status"] }));
            setPage(0);
          }}
        >
          <option value="">All statuses</option>
          {TASK_STATUSES.map((status) => (
            <option key={status} value={status}>
              {statusLabel(status)}
            </option>
          ))}
        </SelectField>
        <SelectField
          id="task-priority-filter"
          label="Priority"
          value={filters.priority}
          onChange={(event) => {
            setFilters((current) => ({ ...current, priority: event.target.value as Filters["priority"] }));
            setPage(0);
          }}
        >
          <option value="">All priorities</option>
          {TASK_PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {priorityLabel(priority)}
            </option>
          ))}
        </SelectField>
        <SelectField
          id="task-assignee-filter"
          label="Assignee"
          value={filters.assignee}
          onChange={(event) => {
            setFilters((current) => ({ ...current, assignee: event.target.value }));
            setPage(0);
          }}
        >
          <option value="">Anyone</option>
          <option value="unassigned">Unassigned</option>
          {members.map((member) => (
            <option key={member.userId} value={`user:${member.userId}`}>
              {member.userFullName}
            </option>
          ))}
        </SelectField>
        <TextField
          id="task-due-after"
          label="Due on or after"
          type="date"
          value={filters.dueAfter}
          onChange={(event) => {
            setFilters((current) => ({ ...current, dueAfter: event.target.value }));
            setPage(0);
          }}
        />
        <TextField
          id="task-due-before"
          label="Due on or before"
          type="date"
          value={filters.dueBefore}
          onChange={(event) => {
            setFilters((current) => ({ ...current, dueBefore: event.target.value }));
            setPage(0);
          }}
        />
      </div>

      {error ? <FormBanner tone="error">{error}</FormBanner> : null}

      {loading && !result ? (
        <div className="flex items-center gap-3 text-slate-800" role="status">
          <Spinner className="text-blue-700" />
          <span className="text-sm font-medium">Loading tasks</span>
        </div>
      ) : (
        <DataTable
          caption="Tasks in the selected organization"
          rows={tasks}
          getRowKey={(task) => task.id}
          emptyTitle={filtersActive ? "No matching tasks" : "No tasks yet"}
          emptyDescription={
            filtersActive
              ? "Change the search or filters to see more tasks."
              : "Create a task to start tracking work for this organization."
          }
          columns={[
            {
              id: "title",
              header: "Task",
              cell: (task) => (
                <button type="button" className="text-left font-semibold text-blue-800 underline-offset-2 hover:underline" onClick={() => openTask(task)}>
                  {task.title}
                </button>
              ),
            },
            {
              id: "status",
              header: "Status",
              cell: (task) => (
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge tone={statusTone(task.status)}>{statusLabel(task.status)}</StatusBadge>
                  {canUpdate ? (
                    <select
                      id={`status-${task.id}`}
                      aria-label={`Status for ${task.title}`}
                      className="rounded-lg border border-slate-300 bg-white px-2 py-1 text-sm"
                      value={task.status}
                      onChange={(event) => void changeStatus(task, event.target.value as TaskStatus)}
                    >
                      {task.permittedStatuses.map((status) => (
                        <option key={status} value={status}>
                          {statusLabel(status)}
                        </option>
                      ))}
                    </select>
                  ) : null}
                </div>
              ),
            },
            {
              id: "priority",
              header: "Priority",
              cell: (task) => <StatusBadge tone={priorityTone(task.priority)}>{priorityLabel(task.priority)}</StatusBadge>,
            },
            {
              id: "due",
              header: "Due",
              cell: (task) => (
                <span className={isOverdue(task.dueDate, task.status) ? "font-medium text-red-800" : "text-slate-800"}>
                  {formatDueDate(task.dueDate)}
                  {task.recurrence && task.recurrence !== "NONE" ? ` · ${recurrenceLabel(task.recurrence)}` : ""}
                </span>
              ),
            },
            {
              id: "assignee",
              header: "Assignee",
              cell: (task) => task.assigneeName ?? "Unassigned",
            },
          ]}
        />
      )}

      {result && result.totalElements > 0 ? (
        <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-slate-700">
          <p>
            Showing {result.page * result.size + 1}–{Math.min(result.totalElements, result.page * result.size + tasks.length)} of{" "}
            {result.totalElements}
            {loading ? " · Updating" : ""}
          </p>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
              Previous
            </Button>
            <Button
              variant="secondary"
              disabled={result.totalPages === 0 || page >= result.totalPages - 1}
              onClick={() => setPage((current) => current + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      ) : null}

      <Dialog
        open={editor !== null}
        title={editor === "edit" ? "Edit task" : "New task"}
        description={editor === "edit" ? "Save changes for this organization." : "Add a task for this organization."}
        onClose={() => setEditor(null)}
      >
        <TaskForm
          values={form}
          statuses={editor === "edit" && selected ? selected.permittedStatuses : TASK_STATUSES}
          members={members}
          fieldErrors={fieldErrors}
          formError={formError}
          submitting={submitting}
          submitLabel={editor === "edit" ? "Save changes" : "Create task"}
          onChange={setForm}
          onSubmit={onSubmit}
          onCancel={() => setEditor(null)}
        />
      </Dialog>

      <Dialog
        open={selected !== null && editor === null}
        title={selected?.title ?? "Task"}
        className="max-w-xl"
        onClose={() => setSelected(null)}
      >
        {selected ? (
          <div className="space-y-4 text-sm text-slate-800">
            <div className="flex flex-wrap gap-2">
              <StatusBadge tone={statusTone(selected.status)}>{statusLabel(selected.status)}</StatusBadge>
              <StatusBadge tone={priorityTone(selected.priority)}>{priorityLabel(selected.priority)}</StatusBadge>
              {selected.recurrence && selected.recurrence !== "NONE" ? (
                <StatusBadge tone="info">{recurrenceLabel(selected.recurrence)}</StatusBadge>
              ) : null}
            </div>
            <p>{selected.description || "No description."}</p>
            <dl className="grid gap-2 sm:grid-cols-2">
              <div>
                <dt className="font-medium text-slate-950">Due</dt>
                <dd className={isOverdue(selected.dueDate, selected.status) ? "text-red-800" : ""}>
                  {formatDueDate(selected.dueDate)}
                </dd>
              </div>
              <div>
                <dt className="font-medium text-slate-950">Repeats</dt>
                <dd>{recurrenceLabel(selected.recurrence ?? "NONE")}</dd>
              </div>
              <div>
                <dt className="font-medium text-slate-950">Assignee</dt>
                <dd>{selected.assigneeName ?? "Unassigned"}</dd>
              </div>
              <div>
                <dt className="font-medium text-slate-950">Created by</dt>
                <dd>{selected.createdByName}</dd>
              </div>
            </dl>
            {user ? (
              <TaskNotesPanel
                organizationId={organizationId}
                task={selected}
                currentUserId={user.id}
                canUpdate={canUpdate}
                canDelete={canDelete}
              />
            ) : null}
            <div className="flex flex-wrap justify-end gap-2">
              {canDelete ? (
                <Button variant="danger" onClick={() => setPendingDelete(selected)}>
                  Delete
                </Button>
              ) : null}
              {canUpdate ? (
                <Button
                  onClick={() => {
                    setEditor("edit");
                  }}
                >
                  Edit
                </Button>
              ) : null}
              <Button variant="secondary" onClick={() => setSelected(null)}>
                Close
              </Button>
            </div>
          </div>
        ) : null}
      </Dialog>

      <Dialog
        open={pendingDelete !== null}
        title="Delete task"
        description={pendingDelete ? `Delete “${pendingDelete.title}”? This cannot be undone.` : undefined}
        onClose={() => setPendingDelete(null)}
      >
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setPendingDelete(null)}>
            Cancel
          </Button>
          <Button variant="danger" loading={deleting} onClick={() => void confirmDelete()}>
            Delete task
          </Button>
        </div>
      </Dialog>
    </div>
  );
}
