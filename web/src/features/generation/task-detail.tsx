"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download } from "lucide-react";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/shared/api/client";
import { formatBytes, formatTime } from "@/shared/format";
import { Button, LinkButton } from "@/shared/ui/button";
import { Dialog } from "@/shared/ui/dialog";
import { Badge, Notice } from "@/shared/ui/feedback";
import { Field, Textarea } from "@/shared/ui/field";
import { Card, DescriptionList, PageHeader } from "@/shared/ui/layout";
import Link from "@/shared/ui/link";
import { QueryState } from "@/shared/ui/query-state";
import { Steps, type Step } from "@/shared/ui/steps";
import { useToast } from "@/shared/ui/toast";
import { nextPollMs, trackingPaused } from "./polling";
import {
  accountingView,
  cancelStateLabel,
  resultContentUrl,
  resultStateLabel,
  taskSchema,
  taskStateView,
  type Task,
} from "./schemas";
import { ShareForm } from "./share-form";

const STAGE_OF: Record<string, number> = {
  QUEUED: 1,
  DISPATCHING: 1,
  SUBMITTED: 2,
  RUNNING: 2,
  RECONCILING: 2,
  CANCEL_REQUESTED: 2,
  REVIEW: 2,
  RESULT_PENDING: 3,
  SUCCEEDED: 5,
};

function taskSteps(task: Task): Step[] {
  const failed = ["FAILED", "CANCELLED", "FAILED_PENDING_FOLLOWUP", "CANCELLED_PENDING_FOLLOWUP", "QUEUE_TIMEOUT"].includes(task.state);
  const stage = STAGE_OF[task.state] ?? 1;
  const stepState = (index: number): Step["state"] => {
    if (failed) return "pending";
    if (stage > index) return "done";
    return stage === index ? "active" : "pending";
  };
  const steps: Step[] = [
    { label: "已受理", state: "done", detail: `冻结 ${task.frozenUnits} 次 · ${formatTime(task.createdAt)}` },
    { label: "排队调度", state: failed ? "done" : stepState(1) },
    { label: "上游生成", state: stepState(2), detail: task.state === "REVIEW" ? "无法自动确认，等待人工核对，次数保持冻结" : undefined },
    { label: "保存并校验结果", state: stepState(3) },
    { label: "交付并结算", state: stage === 5 ? "done" : "pending", detail: task.settledAt ? formatTime(task.settledAt) : undefined },
  ];
  if (failed) steps.push({ label: taskStateView(task.state).label, state: "failed", detail: task.settlement?.description ?? undefined });
  return steps;
}

