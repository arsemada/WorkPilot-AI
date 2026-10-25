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

function NavIcon({ href }: { href: string }) {
  const common = "size-4 shrink-0";
  if (href === "/dashboard") {
    return (
      <svg viewBox="0 0 24 24" className={common} aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8">
        <rect x="3" y="3" width="7" height="7" rx="1.5" />
        <rect x="14" y="3" width="7" height="7" rx="1.5" />
        <rect x="3" y="14" width="7" height="7" rx="1.5" />
        <rect x="14" y="14" width="7" height="7" rx="1.5" />
      </svg>
    );
  }
  if (href === "/my-work") {
    return (
      <svg viewBox="0 0 24 24" className={common} aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8">
        <circle cx="12" cy="8" r="3" />
        <path d="M5.5 19.2c.8-3.1 3.4-5.2 6.5-5.2s5.7 2.1 6.5 5.2" strokeLinecap="round" />
      </svg>
    );
  }
  if (href === "/tasks") {
    return (
      <svg viewBox="0 0 24 24" className={common} aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8">
        <path d="M9 6h11M9 12h11M9 18h11" />
        <path d="M4 6h.01M4 12h.01M4 18h.01" strokeLinecap="round" />
      </svg>
    );
  }
  if (href === "/board") {
    return (
      <svg viewBox="0 0 24 24" className={common} aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8">
        <rect x="3.5" y="4.5" width="5" height="15" rx="1.2" />
        <rect x="9.5" y="4.5" width="5" height="10" rx="1.2" />
        <rect x="15.5" y="4.5" width="5" height="12" rx="1.2" />
      </svg>
    );
  }
  if (href === "/documents") {
    return (
      <svg viewBox="0 0 24 24" className={common} aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8">
        <path d="M7 3.5h7l5 5V20a1.5 1.5 0 0 1-1.5 1.5h-10.5A1.5 1.5 0 0 1 5.5 20V5A1.5 1.5 0 0 1 7 3.5Z" />
        <path d="M14 3.5V9h5" />
      </svg>
    );
  }
  if (href === "/assistant") {
    return (
      <svg viewBox="0 0 24 24" className={common} aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8">
        <path d="M12 4.5 13.6 9 18 10.5 13.6 12 12 16.5 10.4 12 6 10.5 10.4 9 12 4.5Z" />
        <path d="M18 15.5 18.7 17.3 20.5 18 18.7 18.7 18 20.5 17.3 18.7 15.5 18 17.3 17.3 18 15.5Z" />
      </svg>
    );
  }
  if (href === "/workflows") {
    return (
      <svg viewBox="0 0 24 24" className={common} aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8">
        <circle cx="6" cy="6" r="2.2" />
        <circle cx="18" cy="12" r="2.2" />
        <circle cx="6" cy="18" r="2.2" />
        <path d="M8 6.8 15.6 11M8 17.2 15.6 13" />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 24 24" className={common} aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8">
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3.5v2.2M12 18.3v2.2M3.5 12h2.2M18.3 12h2.2M6 6l1.6 1.6M16.4 16.4 18 18M18 6l-1.6 1.6M7.6 16.4 6 18" strokeLinecap="round" />
    </svg>
  );
}

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
              "flex items-center justify-between gap-2 rounded-lg px-3 py-2.5 text-sm font-medium",
              active ? "bg-white text-slate-950 shadow-sm" : "text-slate-300 hover:bg-white/10 hover:text-white",
            )}
          >
            <span className="flex items-center gap-2.5">
              <NavIcon href={item.href} />
              {item.label}
            </span>
            {item.href === "/workflows" && unread > 0 ? (
              <span className={cn("rounded-full px-2 py-0.5 text-xs font-semibold", active ? "bg-blue-700 text-white" : "bg-blue-500 text-white")}>
                {unread}
              </span>
            ) : null}
          </Link>
        );
      })}
    </nav>
  );
}

function Brand() {
  return (
    <div className="px-5 py-5">
      <div className="flex items-center gap-3">
        <span className="grid size-9 place-items-center rounded-lg bg-blue-600 text-sm font-semibold text-white">W</span>
        <div>
          <p className="text-sm font-semibold tracking-wide text-white">WorkPilot AI</p>
          <p className="text-xs text-slate-400">Operations workspace</p>
        </div>
      </div>
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
      <aside className="hidden w-64 shrink-0 flex-col bg-[#0b1220] md:flex">
        <Brand />
        <SidebarNav unread={unread} />
      </aside>
      {mobileOpen ? (
        <div className="fixed inset-0 z-40 md:hidden">
          <button
            type="button"
            className="absolute inset-0 bg-slate-950/50"
            aria-label="Close navigation"
            onClick={() => setMobilePath(null)}
          />
          <div id="mobile-navigation" className="relative flex h-full w-72 flex-col bg-[#0b1220] shadow-lg">
            <Brand />
            <SidebarNav unread={unread} onNavigate={() => setMobilePath(null)} />
          </div>
        </div>
      ) : null}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex flex-wrap items-center justify-between gap-x-3 gap-y-2 border-b border-slate-200/80 bg-white/80 px-4 py-3 backdrop-blur md:px-6">
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
              <p className="truncate text-base font-semibold text-slate-950 max-sm:hidden">{titleForPath(pathname)}</p>
              <p className="truncate text-sm text-slate-600">
                {user?.fullName ?? "Signed in"}
                {membership ? <span className="max-sm:hidden">{` · ${membership.organizationName}`}</span> : null}
              </p>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <Link
              href="/workflows#notifications"
              className="rounded-lg px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
            >
              Notifications{unread > 0 ? ` (${unread})` : ""}
            </Link>
            <Button variant="secondary" className="shrink-0 whitespace-nowrap" onClick={() => setConfirmLogout(true)}>
              Sign out
            </Button>
          </div>
        </header>
        <main id="main" className="flex-1 px-4 py-6 md:px-8 md:py-8">
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
