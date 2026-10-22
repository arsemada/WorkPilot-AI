import type { ReactNode } from "react";

export function AuthFrame({
  title,
  lede,
  children,
  footer,
}: {
  title: string;
  lede: string;
  children: ReactNode;
  footer: ReactNode;
}) {
  return (
    <main className="grid min-h-full flex-1 lg:grid-cols-[minmax(0,1fr)_minmax(22rem,34rem)]">
      <section className="relative hidden overflow-hidden bg-[#0b1220] px-12 py-14 text-white lg:flex lg:flex-col lg:justify-between">
        <div>
          <p className="text-sm font-semibold tracking-[0.18em] text-blue-200 uppercase">WorkPilot AI</p>
          <p className="mt-6 max-w-md text-4xl leading-tight font-semibold tracking-tight">
            One workspace for the work an organization is tracking.
          </p>
          <p className="mt-4 max-w-md text-base leading-7 text-slate-300">
            Tasks, documents, the assistant, and workflow rules stay inside the organization that owns them.
          </p>
        </div>
        <p className="text-sm text-slate-400">Operations workspace</p>
      </section>
      <section className="flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-md">
          <p className="text-xs font-semibold tracking-[0.16em] text-blue-800 uppercase lg:hidden">WorkPilot AI</p>
          <h1 className="page-title mt-2 lg:mt-0">{title}</h1>
          <p className="mt-2 text-base leading-7 text-slate-600">{lede}</p>
          <div className="panel mt-8 p-6">{children}</div>
          <div className="mt-4 text-sm text-slate-600">{footer}</div>
        </div>
      </section>
    </main>
  );
}
