"use client";

import { useSyncExternalStore } from "react";
import { parseServerTime } from "./format";

const listeners = new Set<() => void>();
let timer: number | null = null;
let now = 0;

function subscribe(listener: () => void) {
  listeners.add(listener);
  if (timer === null) {
    now = Date.now();
    timer = window.setInterval(() => {
      now = Date.now();
      listeners.forEach((notify) => notify());
    }, 1000);
  }
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0 && timer !== null) {
      window.clearInterval(timer);
      timer = null;
    }
  };
}

// 服务端渲染时返回 null，避免水合文本不一致。
export function useCountdown(target: string | null | undefined): number | null {
  const current = useSyncExternalStore(subscribe, () => now || Date.now(), () => 0);
  const time = parseServerTime(target);
  if (!time || current === 0) return null;
  return Math.max(0, time.getTime() - current);
}
