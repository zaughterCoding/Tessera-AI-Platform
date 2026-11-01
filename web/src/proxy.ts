import { NextResponse, type NextRequest } from "next/server";
import { loginHref, SESSION_COOKIE } from "@/features/auth/session-cookie";

const PRIVATE_PREFIXES = ["/studio", "/tasks", "/orders", "/entitlements", "/media", "/mine", "/create", "/feed", "/admin"];

function isPrivatePath(pathname: string): boolean {
  return PRIVATE_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

// 每个请求生成新 nonce，只有 Next 自己的脚本能执行。React 需要内联样式属性，样式保留 unsafe-inline。
function contentSecurityPolicy(nonce: string): string {
  const devEval = process.env.NODE_ENV === "development" ? " 'unsafe-eval'" : "";
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${devEval}`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' blob: data:",
    "font-src 'self' data:",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join("; ");
}

export function proxy(request: NextRequest) {
  const returnTo = request.nextUrl.pathname + request.nextUrl.search;
  if (isPrivatePath(request.nextUrl.pathname) && !request.cookies.has(SESSION_COOKIE)) {
    return NextResponse.redirect(new URL(loginHref(returnTo), request.url));
  }

  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const policy = contentSecurityPolicy(nonce);
  const headers = new Headers(request.headers);
  headers.set("x-nonce", nonce);
  headers.set("Content-Security-Policy", policy);
  // 把当前路径传给服务端布局，会话过期时才能带着原路径去登录。
  headers.set("x-return-to", returnTo);

  const response = NextResponse.next({ request: { headers } });
  response.headers.set("Content-Security-Policy", policy);
  return response;
}

export const config = {
  matcher: ["/((?!api|healthz|_next/static|_next/image|favicon.ico|covers/).*)"],
};
