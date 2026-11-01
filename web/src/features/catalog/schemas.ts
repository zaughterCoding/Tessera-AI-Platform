import { z } from "zod";
import { longString } from "@/shared/api/types";

export const categorySchema = z.object({
  id: longString,
  name: z.string(),
  icon: z.string().nullish(),
});
export type Category = z.infer<typeof categorySchema>;

export const applicationSummarySchema = z.object({
  id: longString,
  categoryId: longString.nullish(),
  categoryName: z.string().nullish(),
  name: z.string(),
  summary: z.string().nullish(),
  capabilities: z.string().nullish(),
  coverUrl: z.string().nullish(),
  priceFrom: longString.nullish(),
  score: z.number().nullish(),
  reviewCount: z.number().nullish(),
  soldCount: z.number().nullish(),
  applicationVersion: z.number().nullish(),
});
export type ApplicationSummary = z.infer<typeof applicationSummarySchema>;

export const applicationDetailSchema = applicationSummarySchema.extend({
  description: z.string().nullish(),
  modelName: z.string().nullish(),
  modelVersion: z.string().nullish(),
  publishedAt: z.string().nullish(),
});
export type ApplicationDetail = z.infer<typeof applicationDetailSchema>;

export const productSchema = z.object({
  id: longString,
  applicationId: longString,
  title: z.string(),
  subtitle: z.string().nullish(),
  rules: z.string().nullish(),
  price: longString,
  credits: z.number(),
  validDays: z.number(),
  saleMode: z.number(),
  applicationVersion: z.number(),
  modelVersion: z.string().nullish(),
  imageSize: z.string().nullish(),
  outputCount: z.number(),
  orderable: z.boolean(),
});
export type Product = z.infer<typeof productSchema>;

export const popularityItemSchema = z.object({
  rank: z.number(),
  applicationId: longString,
  name: z.string(),
  summary: z.string().nullish(),
  coverUrl: z.string().nullish(),
  heat: longString.nullish(),
});
export type PopularityItem = z.infer<typeof popularityItemSchema>;

export const APPLICATION_SORTS = [
  { value: "score", label: "评分最高" },
  { value: "price", label: "价格最低" },
  { value: "newest", label: "最新上架" },
] as const;
export type ApplicationSort = (typeof APPLICATION_SORTS)[number]["value"];
