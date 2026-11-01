import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "AI 创作平台", template: "%s · AI 创作平台" },
  description: "浏览 AI 应用、购买使用包、参与限量活动并分享作品。",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
