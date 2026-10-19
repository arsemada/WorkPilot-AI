import type { Metadata } from "next";
import { PlaceholderPage } from "@/components/placeholder-page";

export const metadata: Metadata = {
  title: "AI Assistant · WorkPilot AI",
};

export default function AssistantPage() {
  return (
    <PlaceholderPage
      title="AI Assistant"
      summary="The AI assistant will be implemented in a later phase."
    />
  );
}
