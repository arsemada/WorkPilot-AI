import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import type { OrganizationRole } from "@/lib/api/types";

type Tone = "neutral" | "info" | "success" | "planned";

const tones: Record<Tone, string> = {
  neutral: "bg-slate-100 text-slate-800",
  info: "bg-blue-50 text-blue-900",
  success: "bg-emerald-50 text-emerald-900",
  planned: "bg-amber-50 text-amber-950",
};

export function StatusBadge({ tone, children }: { tone: Tone; children: ReactNode }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold tracking-wide",
        tones[tone],
      )}
    >
      {children}
    </span>
  );
}

export function roleTone(role: OrganizationRole): Tone {
  if (role === "OWNER") {
    return "info";
  }
  if (role === "ADMIN") {
    return "neutral";
  }
  return "success";
}
