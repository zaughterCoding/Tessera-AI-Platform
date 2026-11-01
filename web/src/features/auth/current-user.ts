import "server-only";
import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";
import { cache } from "react";
import { ApiError, serverApi } from "@/shared/api/server";
import { isAdmin, userSchema, type User } from "@/shared/api/types";
import { loginHref, SESSION_COOKIE } from "./session-cookie";

export async function getSessionToken(): Promise<string | undefined> {
  return (await cookies()).get(SESSION_COOKIE)?.value;
}

// 同一次渲染内只查询一次当前用户。后端不可用时按未登录展示，不让公开页面失败。
export const getCurrentUser = cache(async (): Promise<User | null> => {
  const token = await getSessionToken();
  if (!token) return null;
  try {
    return (await serverApi("/user/me", userSchema, { token })).data;
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    return null;
  }
});

// 路由守卫只改善体验，真正的权限边界仍在后端。
export async function requireUser(): Promise<User> {
  const user = await getCurrentUser();
  if (user) return user;
  redirect(loginHref((await headers()).get("x-return-to") ?? "/"));
}

export async function requireAdmin(): Promise<User> {
  const user = await requireUser();
  if (!isAdmin(user)) redirect("/");
  return user;
}
