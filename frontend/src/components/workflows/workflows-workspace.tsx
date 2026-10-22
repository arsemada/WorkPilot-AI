"use client";

import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import { ApiError } from "@/lib/api/client";
import { listNotifications, markAllNotificationsRead, markNotificationRead } from "@/lib/api/notifications";
import { listWorkflowExecutions, listWorkflows, setWorkflowEnabled } from "@/lib/api/workflows";
import type {
  AppNotification,
  Membership,
  Permission,
  WorkflowActionType,
  WorkflowCondition,
  WorkflowEventType,
  WorkflowExecution,
  WorkflowExecutionStatus,
  WorkflowRule,
} from "@/lib/api/types";
import { readSelectedOrganization, subscribeSelectedOrganization } from "@/lib/selected-organization";
import { Button } from "@/components/ui/button";
import { FormBanner } from "@/components/ui/field";
import { Spinner } from "@/components/ui/loading";

const eventLabels: Record<WorkflowEventType, string> = {
  TASK_ASSIGNED: "A task is assigned",
  TASK_OVERDUE: "A task is overdue",
  DOCUMENT_UPLOADED: "A document is uploaded",
  DOCUMENT_PROCESSED: "A document finishes processing",
  SCHEDULED_ACTIVITY: "The daily activity schedule runs",
};

const actionLabels: Record<WorkflowActionType, string> = {
  NOTIFY_ASSIGNEE: "Notify the assignee",
  NOTIFY_RELEVANT_USERS: "Notify the assignee, or the creator when there is no assignee",
  PROCESS_DOCUMENT: "Process the document",
  SUMMARIZE_DOCUMENT: "Summarize the document for the uploader",
  SUMMARIZE_ACTIVITY: "Send the daily activity summary",
};

const conditionLabels: Record<WorkflowCondition, string> = {
  ALWAYS: "Always",
  HAS_ASSIGNEE: "The task has an assignee",
  TEXT_EXTRACTED: "The document has extracted text",
};

const statusLabels: Record<WorkflowExecutionStatus, string> = {
  RUNNING: "Running",
  SUCCEEDED: "Succeeded",
  FAILED: "Failed",
  SKIPPED: "Skipped",
};

function hasPermission(membership: Membership | undefined, permission: Permission) {
  return membership?.permissions.includes(permission) ?? false;
}

