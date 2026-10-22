export type NavItem = {
  href: string;
  label: string;
  description: string;
};

export const navigation: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", description: "Task counts and recent activity" },
  { href: "/tasks", label: "Tasks", description: "Task list and assignment" },
  { href: "/documents", label: "Documents", description: "Organization document library" },
  { href: "/assistant", label: "AI Assistant", description: "Conversations with the assistant" },
  { href: "/workflows", label: "Workflows", description: "Workflow rules, history, and notifications" },
  { href: "/settings", label: "Settings", description: "Settings placeholder" },
];

export function titleForPath(pathname: string) {
  return navigation.find((item) => item.href === pathname)?.label ?? "WorkPilot AI";
}
