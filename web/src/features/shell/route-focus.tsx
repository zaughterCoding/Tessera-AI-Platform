"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";

// 客户端切换路由时把焦点移到主内容，读屏和键盘用户才能感知页面变化。
export function RouteFocus() {
  const pathname = usePathname();
  const previous = useRef(pathname);

  useEffect(() => {
    if (previous.current === pathname) return;
    previous.current = pathname;
    document.getElementById("main")?.focus({ preventScroll: true });
  }, [pathname]);

  return null;
}
