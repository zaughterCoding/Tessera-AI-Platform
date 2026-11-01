"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { goToLogin, useSession } from "@/features/auth/session-provider";
import { api, ApiError } from "@/shared/api/client";
import { formatCountdown, formatMoney, formatTime } from "@/shared/format";
import { clearRequestKey, getRequestKey, peekRequestKey, rememberRequestKey } from "@/shared/request-key";
import { Button, LinkButton } from "@/shared/ui/button";
import { Badge, Notice } from "@/shared/ui/feedback";
import { Card } from "@/shared/ui/layout";
import { Steps, type Step } from "@/shared/ui/steps";
import { useToast } from "@/shared/ui/toast";
import { useCountdown } from "@/shared/use-countdown";
import { orderStatusView } from "@/features/trade/schemas";
import { admissionErrorMessage, admissionSchema, offerSchema, offerStateView, reservationSchema, type Offer, type Reservation } from "./schemas";

const TRACK_MS = 2000;
const MAX_TRACK_POLLS = 30;
const OFFER_REFRESH_MS = 5000;

function reservationSteps(reservation: Reservation): Step[] {
  const { state, order } = reservation;
  const created = state === "SUCCESS";
  const failed = state === "FAILED";
  const paid = order?.status === "PAID";
  const closed = order?.status === "CANCELLED";
  const delivered = order?.entitlementNotice?.includes("已到账");
  return [
    { label: "已受理", state: "done", detail: "受理只代表进入处理，不代表建单成功" },
    {
      label: "创建订单",
      state: failed ? "failed" : created ? "done" : "active",
      detail: state === "REVIEW" ? "等待人工复核，请勿重复提交" : undefined,
    },
    { label: "完成支付", state: closed ? "failed" : paid ? "done" : created ? "active" : "pending" },
    { label: "权益到账", state: delivered ? "done" : paid ? "active" : "pending" },
  ];
}

