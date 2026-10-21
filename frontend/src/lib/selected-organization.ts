"use client";

const KEY = "workpilot:organization";
const LEGACY_KEY = "workpilot:task-organization";

export function subscribeSelectedOrganization(listener: () => void) {
  window.addEventListener("storage", listener);
  return () => window.removeEventListener("storage", listener);
}

export function readSelectedOrganization() {
  return sessionStorage.getItem(KEY) ?? sessionStorage.getItem(LEGACY_KEY);
}

export function writeSelectedOrganization(organizationId: string) {
  sessionStorage.setItem(KEY, organizationId);
}
