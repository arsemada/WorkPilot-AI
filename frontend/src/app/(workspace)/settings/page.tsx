import type { Metadata } from "next";
import { SettingsWorkspace } from "@/components/settings/settings-workspace";

export const metadata: Metadata = {
  title: "Settings · WorkPilot AI",
};

export default function SettingsPage() {
  return <SettingsWorkspace />;
}
