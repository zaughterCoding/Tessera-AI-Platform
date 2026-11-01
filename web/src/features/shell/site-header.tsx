"use client";

import * as RadixDialog from "@radix-ui/react-dialog";
import { clsx } from "clsx";
import { Menu, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { isNavActive, PRIMARY_NAV } from "./nav-items";

export function SiteHeader({ search, account }: { search: ReactNode; account: ReactNode }) {
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-paper/95 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-4 px-4">
        <Link href="/" className="text-base font-semibold tracking-tight whitespace-nowrap text-ink">
          AI <span className="text-accent">创作平台</span>
        </Link>

        <nav aria-label="主导航" className="hidden items-center gap-1 md:flex">
          {PRIMARY_NAV.map((item) => {
            const active = isNavActive(pathname, item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={clsx(
                  "rounded-md px-3 py-1.5 text-sm whitespace-nowrap transition-colors",
                  active ? "bg-sunken font-medium text-ink" : "text-ink-soft hover:text-ink",
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto hidden w-56 md:block lg:w-64">{search}</div>
        <div className="ml-auto flex items-center gap-2 md:ml-0">{account}</div>

        <RadixDialog.Root open={menuOpen} onOpenChange={setMenuOpen}>
          <RadixDialog.Trigger aria-label="打开菜单" className="rounded-md p-2 text-ink-soft hover:bg-sunken md:hidden">
            <Menu aria-hidden className="size-5" />
          </RadixDialog.Trigger>
          <RadixDialog.Portal>
            <RadixDialog.Overlay className="fixed inset-0 z-40 bg-ink/40" />
            <RadixDialog.Content
              aria-describedby=""
              className="fixed inset-y-0 right-0 z-50 flex w-72 max-w-[85vw] flex-col gap-4 bg-surface p-4 shadow-overlay"
            >
              <div className="flex items-center justify-between">
                <RadixDialog.Title className="text-base font-semibold">菜单</RadixDialog.Title>
                <RadixDialog.Close aria-label="关闭菜单" className="rounded-md p-2 text-ink-soft hover:bg-sunken">
                  <X aria-hidden className="size-5" />
                </RadixDialog.Close>
              </div>
              {search}
              <nav aria-label="移动端导航" className="flex flex-col gap-1">
                {PRIMARY_NAV.map((item) => (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setMenuOpen(false)}
                    aria-current={isNavActive(pathname, item.href) ? "page" : undefined}
                    className="rounded-md px-3 py-2 text-sm text-ink hover:bg-sunken aria-[current=page]:bg-sunken aria-[current=page]:font-medium"
                  >
                    {item.label}
                  </Link>
                ))}
              </nav>
            </RadixDialog.Content>
          </RadixDialog.Portal>
        </RadixDialog.Root>
      </div>
    </header>
  );
}
