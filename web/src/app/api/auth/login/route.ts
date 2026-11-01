import { NextResponse, type NextRequest } from "next/server";
import { z } from "zod";
import { errorResponse, isHttps, isSameOriginWrite } from "@/features/auth/bff";
import { SESSION_COOKIE, sessionCookieOptions } from "@/features/auth/session-cookie";
import { parseEnvelope, ApiError } from "@/shared/api/envelope";
import { getServerEnv } from "@/shared/config/server-env";

// 令牌只写入 HttpOnly Cookie，响应体不返回，浏览器脚本无法读取。
export async function POST(request: NextRequest) {
  if (!isSameOriginWrite(request)) return errorResponse(403, "FORBIDDEN", "跨站请求已被拒绝");
  let upstream: Response;
  let text: string;
  try {
    upstream = await fetch(`${getServerEnv().API_ORIGIN}/user/login`, {
      method: "POST",
      headers: { "content-type": "application/json", accept: "application/json" },
      body: await request.text(),
      cache: "no-store",
      signal: AbortSignal.timeout(10000),
    });
    text = await upstream.text();
  } catch {
    return errorResponse(504, "UPSTREAM_UNAVAILABLE", "后端服务暂时不可用，请稍后重试");
  }
  try {
    const { data: token, requestId } = parseEnvelope(
      upstream.status,
      upstream.ok,
      text,
      z.string().min(1),
      upstream.headers.get("x-request-id"),
    );
    const response = NextResponse.json({ success: true, code: "OK", requestId });
    response.cookies.set(SESSION_COOKIE, token, sessionCookieOptions(isHttps(request)));
    return response;
  } catch (error) {
    if (error instanceof ApiError) return errorResponse(error.status, error.code, error.message, error.requestId);
    throw error;
  }
}
