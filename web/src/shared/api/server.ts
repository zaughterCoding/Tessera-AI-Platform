import "server-only";
import type { z } from "zod";
import { getServerEnv } from "@/shared/config/server-env";
import { ApiError, parseEnvelope, type ApiResult } from "./envelope";

const SERVER_TIMEOUT_MS = 8000;

export { ApiError } from "./envelope";

// 服务端渲染取数：一律 no-store。后端要求下架、取消发布立即生效，不再叠加 Next 数据缓存。
export async function serverApi<S extends z.ZodType>(
  path: string,
  schema: S,
  { token, params }: { token?: string; params?: Record<string, string | number | undefined | null> } = {},
): Promise<ApiResult<z.infer<S>>> {
  const query = new URLSearchParams();
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value != null && value !== "") query.set(key, String(value));
  });
  const suffix = query.size ? `?${query.toString()}` : "";
  let response: Response;
  let text: string;
  try {
    response = await fetch(`${getServerEnv().API_ORIGIN}${path}${suffix}`, {
      headers: { Accept: "application/json", ...(token ? { Authorization: token } : {}) },
      cache: "no-store",
      signal: AbortSignal.timeout(SERVER_TIMEOUT_MS),
    });
    text = await response.text();
  } catch {
    throw new ApiError("后端服务暂时不可用", 503, "UNAVAILABLE");
  }
  return parseEnvelope(response.status, response.ok, text, schema, response.headers.get("X-Request-ID"));
}
