import { NextResponse, type NextRequest } from "next/server";
import { errorResponse, isProxyablePath, isSameOriginWrite } from "@/features/auth/bff";
import { SESSION_COOKIE } from "@/features/auth/session-cookie";
import { getServerEnv } from "@/shared/config/server-env";

const UPSTREAM_TIMEOUT_MS = 30000;

const REQUEST_HEADERS = ["accept", "content-type", "content-length", "if-none-match", "if-modified-since", "range"];
const RESPONSE_HEADERS = [
  "content-type",
  "content-length",
  "content-disposition",
  "cache-control",
  "etag",
  "last-modified",
  "x-content-type-options",
  "x-request-id",
  "content-range",
  "accept-ranges",
];

// 只透传不解析，长整数、错误码和文件流都保持后端原样。
async function proxy(request: NextRequest): Promise<Response> {
  const path = request.nextUrl.pathname.slice("/api/".length);
  if (!isProxyablePath(path)) return errorResponse(404, "NOT_FOUND", "资源不存在");
  if (!isSameOriginWrite(request)) return errorResponse(403, "FORBIDDEN", "跨站请求已被拒绝");

  const headers = new Headers();
  for (const name of REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  if (token) headers.set("authorization", token);

  const hasBody = request.method !== "GET" && request.method !== "HEAD" && request.body;
  let upstream: Response;
  try {
    upstream = await fetch(`${getServerEnv().API_ORIGIN}/${path}${request.nextUrl.search}`, {
      method: request.method,
      headers,
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
      ...(hasBody ? { body: request.body, duplex: "half" } : {}),
    } as RequestInit);
  } catch {
    // 写请求可能已被后端处理，504 让前端按“结果未知”走恢复流程。
    return errorResponse(504, "UPSTREAM_UNAVAILABLE", "后端服务暂时不可用，请稍后重试");
  }

  const responseHeaders = new Headers();
  for (const name of RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  const response = new NextResponse(upstream.body, { status: upstream.status, headers: responseHeaders });
  if (upstream.status === 401 && token) response.cookies.delete(SESSION_COOKIE);
  return response;
}

export const GET = proxy;
export const HEAD = proxy;
export const POST = proxy;
export const PUT = proxy;
export const DELETE = proxy;
