import type { Metadata } from "next";
import { PlaceholderPage } from "@/components/placeholder-page";

export const metadata: Metadata = {
  title: "Documents · WorkPilot AI",
};

export default function DocumentsPage() {
  return (
    <PlaceholderPage
      title="Documents"
      summary="Document upload and processing will be implemented in a later phase."
    />
  );
}
