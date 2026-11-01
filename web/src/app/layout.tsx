import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import { AccountMenu } from "@/features/auth/account-menu";
import { getCurrentUser } from "@/features/auth/current-user";
import { SessionProvider } from "@/features/auth/session-provider";
import { RouteFocus } from "@/features/shell/route-focus";
import { SearchForm } from "@/features/shell/search-form";
import { SiteFooter } from "@/features/shell/site-footer";
import { SiteHeader } from "@/features/shell/site-header";
import { QueryProvider } from "@/shared/api/query-provider";
import { ToastProvider } from "@/shared/ui/toast";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "AI 创作平台", template: "%s · AI 创作平台" },
  description: "浏览 AI 应用、购买使用包、参与限量活动并分享作品。",
};

export const viewport: Viewport = {
  themeColor: "#faf8f4",
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  const user = await getCurrentUser();
  return (
    <html lang="zh-CN">
      <body className="flex min-h-dvh flex-col">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-md focus:bg-surface focus:px-3 focus:py-2 focus:text-sm focus:shadow-overlay"
        >
          跳到主要内容
        </a>
        <QueryProvider>
          <ToastProvider>
            <SessionProvider initialUser={user}>
              <SiteHeader search={<SearchForm />} account={<AccountMenu />} />
              <main id="main" tabIndex={-1} className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:py-8">
                {children}
              </main>
              <SiteFooter />
              <RouteFocus />
            </SessionProvider>
          </ToastProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
