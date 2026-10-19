const RETURN_KEY = "workpilot:return";
const NOTICE_KEY = "workpilot:notice";

const noticeStore = {
  snapshot: null as string | null,
  loaded: false,
  listeners: new Set<() => void>(),
};

function emitNotice() {
  noticeStore.listeners.forEach((listener) => listener());
}

function isAppPath(path: string) {
  return (
    path.startsWith("/") &&
    !path.startsWith("//") &&
    !path.startsWith("/login") &&
    !path.startsWith("/register")
  );
}

export function rememberReturnPath(path: string) {
  if (typeof window === "undefined") {
    return;
  }
  if (isAppPath(path)) {
    sessionStorage.setItem(RETURN_KEY, path);
  }
}

export function consumeReturnPath() {
  if (typeof window === "undefined") {
    return "/dashboard";
  }
  const value = sessionStorage.getItem(RETURN_KEY);
  sessionStorage.removeItem(RETURN_KEY);
  return value && isAppPath(value) ? value : "/dashboard";
}

export function markSessionExpired() {
  if (typeof window === "undefined") {
    return;
  }
  sessionStorage.setItem(NOTICE_KEY, "expired");
  noticeStore.loaded = true;
  noticeStore.snapshot = "expired";
  emitNotice();
}

export function subscribeSessionNotice(listener: () => void) {
  noticeStore.listeners.add(listener);
  return () => {
    noticeStore.listeners.delete(listener);
  };
}

export function getSessionNotice() {
  if (!noticeStore.loaded && typeof window !== "undefined") {
    noticeStore.loaded = true;
    noticeStore.snapshot = sessionStorage.getItem(NOTICE_KEY);
    sessionStorage.removeItem(NOTICE_KEY);
  }
  return noticeStore.snapshot;
}

export function getServerSessionNotice() {
  return null;
}
