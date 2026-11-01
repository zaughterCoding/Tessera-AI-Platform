import { z } from "zod";
import { longString } from "@/shared/api/types";

export const balanceSchema = z.object({
  totalCredits: longString,
  availableCredits: longString,
  frozenCredits: longString,
  consumedCredits: longString,
  expiredCredits: longString,
  activeGrants: longString,
  expiredGrants: longString,
});
export type Balance = z.infer<typeof balanceSchema>;

export const grantSchema = z.object({
  id: longString,
  orderId: longString,
  productId: longString,
  applicationId: longString,
  applicationName: z.string(),
  applicationVersion: z.number(),
  modelVersion: z.string(),
  imageSize: z.string(),
  outputCount: z.number(),
  credits: z.number().nullish(),
  validDays: z.number().nullish(),
  sourceFunding: z.string().nullish(),
  totalCredits: z.number(),
  availableCredits: z.number(),
  frozenCredits: z.number(),
  consumedCredits: z.number(),
  expiredCredits: z.number(),
  effectiveFrom: z.string().nullish(),
  expiresAt: z.string().nullish(),
  status: z.string(),
  createdAt: z.string().nullish(),
});
export type Grant = z.infer<typeof grantSchema>;

export const ledgerEntrySchema = z.object({
  id: longString,
  grantId: longString,
  operationType: z.string(),
  deltaAvailable: z.number(),
  deltaFrozen: z.number(),
  deltaConsumed: z.number(),
  deltaExpired: z.number(),
  availableAfter: z.number(),
  frozenAfter: z.number(),
  consumedAfter: z.number(),
  expiredAfter: z.number(),
  reason: z.string().nullish(),
  referenceType: z.string().nullish(),
  referenceId: longString.nullish(),
  createdAt: z.string().nullish(),
});
export type LedgerEntry = z.infer<typeof ledgerEntrySchema>;

const OPERATION_LABELS: Record<string, string> = {
  GRANT: "发放",
  EXPIRE: "过期",
  CORRECTION: "更正",
  FREEZE: "冻结",
  CONSUME: "消费",
  RELEASE: "释放",
};

export function operationLabel(type: string): string {
  return OPERATION_LABELS[type] ?? type;
}
