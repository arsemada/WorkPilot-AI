"use client";

import { useSyncExternalStore, useState, type FormEvent } from "react";
import Link from "next/link";
import { useAuth } from "@/components/auth/auth-provider";
import { ApiError } from "@/lib/api/client";
import {
  getServerSessionNotice,
  getSessionNotice,
  subscribeSessionNotice,
} from "@/lib/return-path";
import { Form, FormBanner, TextField } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";

export function LoginForm() {
  const { signIn } = useAuth();
  const { showToast } = useToast();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const notice = useSyncExternalStore(subscribeSessionNotice, getSessionNotice, getServerSessionNotice);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!email.trim()) {
      nextErrors.email = "Enter your email.";
    }
    if (!password) {
      nextErrors.password = "Enter your password.";
    }
    setFieldErrors(nextErrors);
    setFormError(null);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await signIn({ email: email.trim().toLowerCase(), password });
      showToast({ tone: "success", message: "Signed in." });
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.details ?? {});
        setFormError(error.message);
      } else {
        setFormError("Sign in failed.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        <p className="text-sm font-semibold text-blue-800">WorkPilot AI</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">Sign in</h1>
        <p className="mt-2 text-base leading-7 text-slate-700">
          Use the account you registered with this workspace.
        </p>
        <div className="mt-8 rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <Form onSubmit={onSubmit}>
            {notice === "expired" ? (
              <FormBanner tone="info">Your session ended. Sign in again.</FormBanner>
            ) : null}
            {formError ? <FormBanner tone="error">{formError}</FormBanner> : null}
            <TextField
              id="email"
              label="Email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              error={fieldErrors.email}
              required
            />
            <TextField
              id="password"
              label="Password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              error={fieldErrors.password}
              required
            />
            <Button type="submit" loading={submitting} className="w-full">
              Sign in
            </Button>
          </Form>
        </div>
        <p className="mt-4 text-sm text-slate-700">
          New to WorkPilot?{" "}
          <Link href="/register" className="font-semibold text-blue-800 underline">
            Create an account
          </Link>
        </p>
      </div>
    </main>
  );
}
