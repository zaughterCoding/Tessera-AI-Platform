"use client";

import * as Menu from "@radix-ui/react-dropdown-menu";
import { ChevronDown } from "lucide-react";
import Link from "@/shared/ui/link";
import { useState } from "react";
import { isAdmin } from "@/shared/api/types";
import { Button } from "@/shared/ui/button";
import { useToast } from "@/shared/ui/toast";
import { goToLogin, hardNavigate, useSession } from "./session-provider";

const ACCOUNT_LINKS = [
  { href: "/tasks", label: "我的任务" },
  { href: "/entitlements", label: "我的权益" },
  { href: "/orders", label: "我的订单" },
  { href: "/create", label: "发布作品" },
  { href: "/media", label: "我的素材" },
  { href: "/feed", label: "关注动态" },
  { href: "/mine", label: "个人中心" },
];

const ITEM = "block cursor-pointer rounded px-3 py-2 text-sm text-ink outline-none data-[highlighted]:bg-sunken";

export function AccountMenu() {
  const { user } = useSession();
  const toast = useToast();
  const [leaving, setLeaving] = useState(false);

  if (!user) {
    return <Button size="sm" onClick={goToLogin}>登录</Button>;
  }

  async function logout() {
    setLeaving(true);
    try {
      await fetch("/api/auth/logout", { method: "POST", credentials: "same-origin" });
      // 请求草稿可能含提示词，退出时一并清除。
      sessionStorage.clear();
      hardNavigate("/");
    } catch {
      setLeaving(false);
      toast("退出失败，请检查网络后重试", "error");
    }
  }

  return (
    <Menu.Root>
      <Menu.Trigger className="flex items-center gap-1.5 rounded-md px-2 py-1.5 text-sm text-ink hover:bg-sunken">
        <span aria-hidden className="flex size-6 items-center justify-center rounded-full bg-accent text-xs text-white">
          {user.nickName.slice(0, 1)}
        </span>
        <span className="hidden max-w-24 truncate sm:inline">{user.nickName}</span>
        <ChevronDown aria-hidden className="size-3.5 text-ink-muted" />
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Content align="end" sideOffset={6} className="z-50 min-w-40 rounded-md border border-line bg-surface p-1 shadow-overlay">
          {ACCOUNT_LINKS.map((item) => (
            <Menu.Item key={item.href} asChild>
              <Link href={item.href} className={ITEM}>{item.label}</Link>
            </Menu.Item>
          ))}
          {isAdmin(user) && (
            <Menu.Item asChild>
              <Link href="/admin" className={ITEM}>运营台</Link>
            </Menu.Item>
          )}
          <Menu.Separator className="my-1 h-px bg-line" />
          <Menu.Item className={ITEM} disabled={leaving} onSelect={() => void logout()}>
            退出登录
          </Menu.Item>
        </Menu.Content>
      </Menu.Portal>
    </Menu.Root>
  );
}
