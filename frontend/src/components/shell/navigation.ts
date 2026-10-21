export type NavItem = {
  href: string;
  label: string;
  description: string;
};

export const navigation: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", description: "Overview placeholder" },
  { href: "/tasks", label: "Tasks", description: "Task list and assignment" },
  { href: "/documents", label: "Documents", description: "Document processing placeholder" },
  { href: "/assistant", label: "AI Assistant", description: "Assistant placeholder" },
  { href: "/workflows", label: "Workflows", description: "Workflow placeholder" },
  { href: "/settings", label: "Settings", description: "Settings placeholder" },
];

export function titleForPath(pathname: string) {
  return navigation.find((item) => item.href === pathname)?.label ?? "WorkPilot AI";
}
