import { apiRequest } from "@/lib/api/client";
import type { WorkflowExecution, WorkflowRule } from "@/lib/api/types";

export function listWorkflows(organizationId: string) {
  return apiRequest<WorkflowRule[]>(`/api/organizations/${organizationId}/workflows`, { session: "required" });
}

export function listWorkflowExecutions(organizationId: string) {
  return apiRequest<WorkflowExecution[]>(`/api/organizations/${organizationId}/workflows/executions`, {
    session: "required",
  });
}

export function setWorkflowEnabled(organizationId: string, ruleId: string, enabled: boolean) {
  return apiRequest<WorkflowRule>(
    `/api/organizations/${organizationId}/workflows/${ruleId}/${enabled ? "enable" : "disable"}`,
    { method: "POST", body: {}, session: "required" },
  );
}
