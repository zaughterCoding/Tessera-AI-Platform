import "server-only";
import { notFound } from "next/navigation";
import { cache } from "react";
import { z } from "zod";
import { ApiError, serverApi } from "@/shared/api/server";
import {
  applicationDetailSchema,
  applicationSummarySchema,
  categorySchema,
  popularityItemSchema,
  productSchema,
  type ApplicationSort,
} from "./schemas";

export const APPLICATION_PAGE_SIZE = 12;

export async function fetchCategories() {
  return (await serverApi("/catalog/categories", z.array(categorySchema))).data;
}

export async function fetchApplications(params: {
  categoryId?: string | undefined;
  keyword?: string | undefined;
  sort?: ApplicationSort | undefined;
  page?: number | undefined;
  size?: number | undefined;
}) {
  return serverApi("/catalog/applications", z.array(applicationSummarySchema), { params });
}

async function orNotFound<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

// 元数据和页面共用一次查询；不存在或已下架一律 404。
export const fetchApplication = cache(async (id: string) => {
  return (await orNotFound(serverApi(`/catalog/applications/${id}`, applicationDetailSchema))).data;
});

// 商品与应用并行查询，两者都要把 404 转成 notFound，否则结果取决于谁先返回。
export async function fetchProducts(applicationId: string) {
  return (await orNotFound(serverApi(`/catalog/applications/${applicationId}/products`, z.array(productSchema)))).data;
}

// 热榜依赖可选的分析链路，不可用时首页其他内容照常展示。
export async function fetchPopularity(limit: number) {
  try {
    return (await serverApi("/analytics/popularity", z.array(popularityItemSchema), { params: { minutes: 60, limit } })).data;
  } catch {
    return [];
  }
}