export function TaskDetail({ id }: { id: string }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [pollBase, setPollBase] = useState(0);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");

  const query = useQuery({
    queryKey: ["task", id],
    queryFn: async () => (await api(`/generation/tasks/${id}`, taskSchema)).data,
    // 后台标签页不轮询（默认行为），回到页面时立即刷新。
    refetchInterval: (current) => {
      const task = current.state.data;
      return task ? nextPollMs(task.state, current.state.dataUpdateCount - pollBase) : false;
    },
  });
  const task = query.data;
  const updateCount = queryClient.getQueryState(["task", id])?.dataUpdateCount ?? 0;

  // 状态变化时同步权益和列表，结算结果才能立刻反映到其他页面。
  const lastState = useRef<string | null>(null);
  useEffect(() => {
    if (!task) return;
    const key = `${task.state}|${task.accountingState}`;
    if (lastState.current && lastState.current !== key) {
      void queryClient.invalidateQueries({ queryKey: ["entitlement"] });
      void queryClient.invalidateQueries({ queryKey: ["tasks"] });
    }
    lastState.current = key;
  }, [task, queryClient]);

  const cancel = useMutation({
    mutationFn: async () =>
      (await api(`/generation/tasks/${id}/cancel`, taskSchema, { method: "POST", body: cancelReason.trim() ? { reason: cancelReason.trim() } : {} })).data,
    onSuccess: (next) => {
      queryClient.setQueryData(["task", id], next);
      setCancelOpen(false);
      setCancelReason("");
      setPollBase(updateCount);
      toast("已登记取消，等待确认", "info");
    },
    onError: (error) => {
      toast(error instanceof ApiError ? error.message : "取消未完成", "error");
    },
  });

  const paused = task ? trackingPaused(task.state, updateCount - pollBase) : false;

  return (
    <QueryState query={query} backHref="/tasks">
      {(current) => {
        const state = taskStateView(current.state);
        const accounting = accountingView(current.accountingState);
        const deliveredResults = current.results.filter((result) => result.downloadAvailable);
        return (
          <>
            <PageHeader
              title={current.applicationName}
              description={`任务号 ${current.id}`}
              actions={
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={state.tone} pulse={state.active && !paused}>{state.label}</Badge>
                  <Badge tone={accounting.tone}>{accounting.label}</Badge>
                  <Badge tone={current.simulatedChannel ? "warning" : "info"}>{current.simulatedChannel ? "模拟渠道" : "真实渠道"}</Badge>
                </div>
              }
            />

            <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
              <div className="flex min-w-0 flex-col gap-4">
                {paused && (
                  <Notice
                    tone="info"
                    title="已暂停自动刷新"
                    action={<Button size="sm" variant="secondary" onClick={() => { setPollBase(updateCount); void query.refetch(); }}>继续跟踪</Button>}
                  >
                    任务还在进行中，这段时间没有变化。
                  </Notice>
                )}
                {current.state === "REVIEW" && (
                  <Notice tone="warning" title="等待人工核对">
                    系统无法自动确认上游结果。次数保持冻结，不会被扣除也不会丢失。
                  </Notice>
                )}
                {current.stateNote && current.state !== "SUCCEEDED" && <Notice>{current.stateNote}</Notice>}
                {current.settlement?.description && (
                  <Notice tone={current.settlement.kind === "CONSUME" ? "success" : "info"} title="结算">
                    {current.settlement.description}
                  </Notice>
                )}

                <section aria-labelledby="results-heading">
                  <h2 id="results-heading" className="mb-3 font-medium">
                    结果 {deliveredResults.length}/{current.outputCount}
                    <span className="ml-2 text-xs font-normal text-ink-muted">仅自己可见</span>
                  </h2>
                  {current.results.length ? (
                    <ul className="grid gap-3 sm:grid-cols-2">
                      {current.results.map((result) => (
                        <li key={result.id} className="overflow-hidden rounded-lg border border-line bg-surface">
                          <div className="relative aspect-square bg-sunken">
                            {result.downloadAvailable ? (
                              <Image
                                src={resultContentUrl(current.id, result.id)}
                                alt={`第 ${result.position + 1} 张生成结果`}
                                fill
                                sizes="(min-width: 640px) 30vw, 100vw"
                                className="object-contain"
                              />
                            ) : (
                              <p className="flex size-full items-center justify-center text-sm text-ink-muted">{resultStateLabel(result.state)}</p>
                            )}
                          </div>
                          <div className="flex items-center justify-between gap-2 p-3 text-xs text-ink-muted">
                            <span>{resultStateLabel(result.state)} · {formatBytes(result.sizeBytes)}</span>
                            {result.downloadAvailable && (
                              <a href={resultContentUrl(current.id, result.id)} download className="inline-flex items-center gap-1 text-accent hover:underline">
                                <Download aria-hidden className="size-3.5" />
                                下载
                              </a>
                            )}
                          </div>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="rounded-lg border border-dashed border-line-strong p-6 text-center text-sm text-ink-muted">
                      {state.active ? "结果生成后显示在这里，可以离开此页面，稍后回来查看。" : "这个任务没有结果。"}
                    </p>
                  )}
                </section>

                {current.state === "SUCCEEDED" && deliveredResults.length > 0 && <ShareForm task={current} />}
              </div>

              <div className="flex min-w-0 flex-col gap-4">
                <Card><Steps steps={taskSteps(current)} /></Card>
                <Card>
                  <DescriptionList
                    items={[
                      { label: "规格", value: `${current.modelVersion} / ${current.imageSize} / ${current.outputCount} 张` },
                      { label: "渠道", value: current.channelDisplayName ?? "—" },
                      {
                        label: "冻结来源",
                        value: current.grantId ? <Link href={`/entitlements/${current.grantId}`} className="text-accent">权益包 {current.grantId}</Link> : "—",
                      },
                    ]}
                  />
                  <div className="mt-4 text-sm">
                    <p className="text-ink-muted">提示词</p>
                    <p className="break-words whitespace-pre-wrap">{current.prompt}</p>
                    {current.negativePrompt && (
                      <>
                        <p className="mt-2 text-ink-muted">负向提示词</p>
                        <p className="break-words whitespace-pre-wrap">{current.negativePrompt}</p>
                      </>
                    )}
                  </div>
                </Card>

                {current.cancelRequest && (
                  <Notice title={cancelStateLabel(current.cancelRequest.state)}>
                    {current.cancelRequest.reason && <span>原因：{current.cancelRequest.reason}</span>}
                  </Notice>
                )}
                {current.canCancel && <Button variant="danger" onClick={() => setCancelOpen(true)}>取消任务</Button>}
                <LinkButton href={`/studio?app=${current.applicationId}`} variant="secondary">再创作一个</LinkButton>
              </div>
            </div>

            <Dialog
              open={cancelOpen}
              onOpenChange={setCancelOpen}
              title="取消这个任务？"
              description="取消需要确认：排队中的任务很快结束；已提交上游的要等上游答复。如果上游先完成，任务仍会交付并消费次数。"
            >
              <div className="mt-4">
                <Field label="原因（可选）">
                  <Textarea value={cancelReason} maxLength={200} onChange={(event) => setCancelReason(event.target.value)} className="min-h-16" />
                </Field>
              </div>
              <div className="mt-4 flex justify-end gap-2">
                <Button variant="secondary" onClick={() => setCancelOpen(false)}>不取消</Button>
                <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>确认取消</Button>
              </div>
            </Dialog>
          </>
        );
      }}
    </QueryState>
  );
}