export function CampaignPanel({ initialOffer }: { initialOffer: Offer }) {
  const { user } = useSession();
  const toast = useToast();
  const queryClient = useQueryClient();
  const id = initialOffer.id;
  const scope = `campaign:${id}`;

  // 请求键恢复必须在客户端挂载后进行，服务端没有 sessionStorage。
  const [trackedKey, setTrackedKey] = useState<string | null>(null);
  const [outcomeUnknown, setOutcomeUnknown] = useState(false);

  useEffect(() => {
    if (!user) return;
    const stored = peekRequestKey(scope);
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 浏览器存储只能在挂载后读取
    if (stored) setTrackedKey(stored);
  }, [scope, user]);

  const offerQuery = useQuery({
    queryKey: ["offer", id],
    initialData: initialOffer,
    queryFn: async () => (await api(`/campaign/offers/${id}`, offerSchema)).data,
    refetchInterval: (current) => (["OPEN", "UPCOMING"].includes(current.state.data?.state ?? "") ? OFFER_REFRESH_MS : false),
  });
  const offer = offerQuery.data;

  const tracking = useQuery({
    queryKey: ["campaign-reservation", id, trackedKey],
    enabled: Boolean(user && trackedKey),
    retry: false,
    queryFn: async () =>
      (await api("/campaign/reservations/by-request", reservationSchema, { params: { campaignId: id, requestKey: trackedKey } })).data,
    refetchInterval: (current) => {
      const data = current.state.data;
      const processing = !data || data.state === "PROCESSING" || (data.state === "SUCCESS" && !data.order);
      return processing && current.state.dataUpdateCount < MAX_TRACK_POLLS ? TRACK_MS : false;
    },
  });
  const reservation = tracking.data;

  useEffect(() => {
    const failure = tracking.error;
    if (failure instanceof ApiError && failure.status === 404) {
      // 服务端没有这个请求键的记录，说明上次没有受理成功，可以重新参与。
      clearRequestKey(scope);
      // eslint-disable-next-line react-hooks/set-state-in-effect -- 响应外部查询结果
      setTrackedKey(null);
    }
  }, [tracking.error, scope]);

  // 资格已释放后清除请求键，下次参与生成新请求。
  const released = reservation && (reservation.state === "FAILED" || reservation.stockReleased);
  useEffect(() => {
    if (released) clearRequestKey(scope);
  }, [released, scope]);

  const admit = useMutation({
    mutationFn: async () =>
      (await api("/campaign/reservations", admissionSchema, {
        method: "POST",
        body: { campaignId: id, requestKey: getRequestKey(scope) },
      })).data,
    onSuccess: (admission) => {
      const key = admission.requestKey ?? getRequestKey(scope);
      rememberRequestKey(scope, key);
      setOutcomeUnknown(false);
      setTrackedKey(key);
      void queryClient.invalidateQueries({ queryKey: ["offer", id] });
    },
    onError: (error) => {
      if (error instanceof ApiError && error.outcomeUnknown) {
        setOutcomeUnknown(true);
        return;
      }
      clearRequestKey(scope);
      void queryClient.invalidateQueries({ queryKey: ["offer", id] });
      toast(error instanceof ApiError ? admissionErrorMessage(error.code, error.message) : "参与失败", "error");
    },
  });

  const state = offerStateView(offer.state);
  const countdownTarget = offer.state === "UPCOMING" ? offer.beginAt : offer.state === "OPEN" ? offer.endAt : null;
  const remaining = useCountdown(countdownTarget);

  return (
    <div className="flex flex-col gap-4">
      <Card className="flex flex-col gap-3">
        <div className="flex items-center justify-between gap-2">
          <Badge tone={state.tone} pulse={offer.state === "OPEN"}>{state.label}</Badge>
          {remaining !== null && (
            <span className="font-mono text-sm text-ink-soft">
              {offer.state === "UPCOMING" ? "距开始" : "距结束"} {formatCountdown(remaining)}
            </span>
          )}
        </div>
        <p className="text-2xl font-semibold text-accent">{formatMoney(offer.price)}</p>
        <p className="text-sm text-ink-soft">剩余 {offer.remainingStock ?? "—"} 份 · 截止 {formatTime(offer.endAt)}</p>
        {offer.displayNotice && <p className="text-xs text-ink-muted">{offer.displayNotice}</p>}

        {!reservation && !trackedKey && !outcomeUnknown && (
          user ? (
            <Button loading={admit.isPending} disabled={!offer.sellable} onClick={() => admit.mutate()}>
              {offer.sellable ? "立即抢购" : state.label}
            </Button>
          ) : (
            <Button disabled={!offer.sellable} onClick={goToLogin}>登录后抢购</Button>
          )
        )}

        {outcomeUnknown && (
          <Notice
            tone="warning"
            title="抢购结果未知"
            action={
              <Button size="sm" variant="secondary" loading={admit.isPending} onClick={() => admit.mutate()}>
                查询结果
              </Button>
            }
          >
            网络中断，请求可能已被受理。用同一个请求重试不会多占名额。
          </Notice>
        )}
      </Card>

      {reservation && (
        <Card className="flex flex-col gap-3">
          <h2 className="font-medium">我的抢购进度</h2>
          <Steps steps={reservationSteps(reservation)} />
          {reservation.reservationNotice && (
            <p role="status" className="text-sm text-ink-soft">{reservation.reservationNotice}</p>
          )}
          {reservation.order && (
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone={orderStatusView(reservation.order.status).tone}>{orderStatusView(reservation.order.status).label}</Badge>
              <LinkButton href={`/orders/${reservation.orderId}`} size="sm">查看订单</LinkButton>
            </div>
          )}
          {released && offer.sellable && (
            <Button
              variant="secondary"
              onClick={() => {
                setTrackedKey(null);
                queryClient.removeQueries({ queryKey: ["campaign-reservation", id] });
              }}
            >
              重新参与
            </Button>
          )}
        </Card>
      )}

      {tracking.isError && !(tracking.error instanceof ApiError && tracking.error.status === 404) && (
        <Notice tone="warning" title="暂时查不到抢购进度" action={<Button size="sm" variant="secondary" onClick={() => void tracking.refetch()}>重试</Button>}>
          请求已保留，稍后可以继续查询。
        </Notice>
      )}
    </div>
  );
}
