import type { Metadata } from "next";
import { DocumentsWorkspace } from "@/components/documents/documents-workspace";

export const metadata: Metadata = {
  title: "Documents · WorkPilot AI",
};

export default function DocumentsPage() {
  return <DocumentsWorkspace />;
}
