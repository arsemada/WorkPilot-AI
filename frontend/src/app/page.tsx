"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/auth/auth-provider";
import { Button } from "@/components/ui/button";
import { LoadingScreen } from "@/components/ui/loading";

export default function HomePage() {
  const { status, errorMessage, retry } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (status === "authenticated") {
      router.replace("/dashboard");
    }
    if (status === "anonymous") {
      router.replace("/login");
    }
  }, [status, router]);

  if (status === "unavailable") {
    return (
      <div className="flex flex-1 items-center justify-center px-6 py-24">
        <div className="panel max-w-md p-6 text-center">
          <h1 className="text-lg font-semibold text-slate-950">WorkPilot AI is unavailable</h1>
          <p className="mt-2 text-sm leading-6 text-slate-700">
            {errorMessage ?? "The session could not be loaded."}
          </p>
          <Button className="mt-4" onClick={retry}>
            Try again
          </Button>
        </div>
      </div>
    );
  }

  return <LoadingScreen label="Opening WorkPilot AI" />;
}
