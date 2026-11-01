"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { goToLogin, useSession } from "@/features/auth/session-provider";
import type { Product } from "@/features/catalog/schemas";
import { api, ApiError } from "@/shared/api/client";
import { clearRequestKey, getRequestKey } from "@/shared/request-key";
import { Button, LinkButton } from "@/shared/ui/button";
import { Notice } from "@/shared/ui/feedback";
import { useToast } from "@/shared/ui/toast";
import { orderSchema } from "./schemas";

export function PurchaseButton({ product }: { product: Product }) {
  const { user } = useSession();
  const router = useRouter();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [outcomeUnknown, setOutcomeUnknown] = useState(false);
  const scope = `purchase:${product.id}`;

  const purchase = useMutation({
    mutationFn: async () =>
      (await api("/trade/orders", orderSchema, {
        method: "POST",
        body: { productId: product.id, requestKey: getRequestKey(scope) },
      })).data,
    onSuccess: (order) => {
      clearRequestKey(scope);
      void queryClient.invalidateQueries({ queryKey: ["orders"] });
      router.push(`/orders/${order.id}`);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.outcomeUnknown) {
        // 保留请求键：后端可能已建单，用同一个键重试只会拿回原订单。
        setOutcomeUnknown(true);
        return;
      }
      clearRequestKey(scope);
      toast(error instanceof ApiError ? error.message : "下单失败", "error");
    },
  });

  if (!product.orderable) {
    return <LinkButton href="/campaigns" variant="secondary">前往限量活动</LinkButton>;
  }
  if (!user) {
    return <Button onClick={goToLogin}>登录后购买</Button>;
  }
  if (outcomeUnknown) {
    return (
      <Notice
        tone="warning"
        title="下单结果未知"
        action={
          <Button size="sm" variant="secondary" loading={purchase.isPending} onClick={() => purchase.mutate()}>
            找回订单
          </Button>
        }
      >
        网络中断，订单可能已创建。重试不会重复下单。
      </Notice>
    );
  }
  return (
    <Button loading={purchase.isPending} onClick={() => purchase.mutate()}>
      {product.price === "0" ? "免费领取" : "立即购买"}
    </Button>
  );
}
