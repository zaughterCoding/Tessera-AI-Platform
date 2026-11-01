import { z } from "zod";
import { longString } from "@/shared/api/types";
import type { Tone } from "@/shared/ui/feedback";

export const ORDER_STATUSES = ["UNPAID", "PAID", "CANCELLED"] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

export const orderSchema = z.object({
  id: longString,
  campaignId: longString.nullish(),
  productId: longString,
  applicationId: longString,
  applicationName: z.string(),
  applicationVersion: z.number().nullish(),
  productTitle: z.string(),
  productSubtitle: z.string().nullish(),
  productRules: z.string().nullish(),
  modelVersion: z.string().nullish(),
  imageSize: z.string().nullish(),
  outputCount: z.number().nullish(),
  credits: z.number().nullish(),
  validDays: z.number().nullish(),
  amount: longString,
  status: z.string(),
  funding: z.string().nullish(),
  simulatedPayment: z.boolean().nullish(),
  expireAt: z.string().nullish(),
  paidAt: z.string().nullish(),
  closedAt: z.string().nullish(),
  createdAt: z.string().nullish(),
  entitlementNotice: z.string().nullish(),
  settlementNotice: z.string().nullish(),
});
export type Order = z.infer<typeof orderSchema>;

export const ORDER_STATUS_VIEW = {
  UNPAID: { label: "待支付", tone: "warning" },
  PAID: { label: "已支付", tone: "success" },
  CANCELLED: { label: "已关闭", tone: "neutral" },
} as const satisfies Record<OrderStatus, { label: string; tone: Tone }>;

export function orderStatusView(status: string): { label: string; tone: Tone } {
  return ORDER_STATUS_VIEW[status as OrderStatus] ?? { label: status, tone: "neutral" };
}

export function entitlementDelivered(order: Order): boolean {
  return Boolean(order.entitlementNotice?.includes("已到账") || order.entitlementNotice?.includes("已过期"));
}

export function entitlementPending(order: Order): boolean {
  return order.status === "PAID" && !entitlementDelivered(order);
}
