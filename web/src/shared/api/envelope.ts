import type { z } from "zod";

export type ApiErrorCode = string;

export class ApiError extends Error {
  readonly status: number;
  readonly code: ApiErrorCode;
  readonly requestId: string | null;

  constructor(message: string, status = 0, code: ApiErrorCode = "NETWORK_ERROR", requestId: string | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }

  // 请求可能已被后端处理，只是响应没有回来；写操作要按“结果未知”处理。
  get outcomeUnknown(): boolean {
    return this.code === "TIMEOUT" || this.code === "NETWORK_ERROR" || this.status === 502 || this.status === 504;
  }
}

function statusMessage(status: number): string {
  if (status === 400) return "请求参数不合法";
  if (status === 401) return "请先登录";
  if (status === 403) return "当前账号没有操作权限";
  if (status === 404) return "资源不存在";
  if (status === 409) return "请求与当前状态冲突";
  if (status === 413) return "文件过大";
  if (status === 429) return "请求过于频繁，请稍后重试";
  if (status === 503) return "服务暂时不可用，请稍后重试";
  return `请求未完成（HTTP ${status}）`;
}

export type ApiResult<T> = { data: T; total: number | null; requestId: string | null };

type RawEnvelope = {
  success?: boolean;
  code?: string;
  errorMsg?: string;
  data?: unknown;
  total?: string | number;
  requestId?: string;
};

// 后端统一响应体：非 2xx 或 success=false 转为 ApiError，data 按 schema 校验。
export function parseEnvelope<S extends z.ZodType>(
  status: number,
  ok: boolean,
  text: string,
  schema: S,
  headerRequestId: string | null,
): ApiResult<z.infer<S>> {
  let raw: RawEnvelope | null = null;
  try {
    raw = JSON.parse(text) as RawEnvelope;
  } catch {
    if (ok) throw new ApiError("响应格式不正确", status, "INVALID_RESPONSE", headerRequestId);
  }
  const requestId = raw?.requestId ?? headerRequestId;
  if (!ok || !raw?.success) {
    throw new ApiError(raw?.errorMsg || statusMessage(status), status, raw?.code || `HTTP_${status}`, requestId);
  }
  const parsed = schema.safeParse(raw.data);
  if (!parsed.success) {
    console.error("接口响应不符合约定", parsed.error.issues.slice(0, 5));
    throw new ApiError("响应格式不正确", status, "INVALID_RESPONSE", requestId);
  }
  return { data: parsed.data, total: raw.total == null ? null : Number(raw.total), requestId };
}
