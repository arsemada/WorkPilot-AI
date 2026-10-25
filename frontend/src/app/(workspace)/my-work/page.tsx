import type { Metadata } from "next";
import { MyWorkWorkspace } from "@/components/tasks/my-work-workspace";

export const metadata: Metadata = {
  title: "My work · WorkPilot AI",
};

export default function MyWorkPage() {
  return <MyWorkWorkspace />;
}
