export const PRIMARY_NAV = [
  { href: "/", label: "首页" },
  { href: "/apps", label: "应用" },
  { href: "/campaigns", label: "限量活动" },
  { href: "/community", label: "社区" },
] as const;

export function isNavActive(pathname: string, href: string): boolean {
  if (href === "/") return pathname === "/";
  if (href === "/community") return pathname.startsWith("/community") || pathname.startsWith("/creations");
  return pathname === href || pathname.startsWith(`${href}/`);
}
