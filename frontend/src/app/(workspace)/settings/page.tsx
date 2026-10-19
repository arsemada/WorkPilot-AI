import type { Metadata } from "next";
import { PlaceholderPage } from "@/components/placeholder-page";

export const metadata: Metadata = {
  title: "Settings · WorkPilot AI",
};

export default function SettingsPage() {
  return (
    <PlaceholderPage
      title="Settings"
      summary="Account and organization settings will be implemented in a later phase."
    />
  );
}
