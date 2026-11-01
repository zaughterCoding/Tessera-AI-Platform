import { NextResponse, type NextRequest } from "next/server";
import { loginHref, SESSION_COOKIE } from "@/features/auth/session-cookie";

// 把当前路径传给服务端布局，会话过期时才能带着原路径去登录。
export function proxy(request: NextRequest) {
  const returnTo = request.nextUrl.pathname + request.nextUrl.search;
  if (!request.cookies.has(SESSION_COOKIE)) {
    return NextResponse.redirect(new URL(loginHref(returnTo), request.url));
  }
  const headers = new Headers(request.headers);
  headers.set("x-return-to", returnTo);
  return NextResponse.next({ request: { headers } });
}

export const config = {
  matcher: [
    "/studio/:path*",
    "/tasks/:path*",
    "/orders/:path*",
    "/entitlements/:path*",
    "/media/:path*",
    "/mine/:path*",
    "/create/:path*",
    "/feed/:path*",
    "/admin/:path*",
  ],
};
