import { apiRequest, clearCsrfToken } from "@/lib/api/client";
import type { LoginRequest, MessageResponse, RegisterRequest, UserProfile } from "@/lib/api/types";

export function fetchCurrentUser(session: "required" | "optional" = "optional") {
  return apiRequest<UserProfile>("/api/auth/me", { session });
}

export function login(body: LoginRequest) {
  return apiRequest<UserProfile>("/api/auth/login", {
    method: "POST",
    body,
    session: "optional",
  });
}

export function registerAccount(body: RegisterRequest) {
  return apiRequest<UserProfile>("/api/auth/register", {
    method: "POST",
    body,
    session: "optional",
  });
}

export async function logout() {
  const result = await apiRequest<MessageResponse>("/api/auth/logout", {
    method: "POST",
    session: "optional",
  });
  clearCsrfToken();
  return result;
}
