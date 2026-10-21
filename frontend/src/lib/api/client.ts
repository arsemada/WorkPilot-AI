import { browserApiBaseUrl } from "@/lib/config";
import type { CsrfResponse, ErrorBody } from "@/lib/api/types";

export class ApiError extends Error {
  readonly status: number;
  readonly details?: Record<string, string>;

  constructor(status: number, message: string, details?: Record<string, string>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
  }
}

type Method = "GET" | "POST" | "PATCH" | "DELETE";

type RequestOptions = {
  method?: Method;
  body?: unknown;
  session?: "required" | "optional";
  retried?: boolean;
};

type CsrfState = {
  token: string;
  headerName: string;
};

let csrf: CsrfState | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

export function clearCsrfToken() {
  csrf = null;
}

async function readError(response: Response) {
  try {
    const body = (await response.json()) as ErrorBody;
    return new ApiError(
      response.status,
      body.message || "Request failed",
      body.details,
    );
  } catch {
    return new ApiError(response.status, "Request failed");
  }
}

async function loadCsrf(): Promise<CsrfState> {
  if (csrf) {
    return csrf;
  }

  let response: Response;
  try {
    response = await fetch(`${browserApiBaseUrl()}/api/auth/csrf`, {
      method: "GET",
      credentials: "include",
      headers: { Accept: "application/json" },
    });
  } catch {
    throw new ApiError(0, "The server could not be reached. Check that the backend is running.");
  }

  if (!response.ok) {
    throw await readError(response);
  }

  const data = (await response.json()) as CsrfResponse;
  csrf = {
    token: data.token,
    headerName: data.headerName || "X-XSRF-TOKEN",
  };
  return csrf;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = { Accept: "application/json" };

  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  if (method !== "GET") {
    const token = await loadCsrf();
    headers[token.headerName] = token.token;
  }

  let response: Response;
  try {
    response = await fetch(`${browserApiBaseUrl()}${path}`, {
      method,
      credentials: "include",
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
  } catch {
    throw new ApiError(0, "The server could not be reached. Check that the backend is running.");
  }

  if (response.status === 403 && method !== "GET" && !options.retried) {
    clearCsrfToken();
    return apiRequest<T>(path, { ...options, retried: true });
  }

  if (response.status === 401 && options.session === "required") {
    unauthorizedHandler?.();
  }

  if (!response.ok) {
    throw await readError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export async function apiFormRequest<T>(path: string, body: FormData, retried = false): Promise<T> {
  const token = await loadCsrf();
  let response: Response;
  try {
    response = await fetch(`${browserApiBaseUrl()}${path}`, {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "application/json",
        [token.headerName]: token.token,
      },
      body,
    });
  } catch {
    throw new ApiError(0, "The server could not be reached. Check that the backend is running.");
  }

  if (response.status === 403 && !retried) {
    clearCsrfToken();
    return apiFormRequest<T>(path, body, true);
  }

  if (response.status === 401) {
    unauthorizedHandler?.();
  }

  if (!response.ok) {
    throw await readError(response);
  }

  return (await response.json()) as T;
}

export async function apiDownload(path: string): Promise<Blob> {
  let response: Response;
  try {
    response = await fetch(`${browserApiBaseUrl()}${path}`, {
      method: "GET",
      credentials: "include",
      headers: { Accept: "*/*" },
    });
  } catch {
    throw new ApiError(0, "The server could not be reached. Check that the backend is running.");
  }

  if (response.status === 401) {
    unauthorizedHandler?.();
  }

  if (!response.ok) {
    throw await readError(response);
  }

  return response.blob();
}
