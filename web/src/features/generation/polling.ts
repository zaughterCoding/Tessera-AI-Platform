import { taskStateView } from "./schemas";

// 每次受保护请求都会给会话续期且没有绝对上限，挂着的页面不能一直轮询，
// 否则会话永远不过期。到达上限后暂停，由用户决定是否继续跟踪（约 17 分钟）。
export const MAX_POLLS = 120;

const REVIEW_POLL_MS = 30_000;

export function nextPollMs(state: string, pollsSinceResume: number): number | false {
  if (!taskStateView(state).active) return false;
  if (pollsSinceResume >= MAX_POLLS) return false;
  // 人工核对不会很快变化，不必频繁查询。
  if (state === "REVIEW") return REVIEW_POLL_MS;
  if (pollsSinceResume < 10) return 2000;
  if (pollsSinceResume < 30) return 5000;
  return 10_000;
}

export function trackingPaused(state: string, pollsSinceResume: number): boolean {
  return taskStateView(state).active && pollsSinceResume >= MAX_POLLS;
}
