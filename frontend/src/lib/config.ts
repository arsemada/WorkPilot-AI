const browserDefault = "http://localhost:8081";

/**
 * Address the browser can reach. This is the only API URL used by client fetches.
 * `NEXT_PUBLIC_*` values are embedded in the browser bundle, so they must stay free of secrets.
 */
export function browserApiBaseUrl() {
  return process.env.NEXT_PUBLIC_API_BASE_URL || browserDefault;
}

/**
 * Address the Next.js server process can reach. Docker Compose sets this to the backend service.
 * It is not prefixed for the browser and is unused by credentialed session calls.
 */
export function serverApiBaseUrl() {
  return process.env.API_BASE_URL || browserApiBaseUrl();
}
