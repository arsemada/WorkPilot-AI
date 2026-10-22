export const TREND_RANGES = [7, 14, 30] as const;

export function utcToday() {
  return new Date().toISOString().slice(0, 10);
}

export function utcDaysAgo(inclusiveDays: number) {
  const now = new Date();
  const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - (inclusiveDays - 1)));
  return start.toISOString().slice(0, 10);
}

export function formatTrendDate(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(year, month - 1, day)));
}

export function formatActivityTime(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}
