import { NextResponse, type NextRequest } from "next/server";
import { errorResponse, isSameOriginWrite } from "@/features/auth/bff";
import { SESSION_COOKIE } from "@/features/auth/session-cookie";
import { getServerEnv } from "@/shared/config/server-env";

export async function POST(request: NextRequest) {
  if (!isSameOriginWrite(request)) return errorResponse(403, "FORBIDDEN", "跨站请求已被拒绝");
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  if (token) {
    // 退出以清除 Cookie 为准；后端令牌可能已过期，撤销失败不影响退出。
    await fetch(`${getServerEnv().API_ORIGIN}/user/logout`, {
      method: "POST",
      headers: { authorization: token },
      cache: "no-store",
      signal: AbortSignal.timeout(5000),
    }).catch(() => undefined);
  }
  const response = NextResponse.json({ success: true, code: "OK" });
  response.cookies.delete(SESSION_COOKIE);
  return response;
}
