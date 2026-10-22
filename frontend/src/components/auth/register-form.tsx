"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useAuth } from "@/components/auth/auth-provider";
import { ApiError } from "@/lib/api/client";
import { AuthFrame } from "@/components/auth/auth-frame";
import { Form, FormBanner, TextField } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";

export function RegisterForm() {
  const { signUp } = useAuth();
  const { showToast } = useToast();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [organizationName, setOrganizationName] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!firstName.trim()) {
      nextErrors.firstName = "Enter your first name.";
    }
    if (!lastName.trim()) {
      nextErrors.lastName = "Enter your last name.";
    }
    if (!email.trim()) {
      nextErrors.email = "Enter your email.";
    }
    if (password.length < 8 || password.length > 100) {
      nextErrors.password = "Password must be between 8 and 100 characters.";
    }
    if (organizationName.trim() && organizationName.trim().length < 2) {
      nextErrors.organizationName = "Organization name must be at least 2 characters.";
    }
    setFieldErrors(nextErrors);
    setFormError(null);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await signUp({
        email: email.trim().toLowerCase(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        ...(organizationName.trim() ? { organizationName: organizationName.trim() } : {}),
      });
      showToast({ tone: "success", message: "Account created." });
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.details ?? {});
        setFormError(error.message);
      } else {
        setFormError("Registration failed.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthFrame
      title="Create an account"
      lede="Registration creates your user. An organization name also makes you its owner."
      footer={
        <>
          Already have an account?{" "}
          <Link href="/login" className="font-semibold text-blue-800 underline-offset-2 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <Form onSubmit={onSubmit}>
            {formError ? <FormBanner tone="error">{formError}</FormBanner> : null}
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                id="firstName"
                label="First name"
                autoComplete="given-name"
                value={firstName}
                onChange={(event) => setFirstName(event.target.value)}
                error={fieldErrors.firstName}
                required
              />
              <TextField
                id="lastName"
                label="Last name"
                autoComplete="family-name"
                value={lastName}
                onChange={(event) => setLastName(event.target.value)}
                error={fieldErrors.lastName}
                required
              />
            </div>
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
              autoComplete="new-password"
              hint="Use 8 to 100 characters."
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              error={fieldErrors.password}
              required
            />
            <TextField
              id="organizationName"
              label="Organization name"
              autoComplete="organization"
              hint="Optional. Leave blank to join an organization later."
              value={organizationName}
              onChange={(event) => setOrganizationName(event.target.value)}
              error={fieldErrors.organizationName}
            />
            <Button type="submit" loading={submitting} className="w-full">
              Create account
            </Button>
          </Form>
    </AuthFrame>
  );
}
