import type { Metadata } from "next";
import { PlaceholderPage } from "@/components/placeholder-page";

export const metadata: Metadata = {
  title: "Workflows · WorkPilot AI",
};

export default function WorkflowsPage() {
  return (
    <PlaceholderPage
      title="Workflows"
      summary="Workflow automation will be implemented in a later phase."
    />
  );
}
