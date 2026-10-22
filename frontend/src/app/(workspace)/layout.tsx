"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/components/auth/auth-provider";
import { AppShell } from "@/components/shell/app-shell";
import { Button } from "@/components/ui/button";
import { LoadingScreen } from "@/components/ui/loading";
import { rememberReturnPath } from "@/lib/return-path";

export default function WorkspaceLayout({ children }: { children: ReactNode }) {
  const { status, errorMessage, retry } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "anonymous") {
      rememberReturnPath(pathname);
      router.replace("/login");
    }
  }, [status, pathname, router]);

  const ready = status === "authenticated";

  return (
    <>
      {status === "loading" || status === "anonymous" ? (
        <LoadingScreen label="Checking your session" />
      ) : null}
      {status === "unavailable" ? (
        <div className="flex flex-1 items-center justify-center px-6 py-24">
          <div className="panel max-w-md p-6 text-center">
            <h1 className="text-lg font-semibold text-slate-950">The workspace is unavailable</h1>
            <p className="mt-2 text-sm leading-6 text-slate-700">
              {errorMessage ?? "The session could not be loaded."}
            </p>
            <Button className="mt-4" onClick={retry}>
              Try again
            </Button>
          </div>
        </div>
      ) : null}
      <div hidden={!ready} className={ready ? "contents" : undefined}>
        <AppShell>{children}</AppShell>
      </div>
    </>
  );
}
