"use client";

import Link from "next/link";
import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import { Button } from "@/components/ui/button";
import { FormBanner, SelectField } from "@/components/ui/field";
import { LoadingScreen } from "@/components/ui/loading";
import { StatusBadge } from "@/components/ui/status-badge";
import { DataTable } from "@/components/ui/table";
import { ApiError } from "@/lib/api/client";
import { getDashboardActivity, getDashboardSummary, getDashboardTrends } from "@/lib/api/dashboard";
import type { ActivityEvent, DashboardSummary, DashboardTrend, Task } from "@/lib/api/types";
import { formatActivityTime, formatTrendDate, TREND_RANGES, utcDaysAgo, utcToday } from "@/lib/dashboard";
import {
  readSelectedOrganization,
  subscribeSelectedOrganization,
  writeSelectedOrganization,
} from "@/lib/selected-organization";
import { formatDueDate, priorityLabel, priorityTone, statusLabel, statusTone } from "@/lib/tasks";

const recentTaskColumns = [
  {
    id: "title",
    header: "Task",
    cell: (row: Task) => row.title,
  },
  {
    id: "status",
    header: "Status",
    cell: (row: Task) => <StatusBadge tone={statusTone(row.status)}>{statusLabel(row.status)}</StatusBadge>,
  },
  {
    id: "priority",
    header: "Priority",
    cell: (row: Task) => <StatusBadge tone={priorityTone(row.priority)}>{priorityLabel(row.priority)}</StatusBadge>,
  },
  {
    id: "due",
    header: "Due",
    cell: (row: Task) => formatDueDate(row.dueDate),
  },
];

export function DashboardHome() {
  const { user } = useAuth();
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
  const [rangeDays, setRangeDays] = useState<(typeof TREND_RANGES)[number]>(14);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [trends, setTrends] = useState<DashboardTrend | null>(null);
  const [activity, setActivity] = useState<ActivityEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!organizationId) {
      return;
    }
    let active = true;
    const handle = window.setTimeout(() => {
      void (async () => {
        setLoading(true);
        setError(null);
        try {
          const [nextSummary, nextTrends, nextActivity] = await Promise.all([
            getDashboardSummary(organizationId),
            getDashboardTrends(organizationId, utcDaysAgo(rangeDays), utcToday()),
            getDashboardActivity(organizationId),
          ]);
          if (!active) {
            return;
          }
          setSummary(nextSummary);
          setTrends(nextTrends);
          setActivity(nextActivity);
        } catch (caught) {
          if (!active) {
            return;
          }
          setSummary(null);
          setTrends(null);
          setActivity([]);
          setError(caught instanceof ApiError ? caught.message : "The dashboard could not be loaded.");
        } finally {
          if (active) {
            setLoading(false);
          }
        }
      })();
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(handle);
    };
  }, [organizationId, rangeDays]);

  useEffect(() => {
    if (!organizationId) {
      return;
    }
    const refresh = () => {
      if (document.visibilityState !== "visible") {
        return;
      }
      void Promise.all([
        getDashboardSummary(organizationId),
        getDashboardTrends(organizationId, utcDaysAgo(rangeDays), utcToday()),
        getDashboardActivity(organizationId),
      ])
        .then(([nextSummary, nextTrends, nextActivity]) => {
          setSummary(nextSummary);
          setTrends(nextTrends);
          setActivity(nextActivity);
          setError(null);
        })
        .catch((caught: unknown) => {
          setError(caught instanceof ApiError ? caught.message : "The dashboard could not be loaded.");
        });
    };
    document.addEventListener("visibilitychange", refresh);
    window.addEventListener("focus", refresh);
    return () => {
      document.removeEventListener("visibilitychange", refresh);
      window.removeEventListener("focus", refresh);
    };
  }, [organizationId, rangeDays]);

  if (memberships.length === 0) {
    return (
      <section className="mx-auto max-w-3xl">
        <h1 className="page-title">Dashboard</h1>
        <p className="mt-4 text-base leading-7 text-slate-700">
          You are not a member of an organization yet. Register with an organization name, or ask an
          owner to add your account.
        </p>
      </section>
    );
  }

  const membership = memberships.find((item) => item.organizationId === organizationId);

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="page-title">Dashboard</h1>
          <p className="mt-1 text-sm text-slate-700">
            Task counts for {membership?.organizationName ?? "this organization"}.
          </p>
        </div>
        {memberships.length > 1 ? (
          <SelectField
            id="dashboard-organization"
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
      {loading && !summary ? <LoadingScreen label="Loading dashboard" /> : null}

      {summary ? (
        <>
          <p className="text-sm leading-6 text-slate-700">
            {summary.memberCount === 1 ? "1 member" : `${summary.memberCount} members`}.{" "}
            {summary.todoTasks} to do and {summary.cancelledTasks} cancelled.
          </p>
          <section aria-busy={loading} className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard label="Total tasks" value={summary.totalTasks} />
            <MetricCard label="In progress" value={summary.inProgressTasks} />
            <MetricCard label="Completed" value={summary.completedTasks} />
            <MetricCard label="Overdue" value={summary.overdueTasks} emphasis={summary.overdueTasks > 0} />
          </section>

          <section className="panel p-5" aria-busy={loading}>
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-slate-950">Completions</h2>
                <p className="mt-1 max-w-2xl text-sm leading-6 text-slate-700">
                  Tasks marked done on each UTC day. A task that was already done before activity was
                  recorded stays in Completed and does not appear on a day here.
                </p>
              </div>
              <div role="group" aria-label="Completion date range" className="flex flex-wrap gap-2">
                {TREND_RANGES.map((days) => (
                  <Button
                    key={days}
                    variant={rangeDays === days ? "primary" : "secondary"}
                    aria-pressed={rangeDays === days}
                    onClick={() => setRangeDays(days)}
                  >
                    {days} days
                  </Button>
                ))}
              </div>
            </div>
            {trends && trends.points.every((point) => point.completedCount === 0) ? (
              <p className="mt-4 text-sm text-slate-700">No tasks were marked done in this range.</p>
            ) : null}
            {trends ? <CompletionChart points={trends.points} /> : null}
          </section>

          <section className="grid gap-6 lg:grid-cols-2">
            <div>
              <div className="mb-3 flex items-center justify-between gap-3">
                <h2 className="text-lg font-semibold text-slate-950">Recent tasks</h2>
                <Link href="/tasks" className="text-sm font-semibold text-blue-800 hover:text-blue-950">
                  Open tasks
                </Link>
              </div>
              <DataTable
                columns={recentTaskColumns}
                rows={summary.recentTasks}
                getRowKey={(row) => row.id}
                caption="Five most recently updated tasks"
                emptyTitle="No tasks yet"
                emptyDescription="Create a task and it will show up here after the next refresh."
              />
            </div>
            <div>
              <h2 className="mb-3 text-lg font-semibold text-slate-950">Recent activity</h2>
              {activity.length === 0 ? (
                <div className="empty-panel px-4 py-12">
                  <p className="font-medium text-slate-950">No activity yet</p>
                  <p className="mt-1 text-sm text-slate-700">
                    Creating, updating, completing, and deleting tasks will be listed here.
                  </p>
                </div>
              ) : (
                <ol className="space-y-3">
                  {activity.map((event) => (
                    <li key={event.id} className="panel px-4 py-3">
                      <p className="text-sm font-medium text-slate-950">
                        {event.actorName} {event.summary}
                      </p>
                      <p className="mt-1 text-xs text-slate-600">{formatActivityTime(event.occurredAt)}</p>
                    </li>
                  ))}
                </ol>
              )}
            </div>
          </section>
        </>
      ) : null}
    </div>
  );
}

