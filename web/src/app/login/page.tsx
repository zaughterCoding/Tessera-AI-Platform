import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { getCurrentUser } from "@/features/auth/current-user";
import { LoginForm } from "@/features/auth/login-form";
import { safeReturnTo } from "@/features/auth/session-cookie";
import { Card } from "@/shared/ui/layout";

export const metadata: Metadata = { title: "登录", robots: { index: false } };

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ returnTo?: string | string[] }> }) {
  const { returnTo } = await searchParams;
  const target = safeReturnTo(typeof returnTo === "string" ? returnTo : null);
  if (await getCurrentUser()) redirect(target);
  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-6 text-2xl font-semibold tracking-tight">登录</h1>
      <Card>
        <LoginForm returnTo={target} />
      </Card>
    </div>
  );
}
