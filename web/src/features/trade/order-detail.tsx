"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { reservationSchema } from "@/features/campaign/schemas";
import { api, ApiError } from "@/shared/api/client";
import { formatCountdown, formatMoney, formatTime } from "@/shared/format";
import { Button, LinkButton } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { Badge, Notice } from "@/shared/ui/feedback";
import { Card, DescriptionList, PageHeader } from "@/shared/ui/layout";
import { QueryState } from "@/shared/ui/query-state";
import { Steps, type Step } from "@/shared/ui/steps";
import { useToast } from "@/shared/ui/toast";
import { useCountdown } from "@/shared/use-countdown";
import { entitlementDelivered, entitlementPending, orderSchema, orderStatusView, type Order } from "./schemas";

// 到账是异步的，等一段时间后停止自动刷新并告知用户，不无限轮询。
const MAX_ENTITLEMENT_POLLS = 40;
const POLL_MS = 3000;

function orderSteps(order: Order): Step[] {
  const closed = order.status === "CANCELLED";
  const paid = order.status === "PAID";
  return [
    { label: "订单已创建", state: "done", detail: formatTime(order.createdAt) },
    closed
      ? { label: "订单已关闭", state: "failed", detail: order.settlementNotice ?? undefined }
      : {
          label: order.funding === "FREE" ? "免费领取" : "模拟支付",
          state: paid ? "done" : "active",
          detail: paid ? formatTime(order.paidAt) : "等待支付",
        },
    {
      label: "权益到账",
      state: closed ? "pending" : entitlementDelivered(order) ? "done" : paid ? "active" : "pending",
      detail: order.entitlementNotice ?? undefined,
    },
  ];
}

export function OrderDetail({ id }: { id: string }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [confirmCancel, setConfirmCancel] = useState(false);

  const query = useQuery({
    queryKey: ["order", id],
    queryFn: async () => (await api(`/trade/orders/${id}`, orderSchema)).data,
    refetchInterval: (current) => {
      const order = current.state.data;
      if (!order) return false;
      const waitingExpiry = order.status === "UNPAID" && order.expireAt && Date.parse(`${order.expireAt}Z`) <= Date.now();
      const waitingGrant = entitlementPending(order) && current.state.dataUpdateCount < MAX_ENTITLEMENT_POLLS;
      return waitingExpiry || waitingGrant ? POLL_MS : false;
    },
  });
  const order = query.data;

  const reservation = useQuery({
    queryKey: ["reservation", id],
    enabled: Boolean(order?.campaignId),
    queryFn: async () => (await api(`/campaign/reservations/${id}`, reservationSchema)).data,
  });

  function applyOrder(next: Order) {
    queryClient.setQueryData(["order", id], next);
    void queryClient.invalidateQueries({ queryKey: ["orders"] });
    void queryClient.invalidateQueries({ queryKey: ["entitlement"] });
    void queryClient.invalidateQueries({ queryKey: ["reservation", id] });
  }

  const onActionError = (error: Error) => {
    toast(error instanceof ApiError ? error.message : "操作未完成", "error");
    void query.refetch();
  };

  const pay = useMutation({
    mutationFn: async (amount: string) =>
      (await api(`/trade/orders/${id}/pay`, orderSchema, { method: "POST", body: { expectedAmount: amount } })).data,
    onSuccess: (next) => {
      applyOrder(next);
      toast("模拟支付成功，权益到账后即可使用", "success");
    },
    onError: onActionError,
  });

  const cancel = useMutation({
    mutationFn: async () => (await api(`/trade/orders/${id}/cancel`, orderSchema, { method: "POST" })).data,
    onSuccess: (next) => {
      applyOrder(next);
      setConfirmCancel(false);
      toast("订单已关闭");
    },
    onError: (error) => {
      setConfirmCancel(false);
      onActionError(error);
    },
  });

  const remaining = useCountdown(order?.status === "UNPAID" ? order.expireAt : null);

  return (
    <QueryState query={query} backHref="/orders">
      {(current) => {
        const status = orderStatusView(current.status);
        return (
          <>
            <PageHeader title="订单详情" description={`订单号 ${current.id}`} actions={<Badge tone={status.tone}>{status.label}</Badge>} />
            <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
              <div className="flex min-w-0 flex-col gap-4">
                <Card>
                  <h2 className="font-medium">{current.applicationName} · {current.productTitle}</h2>
                  {current.productSubtitle && <p className="text-sm text-ink-muted">{current.productSubtitle}</p>}
                  <div className="mt-4">
                    <DescriptionList
                      items={[
                        { label: "金额", value: formatMoney(current.amount) },
                        { label: "次数", value: `${current.credits ?? "—"} 次` },
                        { label: "规格", value: `${current.modelVersion ?? "—"} / ${current.imageSize ?? "—"} / 每次 ${current.outputCount ?? "—"} 张` },
                        { label: "有效期", value: `${current.validDays ?? "—"} 天` },
                      ]}
                    />
                  </div>
                  {current.productRules && <p className="mt-4 text-xs text-ink-muted">{current.productRules}</p>}
                </Card>
                {reservation.data && (
                  <Notice title="限量活动" tone={reservation.data.state === "REVIEW" ? "warning" : "info"}>
                    {reservation.data.reservationNotice}
                  </Notice>
                )}
                {current.settlementNotice && <Notice>{current.settlementNotice}</Notice>}
                {entitlementPending(current) && (
                  <Notice tone="warning" title="权益还在发放中">
                    发放是异步进行的，支付成功不代表已到账，稍后可刷新查看。
                  </Notice>
                )}
                {entitlementDelivered(current) && (
                  <div><LinkButton href={`/studio?app=${current.applicationId}`}>去创作</LinkButton></div>
                )}
              </div>
              <div className="flex min-w-0 flex-col gap-4">
                <Card><Steps steps={orderSteps(current)} /></Card>
                {current.status === "UNPAID" && (
                  <Card className="flex flex-col gap-3">
                    <p className="text-sm text-ink-soft">
                      {remaining === 0 ? "支付时间已到，订单正在关闭" : `请在 ${remaining === null ? "—" : formatCountdown(remaining)} 内完成支付`}
                    </p>
                    <Button loading={pay.isPending} disabled={remaining === 0} onClick={() => pay.mutate(current.amount)}>
                      模拟支付 {formatMoney(current.amount)}
                    </Button>
                    <Button variant="secondary" onClick={() => setConfirmCancel(true)}>取消订单</Button>
                    <p className="text-xs text-ink-muted">演示环境，不发生真实扣款。</p>
                  </Card>
                )}
              </div>
            </div>
            <ConfirmDialog
              open={confirmCancel}
              onOpenChange={setConfirmCancel}
              title="取消这个订单？"
              description="关闭后不能再支付。限量活动的名额会被释放。"
              confirmLabel="确认取消"
              danger
              loading={cancel.isPending}
              onConfirm={() => cancel.mutate()}
            />
          </>
        );
      }}
    </QueryState>
  );
}
