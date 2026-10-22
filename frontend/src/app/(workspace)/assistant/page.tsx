import type { Metadata } from "next";
import { AssistantWorkspace } from "@/components/assistant/assistant-workspace";

export const metadata: Metadata = {
  title: "AI Assistant · WorkPilot AI",
};

export default function AssistantPage() {
  return <AssistantWorkspace />;
}
