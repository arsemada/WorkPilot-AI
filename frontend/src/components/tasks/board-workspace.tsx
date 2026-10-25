"use client";

import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import { ApiError } from "@/lib/api/client";
import { fetchTaskBoard, updateTask } from "@/lib/api/tasks";
import type { Membership, Permission, Task, TaskBoard, TaskStatus } from "@/lib/api/types";
import { statusLabel } from "@/lib/tasks";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { FormBanner, SelectField } from "@/components/ui/field";
import { Spinner } from "@/components/ui/loading";
import { useToast } from "@/components/ui/toast";
import { TaskNotesPanel } from "@/components/tasks/task-notes-panel";
import { TaskWorkCard } from "@/components/tasks/task-work-card";
import {
  readSelectedOrganization,
  subscribeSelectedOrganization,
  writeSelectedOrganization,
} from "@/lib/selected-organization";

function hasPermission(membership: Membership | undefined, permission: Permission) {
  return membership?.permissions.includes(permission) ?? false;
}

export function BoardWorkspace() {
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
  const [result, setResult] = useState<TaskBoard | null>(null);
  const [selected, setSelected] = useState<Task | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const membership = memberships.find((item) => item.organizationId === organizationId);
  const canUpdate = hasPermission(membership, "RESOURCE_UPDATE");
  const canDelete = hasPermission(membership, "RESOURCE_DELETE");

  const load = useCallback(async () => {
    if (!organizationId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setResult(await fetchTaskBoard(organizationId));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "The board could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [organizationId]);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      void load();
    }, 0);
    return () => window.clearTimeout(handle);
  }, [load]);

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

  if (memberships.length === 0) {
    return (
      <section className="mx-auto max-w-3xl">
        <h1 className="page-title">Board</h1>
        <p className="mt-4 text-base leading-7 text-slate-700">
          You are not a member of an organization yet. Register with an organization name, or ask an
          owner to add your account.
        </p>
      </section>
    );
  }

  const columns = result
    ? [
        { id: "todo", title: statusLabel("TODO"), tasks: result.todo, empty: "No tasks to do." },
        { id: "in-progress", title: statusLabel("IN_PROGRESS"), tasks: result.inProgress, empty: "Nothing in progress." },
        { id: "done", title: statusLabel("DONE"), tasks: result.done, empty: "No completed tasks yet." },
        { id: "cancelled", title: statusLabel("CANCELLED"), tasks: result.cancelled, empty: "No cancelled tasks." },
      ]
    : [];

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="page-title">Board</h1>
          <p className="mt-1 text-sm text-slate-700">
            Tasks for {membership?.organizationName}, grouped by status.
          </p>
        </div>
        {memberships.length > 1 ? (
          <SelectField
            id="board-organization"
            label="Organization"
            value={organizationId}
            onChange={(event) => {
              writeSelectedOrganization(event.target.value);
              setChosenOrganizationId(event.target.value);
            }}
          >
            {memberships.map((item) => (
              <option key={item.organizationId} value={item.organizationId}>
                {item.organizationName}
              </option>
            ))}
          </SelectField>
        ) : null}
      </div>

      {error ? <FormBanner tone="error">{error}</FormBanner> : null}

      {loading && !result ? (
        <div className="flex items-center gap-3 text-slate-800" role="status">
          <Spinner className="text-blue-700" />
          <span className="text-sm font-medium">Loading the board</span>
        </div>
      ) : null}

      {result ? (
        <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-4" aria-busy={loading}>
          {columns.map((column) => (
            <section key={column.id} className="panel flex min-h-64 flex-col p-4" aria-labelledby={`${column.id}-heading`}>
              <div className="mb-3 flex items-baseline justify-between gap-2">
                <h2 id={`${column.id}-heading`} className="text-base font-semibold text-slate-950">
                  {column.title}
                </h2>
                <p className="text-sm text-slate-600">{column.tasks.length}</p>
              </div>
              {column.tasks.length === 0 ? (
                <p className="text-sm text-slate-700">{column.empty}</p>
              ) : (
                <div className="flex flex-col gap-3">
                  {column.tasks.map((task) => (
                    <TaskWorkCard
                      key={task.id}
                      task={task}
                      canUpdate={canUpdate}
                      onOpen={setSelected}
                      onStatusChange={(item, status) => void changeStatus(item, status)}
                    />
                  ))}
                </div>
              )}
            </section>
          ))}
        </div>
      ) : null}

      <Dialog
        open={selected !== null}
        title={selected?.title ?? "Task"}
        className="max-w-xl"
        onClose={() => setSelected(null)}
      >
        {selected && user ? (
          <div className="space-y-4">
            <TaskNotesPanel
              organizationId={organizationId}
              task={selected}
              currentUserId={user.id}
              canUpdate={canUpdate}
              canDelete={canDelete}
            />
            <div className="flex justify-end">
              <Button variant="secondary" onClick={() => setSelected(null)}>
                Close
              </Button>
            </div>
          </div>
        ) : null}
      </Dialog>
    </div>
  );
}
