"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useCallback, useState, useSyncExternalStore } from "react";
import { api, ApiError } from "@/shared/api/client";
import { taskSchema } from "./schemas";

export type SubmitBody = {
  requestKey: string;
  applicationId: string;
  applicationVersion: number;
  modelVersion: string;
  imageSize: string;
  outputCount: number;
  prompt: string;
  negativePrompt: string;
  mediaIds: string[];
};

const DRAFT_KEY = "aiplatform.generation-draft";

// 生成接口没有按请求键查询的入口，只能用同键同内容重放；内容不同会被判为冲突，
// 所以提交前把请求键和完整请求体一起保存。
function readDraft(): SubmitBody | null {
  const raw = sessionStorage.getItem(DRAFT_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as SubmitBody;
  } catch {
    return null;
  }
}

const noopSubscribe = () => () => undefined;

function rejectionMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return "提交未完成，请重试";
  switch (error.code) {
    case "INSUFFICIENT_CREDITS":
      return "这个规格没有可用次数了，请先购买或等待到账。";
    case "SPEC_MISMATCH":
      return "没有与所选规格匹配的权益包。";
    case "APPLICATION_UNAVAILABLE":
      return "该应用当前不可提交，请稍后再试。";
    case "QUEUE_FULL":
      return "排队已满，本次没有冻结次数，请稍后再提交。";
    case "TOO_MANY_ACTIVE_TASKS":
      return "你进行中的任务太多了，等已有任务结束后再提交。";
    case "SUBMISSION_BUSY":
      return "提交繁忙，本次没有冻结次数，请稍后重试。";
    case "SUBMISSION_DISABLED":
      return "当前环境未开启任务提交。";
    case "REQUEST_CONFLICT":
      return "这次请求与之前的提交冲突，请重新提交。";
    case "INVALID_REQUEST":
      return "请检查提示词、素材和所选规格。";
    default:
      return error.message;
  }
}

// 提交状态机：editing → submitting → 接受（跳转任务页）| 拒绝（回到编辑）| 结果未知（可找回）。
export function useTaskSubmission() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [rejection, setRejection] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);

  // 刷新页面后也能从 sessionStorage 恢复未确认的提交。
  const draft = useSyncExternalStore(
    noopSubscribe,
    useCallback(() => {
      void revision;
      return sessionStorage.getItem(DRAFT_KEY);
    }, [revision]),
    () => null,
  );
  const pendingDraft = draft ? readDraft() : null;

  const setStoredDraft = useCallback((body: SubmitBody | null) => {
    if (body) sessionStorage.setItem(DRAFT_KEY, JSON.stringify(body));
    else sessionStorage.removeItem(DRAFT_KEY);
    setRevision((value) => value + 1);
  }, []);

  const mutation = useMutation({
    mutationFn: async (body: SubmitBody) =>
      (await api("/generation/tasks", taskSchema, { method: "POST", body })).data,
    onMutate: (body) => {
      setRejection(null);
      setStoredDraft(body);
    },
    onSuccess: (task) => {
      setStoredDraft(null);
      void queryClient.invalidateQueries({ queryKey: ["entitlement"] });
      void queryClient.invalidateQueries({ queryKey: ["tasks"] });
      queryClient.setQueryData(["task", task.id], task);
      router.replace(`/tasks/${task.id}`);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.outcomeUnknown) return;
      // 确定被拒绝：后端没有留下任务，清掉草稿以便用新请求键提交。
      setStoredDraft(null);
      setRejection(rejectionMessage(error));
    },
  });

  return {
    submit: mutation.mutate,
    submitting: mutation.isPending,
    rejection,
    // 有草稿且不在提交中，说明上次提交没有得到确定答复。
    unknownDraft: mutation.isPending ? null : pendingDraft,
    recover: () => {
      if (pendingDraft) mutation.mutate(pendingDraft);
    },
    discardDraft: () => setStoredDraft(null),
  };
}
