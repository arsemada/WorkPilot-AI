import type { Metadata } from "next";
import { WorkflowsWorkspace } from "@/components/workflows/workflows-workspace";

export const metadata: Metadata = {
  title: "Workflows · WorkPilot AI",
};

export default function WorkflowsPage() {
  return <WorkflowsWorkspace />;
}
