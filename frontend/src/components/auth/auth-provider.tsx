"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useRouter, usePathname } from "next/navigation";
import { ApiError, setUnauthorizedHandler } from "@/lib/api/client";
import { fetchCurrentUser, login, logout, registerAccount } from "@/lib/api/auth";
import type { LoginRequest, RegisterRequest, UserProfile } from "@/lib/api/types";
import { markSessionExpired } from "@/lib/return-path";
import { useToast } from "@/components/ui/toast";

type Status = "loading" | "authenticated" | "anonymous" | "unavailable";

type AuthContextValue = {
  user: UserProfile | null;
  status: Status;
  errorMessage: string | null;
  signIn: (input: LoginRequest) => Promise<void>;
  signUp: (input: RegisterRequest) => Promise<void>;
  signOut: () => Promise<void>;
  retry: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { showToast } = useToast();
  const [user, setUser] = useState<UserProfile | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const expired = useRef(false);
  const sessionRequest = useRef(0);

  const expireSession = useCallback(() => {
    if (expired.current) {
      return;
    }
    expired.current = true;
    markSessionExpired();
    setUser(null);
    setStatus("anonymous");
    showToast({ tone: "error", message: "Your session ended. Sign in again." });
    router.replace("/login");
  }, [router, showToast]);

  useEffect(() => {
    setUnauthorizedHandler(expireSession);
    return () => setUnauthorizedHandler(null);
  }, [expireSession]);

  useEffect(() => {
    let active = true;
    const requestId = ++sessionRequest.current;

    fetchCurrentUser("optional")
      .then((profile) => {
        if (!active || sessionRequest.current !== requestId) {
          return;
        }
        expired.current = false;
        setUser(profile);
        setStatus("authenticated");
        setErrorMessage(null);
      })
      .catch((error: unknown) => {
        if (!active || sessionRequest.current !== requestId) {
          return;
        }
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          setUser(null);
          setStatus("anonymous");
          setErrorMessage(null);
          return;
        }
        setUser(null);
        setStatus("unavailable");
        setErrorMessage(
          error instanceof Error ? error.message : "Could not load your session.",
        );
      });

    return () => {
      active = false;
    };
  }, [attempt]);

  useEffect(() => {
    if (status !== "authenticated") {
      return;
    }

    let active = true;
    fetchCurrentUser("required").catch((error: unknown) => {
      if (!active || !(error instanceof ApiError) || error.status !== 401) {
        return;
      }
    });

    return () => {
      active = false;
    };
  }, [pathname, status]);

  useEffect(() => {
    function onFocus() {
      if (status !== "authenticated") {
        return;
      }
      void fetchCurrentUser("required").catch((error: unknown) => {
        if (!(error instanceof ApiError) || error.status !== 401) {
          return;
        }
      });
    }

    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [status]);

  const signIn = useCallback(async (input: LoginRequest) => {
    sessionRequest.current += 1;
    const profile = await login(input);
    expired.current = false;
    setUser(profile);
    setStatus("authenticated");
    setErrorMessage(null);
  }, []);

  const signUp = useCallback(
    async (input: RegisterRequest) => {
      await registerAccount(input);
      try {
        await signIn({ email: input.email, password: input.password });
      } catch (error) {
        if (error instanceof ApiError) {
          throw new ApiError(
            error.status,
            `Account created, but sign in failed. ${error.message}`,
            error.details,
          );
        }
        throw error;
      }
    },
    [signIn],
  );

  const signOut = useCallback(async () => {
    sessionRequest.current += 1;
    try {
      await logout();
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) {
        throw error;
      }
    } finally {
      setUser(null);
      setStatus("anonymous");
    }
  }, []);

  const retry = useCallback(() => {
    setStatus("loading");
    setErrorMessage(null);
    setAttempt((value) => value + 1);
  }, []);

  const value = useMemo(
    () => ({ user, status, errorMessage, signIn, signUp, signOut, retry }),
    [user, status, errorMessage, signIn, signUp, signOut, retry],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
