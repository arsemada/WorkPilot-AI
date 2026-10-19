"use client";

import { useAuth } from "@/components/auth/auth-provider";
import { DataTable } from "@/components/ui/table";
import { StatusBadge, roleTone } from "@/components/ui/status-badge";
import type { Membership } from "@/lib/api/types";

const columns = [
  {
    id: "organization",
    header: "Organization",
    cell: (row: Membership) => row.organizationName,
  },
  {
    id: "role",
    header: "Role",
    cell: (row: Membership) => <StatusBadge tone={roleTone(row.role)}>{row.role}</StatusBadge>,
  },
];

export function DashboardHome() {
  const { user } = useAuth();
  const memberships = user?.memberships ?? [];

  return (
    <div className="mx-auto max-w-4xl space-y-8">
      <section>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-950">Dashboard</h1>
          <StatusBadge tone="planned">Later phase</StatusBadge>
        </div>
        <p className="mt-4 max-w-2xl text-base leading-7 text-slate-700">
          Signed in as {user?.fullName ?? "your account"}. Dashboard metrics and activity summaries
          will be implemented in a later phase.
        </p>
      </section>
      <section>
        <h2 className="text-lg font-semibold text-slate-950">Organizations</h2>
        <p className="mt-1 mb-4 max-w-2xl text-sm leading-6 text-slate-700">
          These memberships come from your current session. Organization administration will be
          implemented in a later phase.
        </p>
        <DataTable
          columns={columns}
          rows={memberships}
          getRowKey={(row) => row.id}
          caption="Organizations on the current session"
          emptyTitle="No organizations yet"
          emptyDescription="Create an account with an organization name to appear here. Administration tools come later."
        />
      </section>
    </div>
  );
}