function formatWhen(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function WorkflowsWorkspace() {
  const { user } = useAuth();
  const memberships = useMemo(
    () => [...(user?.memberships ?? [])].sort((a, b) => a.organizationName.localeCompare(b.organizationName)),
    [user],
  );
  const storedOrganizationId = useSyncExternalStore(subscribeSelectedOrganization, readSelectedOrganization, () => null);
  const organizationId = useMemo(() => {
    const match = memberships.find((membership) => membership.organizationId === storedOrganizationId);
    return (match ?? memberships[0])?.organizationId ?? "";
  }, [memberships, storedOrganizationId]);
  const membership = memberships.find((item) => item.organizationId === organizationId);
  const canManage = hasPermission(membership, "ORG_UPDATE");
  const [rules, setRules] = useState<WorkflowRule[]>([]);
  const [executions, setExecutions] = useState<WorkflowExecution[]>([]);
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendingRuleId, setPendingRuleId] = useState<string | null>(null);
  const [pendingNotificationId, setPendingNotificationId] = useState<string | null>(null);
  const [markingAll, setMarkingAll] = useState(false);

  const load = useCallback(async () => {
    if (!organizationId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [nextRules, nextExecutions, nextNotifications] = await Promise.all([
        listWorkflows(organizationId),
        listWorkflowExecutions(organizationId),
        listNotifications(),
      ]);
      setRules(nextRules);
      setExecutions(nextExecutions);
      setNotifications(nextNotifications.filter((item) => item.organizationId === organizationId));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Workflows could not be loaded.");
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

  async function toggle(rule: WorkflowRule) {
    if (!canManage || pendingRuleId) {
      return;
    }
    setPendingRuleId(rule.id);
    setError(null);
    try {
      const updated = await setWorkflowEnabled(organizationId, rule.id, !rule.enabled);
      setRules((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "The workflow could not be updated.");
    } finally {
      setPendingRuleId(null);
    }
  }

  async function markRead(notification: AppNotification) {
    if (notification.read || pendingNotificationId) {
      return;
    }
    setPendingNotificationId(notification.id);
    setError(null);
    try {
      const updated = await markNotificationRead(notification.id);
      setNotifications((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "The notification could not be updated.");
    } finally {
      setPendingNotificationId(null);
    }
  }

  async function markAll() {
    setMarkingAll(true);
    setError(null);
    try {
      await markAllNotificationsRead();
      setNotifications((current) => current.map((item) => ({ ...item, read: true })));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "The notifications could not be updated.");
    } finally {
      setMarkingAll(false);
    }
  }

  if (!organizationId) {
    return (
      <section className="mx-auto max-w-3xl">
        <h1 className="page-title">Workflows</h1>
        <p className="mt-4 text-base leading-7 text-slate-700">
          You are not a member of an organization yet. Register with an organization name, or ask an owner to add your account.
        </p>
      </section>
    );
  }

  const unread = notifications.filter((item) => !item.read).length;

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-8">
      <div>
        <h1 className="page-title">Workflows</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-700">
          {membership?.organizationName}. These are the triggers and actions this organization can run. A disabled rule does not run, and a failed run stays visible.
        </p>
      </div>
      {error ? <FormBanner tone="error">{error}</FormBanner> : null}
      {loading && rules.length === 0 ? (
        <div className="flex items-center gap-3 text-sm text-slate-700">
          <Spinner />
          Loading workflows
        </div>
      ) : null}

      <section>
        <h2 className="text-lg font-semibold text-slate-950">Rules</h2>
        {rules.length === 0 && !loading ? (
          <div className="empty-panel mt-3 px-4 py-12">
            <p className="font-medium text-slate-950">No workflows yet</p>
            <p className="mt-1 text-sm text-slate-700">Supported workflows appear here after the organization is created.</p>
          </div>
        ) : (
          <ul className="mt-3 space-y-3">
            {rules.map((rule) => (
              <li key={rule.id} className="panel px-4 py-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold text-slate-950">{rule.name}</p>
                    <p className="mt-1 text-sm text-slate-700">When {eventLabels[rule.eventType].toLowerCase()}</p>
                    <p className="text-sm text-slate-700">Action: {actionLabels[rule.actionType]}</p>
                    <p className="text-sm text-slate-700">Condition: {conditionLabels[rule.conditionType]}</p>
                    <p className="mt-1 text-xs font-semibold uppercase tracking-wide text-slate-600">
                      {rule.enabled ? "Enabled" : "Disabled"}
                    </p>
                  </div>
                  {canManage ? (
                    <Button
                      variant="secondary"
                      loading={pendingRuleId === rule.id}
                      onClick={() => void toggle(rule)}
                    >
                      {rule.enabled ? "Disable" : "Enable"}
                    </Button>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2 className="text-lg font-semibold text-slate-950">History</h2>
        {executions.length === 0 && !loading ? (
          <div className="empty-panel mt-3 px-4 py-12">
            <p className="font-medium text-slate-950">No workflow runs yet</p>
            <p className="mt-1 text-sm text-slate-700">Assigning a task or uploading a document can start one.</p>
          </div>
        ) : (
          <ul className="mt-3 space-y-3">
            {executions.map((execution) => (
              <li key={execution.id} className="panel px-4 py-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-semibold text-slate-950">{execution.ruleName}</p>
                  <p className={execution.status === "FAILED" ? "text-sm font-semibold text-red-800" : "text-sm font-semibold text-slate-700"}>
                    {statusLabels[execution.status]}
                  </p>
                </div>
                <p className="mt-1 text-sm text-slate-700">{eventLabels[execution.eventType]}</p>
                {execution.status === "FAILED" && execution.errorMessage ? (
                  <p className="mt-2 text-sm text-red-800">{execution.errorMessage}</p>
                ) : null}
                {execution.status !== "FAILED" && execution.resultMessage ? (
                  <p className="mt-2 text-sm text-slate-700">{execution.resultMessage}</p>
                ) : null}
                <p className="mt-2 text-xs text-slate-600">
                  Attempt {execution.attemptCount} · {formatWhen(execution.createdAt)}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section id="notifications">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-950">Notifications</h2>
          {unread > 0 ? (
            <Button variant="secondary" loading={markingAll} onClick={() => void markAll()}>
              Mark all read
            </Button>
          ) : null}
        </div>
        {notifications.length === 0 && !loading ? (
          <div className="empty-panel mt-3 px-4 py-12">
            <p className="font-medium text-slate-950">No notifications yet</p>
            <p className="mt-1 text-sm text-slate-700">Assigning a task or an overdue task can send one here.</p>
          </div>
        ) : (
          <ul className="mt-3 space-y-3">
            {notifications.map((notification) => (
              <li
                key={notification.id}
                className={notification.read ? "panel px-4 py-4" : "rounded-2xl border border-blue-200 bg-blue-50 px-4 py-4 shadow-sm"}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold text-slate-950">{notification.title}</p>
                    <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-slate-800">{notification.body}</p>
                    <p className="mt-2 text-xs text-slate-600">
                      {notification.read ? "Read" : "Unread"} · {formatWhen(notification.createdAt)}
                    </p>
                  </div>
                  {notification.read ? null : (
                    <Button
                      variant="secondary"
                      loading={pendingNotificationId === notification.id}
                      onClick={() => void markRead(notification)}
                    >
                      Mark read
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
