import "server-only";
import { NextResponse, type NextRequest } from "next/server";

const PATH_PATTERN = /^[A-Za-z0-9._~-]+(?:\/[A-Za-z0-9._~-]+)*$/;

// 这些路径不经过浏览器入口：登录走专用路由以免令牌进入 JS，回调和内部接口不对用户开放。
export function isProxyablePath(path: string): boolean {
  if (!PATH_PATTERN.test(path)) return false;
  const segments = path.split("/");
  if (segments.some((segment) => segment === "." || segment === "..")) return false;
  const first = segments[0];
  if (first === "actuator" || first === "internal") return false;
  if (path === "user/login" || path === "error") return false;
  if (path.startsWith("generation/provider")) return false;
  return true;
}

// 令牌放进 Cookie 后浏览器会自动携带，写请求必须确认来自本站，防止 CSRF。
export function isSameOriginWrite(request: NextRequest): boolean {
  if (request.method === "GET" || request.method === "HEAD") return true;
  const fetchSite = request.headers.get("sec-fetch-site");
  if (fetchSite) return fetchSite === "same-origin";
  const origin = request.headers.get("origin");
  if (!origin) return true;
  const host = request.headers.get("x-forwarded-host") ?? request.headers.get("host");
  try {
    return new URL(origin).host === host;
  } catch {
    return false;
  }
}

export function isHttps(request: NextRequest): boolean {
  return (request.headers.get("x-forwarded-proto") ?? request.nextUrl.protocol.replace(":", "")) === "https";
}

export function errorResponse(status: number, code: string, errorMsg: string, requestId?: string | null) {
  return NextResponse.json({ success: false, code, errorMsg, ...(requestId ? { requestId } : {}) }, { status });
}
