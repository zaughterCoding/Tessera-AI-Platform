export const SESSION_COOKIE = "aiplatform_session";

// 只有 HTTPS 下才加 Secure，本地 http 运行时浏览器才会保存。
export function sessionCookieOptions(secure: boolean) {
  return { httpOnly: true, sameSite: "lax", path: "/", secure } as const;
}

// 只接受站内路径，防止登录后被带到外部网站。
export function safeReturnTo(value: string | null | undefined): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) return "/";
  if (value.startsWith("/login")) return "/";
  return value;
}

export function loginHref(returnTo: string): string {
  return `/login?returnTo=${encodeURIComponent(returnTo)}`;
}
