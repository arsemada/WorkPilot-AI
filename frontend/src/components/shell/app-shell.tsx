"use client";

import { useCallback, useEffect, useState, type ReactNode } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/components/auth/auth-provider";
import { unreadNotificationCount } from "@/lib/api/notifications";
import { navigation, titleForPath } from "@/components/shell/navigation";
import { rememberReturnPath } from "@/lib/return-path";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { cn } from "@/lib/cn";

function SidebarNav({ unread, onNavigate }: { unread: number; onNavigate?: () => void }) {
  const pathname = usePathname();

  return (
    <nav aria-label="Primary" className="flex flex-1 flex-col gap-1 px-3 py-4">
      {navigation.map((item) => {
        const active = pathname === item.href;
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active ? "page" : undefined}
            onClick={onNavigate}
            className={cn(
              "flex items-center justify-between gap-2 rounded-md px-3 py-2 text-sm font-medium",
              active ? "bg-blue-50 text-blue-900" : "text-slate-800 hover:bg-slate-100",
            )}
          >
            <span>{item.label}</span>
            {item.href === "/workflows" && unread > 0 ? (
              <span className="rounded-full bg-blue-700 px-2 py-0.5 text-xs font-semibold text-white">{unread}</span>
            ) : null}
          </Link>
        );
      })}
    </nav>
  );
}

function Brand() {
  return (
    <div className="border-b border-slate-200 px-5 py-4">
      <p className="text-xs font-semibold tracking-wide text-blue-800">WorkPilot AI</p>
      <p className="mt-1 text-sm text-slate-700">Operations workspace</p>
    </div>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, signOut } = useAuth();
  const { showToast } = useToast();
  const [mobilePath, setMobilePath] = useState<string | null>(null);
  const mobileOpen = mobilePath === pathname;
  const [unread, setUnread] = useState(0);
  const [confirmLogout, setConfirmLogout] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const closeLogout = useCallback(() => setConfirmLogout(false), []);

  useEffect(() => {
    if (!user) {
      return;
    }
    let cancelled = false;
    async function refresh() {
      try {
        const result = await unreadNotificationCount();
        if (!cancelled) {
          setUnread(result.count);
        }
      } catch {
        if (!cancelled) {
          setUnread(0);
        }
      }
    }
    const start = window.setTimeout(() => {
      void refresh();
    }, 0);
    const interval = window.setInterval(() => {
      void refresh();
    }, 30000);
    return () => {
      cancelled = true;
      window.clearTimeout(start);
      window.clearInterval(interval);
    };
  }, [user, pathname]);

  useEffect(() => {
    if (!mobileOpen) {
      return;
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setMobilePath(null);
      }
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [mobileOpen]);

  async function confirmSignOut() {
    setSigningOut(true);
    try {
      rememberReturnPath(pathname);
      await signOut();
      showToast({ tone: "success", message: "You have signed out." });
      router.replace("/login");
    } catch {
      showToast({ tone: "error", message: "Sign out failed. Try again." });
    } finally {
      setSigningOut(false);
      setConfirmLogout(false);
    }
  }

  const membership = user?.memberships[0];

  return (
    <div className="flex min-h-full flex-1">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:top-3 focus:left-3 focus:z-50 focus:rounded-md focus:bg-white focus:px-3 focus:py-2"
      >
        Skip to content
      </a>
      <aside className="hidden w-64 shrink-0 flex-col border-r border-slate-200 bg-white md:flex">
        <Brand />
        <SidebarNav unread={unread} />
      </aside>
      {mobileOpen ? (
        <div className="fixed inset-0 z-40 md:hidden">
          <button
            type="button"
            className="absolute inset-0 bg-slate-900/40"
            aria-label="Close navigation"
            onClick={() => setMobilePath(null)}
          />
          <div id="mobile-navigation" className="relative flex h-full w-72 flex-col bg-white shadow-lg">
            <Brand />
            <SidebarNav unread={unread} onNavigate={() => setMobilePath(null)} />
          </div>
        </div>
      ) : null}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 py-3 md:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <Button
              variant="secondary"
              className="md:hidden"
              aria-expanded={mobileOpen}
              aria-controls="mobile-navigation"
              onClick={() => setMobilePath(pathname)}
            >
              Menu
            </Button>
            <div className="min-w-0">
              <p className="truncate text-base font-semibold text-slate-950">{titleForPath(pathname)}</p>
              <p className="truncate text-sm text-slate-700">
                {user?.fullName ?? "Signed in"}
                {membership ? ` · ${membership.organizationName}` : ""}
              </p>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <Link
              href="/workflows#notifications"
              className="rounded-md px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-100"
            >
              Notifications{unread > 0 ? ` (${unread})` : ""}
            </Link>
            <Button variant="secondary" className="shrink-0 whitespace-nowrap" onClick={() => setConfirmLogout(true)}>
              Sign out
            </Button>
          </div>
        </header>
        <main id="main" className="flex-1 px-4 py-6 md:px-8">
          {children}
        </main>
      </div>
      <Dialog
        open={confirmLogout}
        title="Sign out"
        description="This ends your session on this browser."
        onClose={closeLogout}
      >
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirmLogout(false)}>
            Cancel
          </Button>
          <Button variant="danger" loading={signingOut} onClick={() => void confirmSignOut()}>
            Sign out
          </Button>
        </div>
      </Dialog>
    </div>
  );
}
