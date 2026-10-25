import type { Metadata } from "next";
import { BoardWorkspace } from "@/components/tasks/board-workspace";

export const metadata: Metadata = {
  title: "Board · WorkPilot AI",
};

export default function BoardPage() {
  return <BoardWorkspace />;
}