function MetricCard({ label, value, emphasis = false }: { label: string; value: number; emphasis?: boolean }) {
  return (
    <div className={emphasis ? "panel metric metric-alert px-5 py-4" : "panel metric px-5 py-4"}>
      <p className="text-sm font-medium text-slate-700">{label}</p>
      <p className={emphasis ? "mt-2 text-3xl font-semibold text-red-800" : "mt-2 text-3xl font-semibold text-slate-950"}>
        {value}
      </p>
    </div>
  );
}

function CompletionChart({ points }: { points: DashboardTrend["points"] }) {
  const max = Math.max(1, ...points.map((point) => point.completedCount));
  const slot = 36;
  const width = Math.max(points.length * slot, slot);
  const height = 168;
  const padBottom = 32;
  const padTop = 18;
  const plotHeight = height - padBottom - padTop;

  return (
    <div className="mt-4 space-y-4">
      <div className="overflow-x-auto">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          role="img"
          aria-labelledby="completion-chart-title"
          className="h-44 min-w-full"
        >
          <title id="completion-chart-title">
            {points.map((point) => `${formatTrendDate(point.date)} ${point.completedCount}`).join(", ")}
          </title>
          <line x1="0" x2={width} y1={height - padBottom} y2={height - padBottom} stroke="#cbd5e1" />
          {points.map((point, index) => {
            const barHeight = (point.completedCount / max) * plotHeight;
            const barWidth = 18;
            const x = index * slot + (slot - barWidth) / 2;
            const y = height - padBottom - barHeight;
            const showLabel = points.length <= 14 || index % Math.ceil(points.length / 7) === 0 || index === points.length - 1;
            return (
              <g key={point.date}>
                <rect
                  x={x}
                  y={point.completedCount === 0 ? height - padBottom - 1 : y}
                  width={barWidth}
                  height={point.completedCount === 0 ? 1 : Math.max(barHeight, 3)}
                  rx="2"
                  fill={point.completedCount === 0 ? "#cbd5e1" : "#1d4ed8"}
                >
                  <title>
                    {formatTrendDate(point.date)}: {point.completedCount}
                  </title>
                </rect>
                {showLabel ? (
                  <text x={x + barWidth / 2} y={height - 10} textAnchor="middle" fontSize="10" fill="#334155">
                    {formatTrendDate(point.date)}
                  </text>
                ) : null}
              </g>
            );
          })}
        </svg>
      </div>
      <div className="panel max-h-48 overflow-y-auto">
        <table className="min-w-full border-collapse text-left text-sm">
          <caption className="sr-only">Tasks marked done by UTC day</caption>
          <thead className="sticky top-0 bg-slate-50 text-slate-800">
            <tr>
              <th scope="col" className="px-3 py-2 font-semibold">
                Date
              </th>
              <th scope="col" className="px-3 py-2 font-semibold">
                Marked done
              </th>
            </tr>
          </thead>
          <tbody>
            {points.map((point) => (
              <tr key={point.date} className="border-t border-slate-200">
                <th scope="row" className="px-3 py-2 font-medium text-slate-900">
                  {formatTrendDate(point.date)}
                </th>
                <td className="px-3 py-2 text-slate-800">{point.completedCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
