import { cn } from "@/lib/cn";

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "inline-block size-4 animate-spin rounded-full border-2 border-current border-r-transparent",
        className,
      )}
    />
  );
}

export function LoadingScreen({ label }: { label: string }) {
  return (
    <div className="flex min-h-full flex-1 items-center justify-center px-6 py-24" role="status">
      <div className="panel flex items-center gap-3 px-5 py-4 text-slate-800">
        <Spinner className="size-5 text-blue-700" />
        <span className="text-sm font-medium">{label}</span>
      </div>
    </div>
  );
}
