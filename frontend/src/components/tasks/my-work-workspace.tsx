"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import { ApiError } from "@/lib/api/client";
import { fetchMyWork, updateTask } from "@/lib/api/tasks";
import type { Membership, MyWork, Permission, Task, TaskStatus } from "@/lib/api/types";
import { formatDueDate } from "@/lib/tasks";
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

export function MyWorkWorkspace() {
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
  const [result, setResult] = useState<MyWork | null>(null);
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
      setResult(await fetchMyWork(organizationId));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Your work could not be loaded.");
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
        <h1 className="page-title">My work</h1>
        <p className="mt-4 text-base leading-7 text-slate-700">
          You are not a member of an organization yet. Register with an organization name, or ask an
          owner to add your account.
        </p>
      </section>
    );
  }

  const sections = result
    ? [
        {
          id: "overdue",
          title: "Overdue",
          empty: "No overdue tasks assigned to you.",
          tasks: result.overdue,
        },
        {
          id: "due-today",
          title: "Due today",
          empty: "Nothing is due today.",
          tasks: result.dueToday,
        },
        {
          id: "in-progress",
          title: "In progress",
          empty: "You have no other tasks in progress.",
          tasks: result.inProgress,
        },
        {
          id: "up-next",
          title: "Up next",
          empty: "No later tasks are assigned to you.",
          tasks: result.upNext,
        },
      ]
    : [];
  const total = sections.reduce((sum, section) => sum + section.tasks.length, 0);

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="page-title">My work</h1>
          <p className="mt-1 text-sm text-slate-700">
            Open tasks assigned to you in {membership?.organizationName}.
            {result ? ` ${formatDueDate(result.today)}.` : ""}
          </p>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          {memberships.length > 1 ? (
            <SelectField
              id="my-work-organization"
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
          <Link href="/tasks" className="text-sm font-semibold text-blue-800 hover:text-blue-950">
            All tasks
          </Link>
        </div>
      </div>

      {error ? <FormBanner tone="error">{error}</FormBanner> : null}

      {loading && !result ? (
        <div className="flex items-center gap-3 text-slate-800" role="status">
          <Spinner className="text-blue-700" />
          <span className="text-sm font-medium">Loading your work</span>
        </div>
      ) : null}

      {result && total === 0 ? (
        <div className="empty-panel px-4 py-12">
          <p className="font-medium text-slate-950">Nothing assigned to you</p>
          <p className="mt-1 text-sm text-slate-700">
            Open the task list to take a due item, or ask someone to assign you work.
          </p>
        </div>
      ) : null}

      {result && total > 0
        ? sections.map((section) => (
            <section key={section.id} className="space-y-3" aria-labelledby={`${section.id}-heading`}>
              <div className="flex items-baseline justify-between gap-3">
                <h2 id={`${section.id}-heading`} className="text-lg font-semibold text-slate-950">
                  {section.title}
                </h2>
                <p className="text-sm text-slate-600">{section.tasks.length}</p>
              </div>
              {section.tasks.length === 0 ? (
                <p className="text-sm text-slate-700">{section.empty}</p>
              ) : (
                <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  {section.tasks.map((task) => (
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
          ))
        : null}

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
