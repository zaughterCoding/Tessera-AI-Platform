"use client";

import { clsx } from "clsx";
import { Heart } from "lucide-react";
import { useRef, useState } from "react";
import { goToLogin, useSession } from "@/features/auth/session-provider";
import { api, ApiError } from "@/shared/api/client";
import { useToast } from "@/shared/ui/toast";
import { likeResultSchema, type LikeResult } from "./schemas";

export function LikeButton({ creationId, initial }: { creationId: string; initial: LikeResult }) {
  const { user } = useSession();
  const toast = useToast();
  const [state, setState] = useState(initial);
  const confirmed = useRef(initial);
  const latest = useRef(0);

  async function toggle() {
    if (!user) {
      goToLogin();
      return;
    }
    // 乐观更新。接口是幂等的期望状态接口，连点时只采用最后一次意图的响应。
    const liked = !state.liked;
    const request = ++latest.current;
    setState((current) => ({ liked, likeCount: Math.max(0, current.likeCount + (liked ? 1 : -1)) }));
    try {
      const { data } = await api(`/creations/${creationId}/likes/me`, likeResultSchema, { method: liked ? "PUT" : "DELETE" });
      confirmed.current = data;
      if (request === latest.current) setState(data);
    } catch (error) {
      if (request !== latest.current) return;
      setState(confirmed.current);
      if (!(error instanceof ApiError && error.status === 401)) toast("操作没有成功，已恢复原状态", "error");
    }
  }

  return (
    <button
      type="button"
      aria-pressed={state.liked}
      onClick={() => void toggle()}
      className={clsx(
        "inline-flex h-9 items-center gap-2 rounded-md border px-3 text-sm transition-colors",
        state.liked ? "border-accent bg-accent-soft text-accent" : "border-line-strong bg-surface text-ink-soft hover:text-ink",
      )}
    >
      <Heart aria-hidden className={clsx("size-4", state.liked && "fill-current")} />
      {state.liked ? "已赞" : "点赞"}
      <span>{state.likeCount}</span>
    </button>
  );
}
