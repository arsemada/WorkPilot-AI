import type { Metadata } from "next";
import { TasksWorkspace } from "@/components/tasks/tasks-workspace";

export const metadata: Metadata = {
  title: "Tasks · WorkPilot AI",
};

export default function TasksPage() {
  return <TasksWorkspace />;
}
