import type { FormEvent, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
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

const controlClass =
  "block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-base text-slate-900 shadow-sm placeholder:text-slate-500";

function describedBy(id: string, hint?: string, error?: string) {
  return [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean).join(" ");
}

function FieldChrome({
  id,
  label,
  hint,
  error,
  children,
}: {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-slate-900">
        {label}
      </label>
      {children}
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

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  hint?: string;
};

export function TextField({ id, label, error, hint, className, ...props }: TextFieldProps) {
  return (
    <FieldChrome id={id ?? ""} label={label} hint={hint} error={error}>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id ?? "", hint, error) || undefined}
        className={cn(controlClass, error ? "border-red-700" : "", className)}
        {...props}
      />
    </FieldChrome>
  );
}

type TextAreaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string;
  error?: string;
  hint?: string;
};

export function TextAreaField({ id, label, error, hint, className, ...props }: TextAreaProps) {
  return (
    <FieldChrome id={id ?? ""} label={label} hint={hint} error={error}>
      <textarea
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id ?? "", hint, error) || undefined}
        className={cn(controlClass, "min-h-24", error ? "border-red-700" : "", className)}
        {...props}
      />
    </FieldChrome>
  );
}

type SelectFieldProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  error?: string;
  hint?: string;
};

export function SelectField({ id, label, error, hint, className, children, ...props }: SelectFieldProps) {
  return (
    <FieldChrome id={id ?? ""} label={label} hint={hint} error={error}>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id ?? "", hint, error) || undefined}
        className={cn(controlClass, error ? "border-red-700" : "", className)}
        {...props}
      >
        {children}
      </select>
    </FieldChrome>
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
