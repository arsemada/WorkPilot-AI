import { StatusBadge } from "@/components/ui/status-badge";

export function PlaceholderPage({ title, summary }: { title: string; summary: string }) {
  return (
    <section className="mx-auto max-w-3xl">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-950">{title}</h1>
        <StatusBadge tone="planned">Later phase</StatusBadge>
      </div>
      <p className="mt-4 max-w-2xl text-base leading-7 text-slate-700">{summary}</p>
      <p className="mt-6 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm leading-6 text-slate-800">
        This page is a placeholder. Nothing here is saved or processed yet.
      </p>
    </section>
  );
}
