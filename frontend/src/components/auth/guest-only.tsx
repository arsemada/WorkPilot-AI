"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/auth/auth-provider";
import { LoadingScreen } from "@/components/ui/loading";
import { consumeReturnPath } from "@/lib/return-path";

export function GuestOnly({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const router = useRouter();
  const redirected = useRef(false);

  useEffect(() => {
    if (status !== "authenticated" || redirected.current) {
      return;
    }
    redirected.current = true;
    router.replace(consumeReturnPath());
  }, [status, router]);

  if (status === "loading" || status === "authenticated") {
    return <LoadingScreen label="Checking your session" />;
  }

  return children;
}
