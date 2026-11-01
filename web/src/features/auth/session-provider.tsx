"use client";

import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { setUnauthorizedHandler } from "@/shared/api/client";
import type { User } from "@/shared/api/types";
import { Button } from "@/shared/ui/button";
import { Notice } from "@/shared/ui/feedback";
import { loginHref } from "./session-cookie";

type Session = { user: User | null };

const SessionContext = createContext<Session>({ user: null });

export function useSession(): Session {
  return useContext(SessionContext);
}

// 身份变化时整页跳转，服务端布局和查询缓存都以新身份重新开始。
export function hardNavigate(target: string) {
  window.location.assign(target);
}

export function goToLogin() {
  hardNavigate(loginHref(window.location.pathname + window.location.search));
}

// 登录和退出都用整页跳转，因此初始用户在页面生命周期内只可能变成“已过期”。
export function SessionProvider({ initialUser, children }: { initialUser: User | null; children: ReactNode }) {
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    setUnauthorizedHandler(() => setExpired(true));
  }, []);

  const value = useMemo<Session>(() => ({ user: expired ? null : initialUser }), [expired, initialUser]);

  return (
    <SessionContext.Provider value={value}>
      {expired && initialUser && (
        <div className="mx-auto w-full max-w-6xl px-4 pt-4">
          <Notice
            tone="warning"
            title="登录已过期"
            action={<Button size="sm" variant="secondary" onClick={goToLogin}>重新登录</Button>}
          >
            页面上的旧数据可能已不准确，重新登录后会回到当前页面。
          </Notice>
        </div>
      )}
      {children}
    </SessionContext.Provider>
  );
}
