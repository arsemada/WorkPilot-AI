import type { FormEvent, InputHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/cn";

export function Form({
  children,
  className,
  onSubmit,
}: {
  children: ReactNode;
  className?: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form className={cn("space-y-4", className)} noValidate onSubmit={onSubmit}>
      {children}
    </form>
  );
}

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  hint?: string;
};

export function TextField({ id, label, error, hint, className, ...props }: TextFieldProps) {
  const describedBy = [hint ? `${id}-hint` : null, error ? `${id}-error` : null]
    .filter(Boolean)
    .join(" ");

  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-slate-900">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={cn(
          "block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-base text-slate-900 shadow-sm placeholder:text-slate-500",
          error ? "border-red-700" : "",
          className,
        )}
        {...props}
      />
      {hint ? (
        <p id={`${id}-hint`} className="text-sm text-slate-700">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={`${id}-error`} className="text-sm text-red-800">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export function FormBanner({ tone, children }: { tone: "error" | "info"; children: ReactNode }) {
  return (
    <p
      role={tone === "error" ? "alert" : "status"}
      className={cn(
        "rounded-md border px-3 py-2 text-sm",
        tone === "error"
          ? "border-red-200 bg-red-50 text-red-900"
          : "border-blue-200 bg-blue-50 text-blue-950",
      )}
    >
      {children}
    </p>
  );
}
