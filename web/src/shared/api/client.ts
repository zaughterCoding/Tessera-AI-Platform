import { z } from "zod";
import { ApiError, parseEnvelope, type ApiResult } from "./envelope";

export { ApiError } from "./envelope";

const DEFAULT_TIMEOUT_MS = 15000;

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  params?: Record<string, string | number | undefined | null>;
  body?: unknown;
  timeoutMs?: number;
  signal?: AbortSignal;
};

let onUnauthorized: () => void = () => undefined;

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

export function buildQuery(params: RequestOptions["params"]): string {
  const query = new URLSearchParams();
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value != null && value !== "") query.set(key, String(value));
  });
  const text = query.toString();
  return text ? `?${text}` : "";
}

// 浏览器只访问同源 BFF，令牌由 BFF 从 HttpOnly Cookie 注入。
export async function api<S extends z.ZodType>(
  path: string,
  schema: S,
  { method = "GET", params, body, timeoutMs = DEFAULT_TIMEOUT_MS, signal }: RequestOptions = {},
): Promise<ApiResult<z.infer<S>>> {
  const timeout = AbortSignal.timeout(timeoutMs);
  const multipart = typeof FormData !== "undefined" && body instanceof FormData;
  let response: Response;
  let text: string;
  try {
    response = await fetch(`/api${path}${buildQuery(params)}`, {
      method,
      headers: {
        Accept: "application/json",
        ...(body != null && !multipart ? { "Content-Type": "application/json" } : {}),
      },
      credentials: "same-origin",
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
      body: body == null ? undefined : multipart ? (body as FormData) : JSON.stringify(body),
    });
    text = await response.text();
  } catch (error) {
    if (signal?.aborted) throw error;
    if (timeout.aborted) throw new ApiError("请求超时，请稍后重试", 0, "TIMEOUT");
    throw new ApiError("网络连接失败，请检查连接", 0, "NETWORK_ERROR");
  }
  if (response.status === 401) onUnauthorized();
  return parseEnvelope(response.status, response.ok, text, schema, response.headers.get("X-Request-ID"));
}

export const noData = z.unknown();
