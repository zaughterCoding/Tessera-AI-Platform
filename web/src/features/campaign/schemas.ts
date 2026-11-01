import { z } from "zod";
import { longString } from "@/shared/api/types";
import { orderSchema } from "@/features/trade/schemas";
import type { Tone } from "@/shared/ui/feedback";

export const offerSchema = z.object({
  id: longString,
  productId: longString,
  applicationId: longString,
  applicationName: z.string(),
  productTitle: z.string(),
  productSubtitle: z.string().nullish(),
  productRules: z.string().nullish(),
  modelVersion: z.string().nullish(),
  imageSize: z.string().nullish(),
  price: longString,
  credits: z.number(),
  validDays: z.number(),
  outputCount: z.number(),
  beginAt: z.string(),
  endAt: z.string(),
  remainingStock: z.number().nullish(),
  state: z.string(),
  sellable: z.boolean(),
  displayNotice: z.string().nullish(),
});
export type Offer = z.infer<typeof offerSchema>;

export const admissionSchema = z.object({
  orderId: longString,
  campaignId: longString,
  state: z.string(),
  requestKey: z.string().nullish(),
  existing: z.boolean().nullish(),
  acceptanceNotice: z.string().nullish(),
  reservationNotice: z.string().nullish(),
  entitlementNotice: z.string().nullish(),
});
export type Admission = z.infer<typeof admissionSchema>;

export const reservationSchema = z.object({
  orderId: longString,
  campaignId: longString,
  state: z.string(),
  stockReleased: z.boolean().nullish(),
  requestKey: z.string().nullish(),
  order: orderSchema.nullish(),
  acceptanceNotice: z.string().nullish(),
  reservationNotice: z.string().nullish(),
  entitlementNotice: z.string().nullish(),
});
export type Reservation = z.infer<typeof reservationSchema>;

const OFFER_STATES = {
  DRAFT: { label: "草稿", tone: "neutral" },
  UPCOMING: { label: "即将开始", tone: "info" },
  OPEN: { label: "进行中", tone: "success" },
  SOLD_OUT: { label: "已售罄", tone: "neutral" },
  ENDED: { label: "已结束", tone: "neutral" },
  PAUSED: { label: "已暂停", tone: "warning" },
} as const satisfies Record<string, { label: string; tone: Tone }>;

export function offerStateView(state: string): { label: string; tone: Tone } {
  return OFFER_STATES[state as keyof typeof OFFER_STATES] ?? { label: state, tone: "neutral" };
}

export function admissionErrorMessage(code: string, fallback: string): string {
  if (code === "SOLD_OUT") return "已售罄，名额被抢完了。";
  if (code === "CONFLICT") return fallback || "当前状态不能参与，请刷新后查看。";
  return fallback;
}
