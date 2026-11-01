import { z } from "zod";
import { longString } from "@/shared/api/types";
import type { Tone } from "@/shared/ui/feedback";

export const TASK_STATES = [
  "QUEUED",
  "DISPATCHING",
  "SUBMITTED",
  "RUNNING",
  "RECONCILING",
  "RESULT_PENDING",
  "FAILED_PENDING_FOLLOWUP",
  "CANCELLED_PENDING_FOLLOWUP",
  "QUEUE_TIMEOUT",
  "REVIEW",
  "CANCEL_REQUESTED",
  "SUCCEEDED",
  "FAILED",
  "CANCELLED",
] as const;
export type TaskState = (typeof TASK_STATES)[number];

// satisfies 保证穷举：后端新增状态后这里会编译失败，不会静默显示错误文案。
export const TASK_STATE_VIEW = {
  QUEUED: { label: "排队中", tone: "info", active: true },
  DISPATCHING: { label: "调度中", tone: "info", active: true },
  SUBMITTED: { label: "上游已受理", tone: "info", active: true },
  RUNNING: { label: "上游执行中", tone: "info", active: true },
  RECONCILING: { label: "对账中", tone: "info", active: true },
  RESULT_PENDING: { label: "保存结果中", tone: "info", active: true },
  FAILED_PENDING_FOLLOWUP: { label: "上游失败，处理中", tone: "warning", active: true },
  CANCELLED_PENDING_FOLLOWUP: { label: "上游已取消，处理中", tone: "warning", active: true },
  QUEUE_TIMEOUT: { label: "排队超时，处理中", tone: "warning", active: true },
  REVIEW: { label: "人工核对中", tone: "warning", active: true },
  CANCEL_REQUESTED: { label: "取消确认中", tone: "warning", active: true },
  SUCCEEDED: { label: "已完成", tone: "success", active: false },
  FAILED: { label: "已失败", tone: "danger", active: false },
  CANCELLED: { label: "已取消", tone: "neutral", active: false },
} as const satisfies Record<TaskState, { label: string; tone: Tone; active: boolean }>;

export function taskStateView(state: string): { label: string; tone: Tone; active: boolean } {
  return TASK_STATE_VIEW[state as TaskState] ?? { label: `未知状态（${state}）`, tone: "neutral", active: true };
}

const ACCOUNTING_VIEW: Record<string, { label: string; tone: Tone }> = {
  FROZEN: { label: "次数已冻结", tone: "info" },
  CONSUMED: { label: "次数已消费", tone: "neutral" },
  RELEASED: { label: "次数已退回", tone: "success" },
};

export function accountingView(state: string): { label: string; tone: Tone } {
  return ACCOUNTING_VIEW[state] ?? { label: state, tone: "neutral" };
}

const RESULT_STATE_LABELS: Record<string, string> = {
  PENDING: "待保存",
  SAVING: "保存中",
  SAVED: "已保存",
  DELIVERED: "已交付",
  FAILED: "保存失败",
};

export function resultStateLabel(state: string): string {
  return RESULT_STATE_LABELS[state] ?? state;
}

const CANCEL_STATE_LABELS: Record<string, string> = {
  PENDING: "取消确认中",
  RETRYING: "取消确认中（重试）",
  RESOLVED: "取消已生效",
  REJECTED: "未取消，任务已先完成",
  UNSUPPORTED: "渠道不支持取消，等待任务结束",
};

export function cancelStateLabel(state: string): string {
  return CANCEL_STATE_LABELS[state] ?? state;
}

export const resultSchema = z.object({
  id: longString,
  taskId: longString,
  position: z.number(),
  state: z.string(),
  contentType: z.string().nullish(),
  sizeBytes: longString.nullish(),
  contentDigest: z.string().nullish(),
  deliveredAt: z.string().nullish(),
  downloadAvailable: z.boolean().nullish(),
});
export type TaskResult = z.infer<typeof resultSchema>;

export const taskSchema = z.object({
  id: longString,
  applicationId: longString,
  applicationName: z.string(),
  applicationVersion: z.number(),
  modelVersion: z.string(),
  imageSize: z.string(),
  outputCount: z.number(),
  prompt: z.string().nullish(),
  negativePrompt: z.string().nullish(),
  grantId: longString.nullish(),
  frozenUnits: z.number(),
  state: z.string(),
  accountingState: z.string(),
  stateNote: z.string().nullish(),
  channelDisplayName: z.string().nullish(),
  simulatedChannel: z.boolean().nullish(),
  results: z.array(resultSchema).default([]),
  cancelRequest: z
    .object({
      state: z.string(),
      outcome: z.string().nullish(),
      reason: z.string().nullish(),
      requestedAt: z.string().nullish(),
      open: z.boolean().nullish(),
    })
    .nullish(),
  settlement: z.object({ kind: z.string(), units: z.number(), description: z.string().nullish() }).nullish(),
  canCancel: z.boolean().nullish(),
  deliveredAt: z.string().nullish(),
  settledAt: z.string().nullish(),
  createdAt: z.string().nullish(),
  executionNotice: z.string().nullish(),
});
export type Task = z.infer<typeof taskSchema>;

export const generationStatusSchema = z.object({
  submitEnabled: z.boolean(),
  schedulingEnabled: z.boolean(),
  executionStarted: z.boolean().nullish(),
  simulatedChannel: z.boolean().nullish(),
  channelDisplayName: z.string().nullish(),
  notice: z.string().nullish(),
});
export type GenerationStatus = z.infer<typeof generationStatusSchema>;

export const mediaAssetSchema = z.object({
  id: longString,
  state: z.string(),
  contentType: z.string().nullish(),
  sizeBytes: longString.nullish(),
  originalName: z.string().nullish(),
  url: z.string().nullish(),
  createdAt: z.string().nullish(),
});
export type MediaAsset = z.infer<typeof mediaAssetSchema>;

// 后端返回的媒体地址不含网关前缀。
export function mediaSrc(url: string | null | undefined): string | null {
  if (!url) return null;
  return url.startsWith("/media/") ? `/api${url}` : url;
}

export function resultContentUrl(taskId: string, resultId: string): string {
  return `/api/generation/tasks/${taskId}/results/${resultId}/content`;
}
