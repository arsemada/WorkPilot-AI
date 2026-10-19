import type { Metadata } from "next";
import { PlaceholderPage } from "@/components/placeholder-page";

export const metadata: Metadata = {
  title: "Tasks · WorkPilot AI",
};

export default function TasksPage() {
  return (
    <PlaceholderPage
      title="Tasks"
      summary="Task lists, assignments, and status tracking will be implemented in a later phase."
    />
  );
}
