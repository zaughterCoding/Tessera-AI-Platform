import "server-only";
import { notFound } from "next/navigation";
import { cache } from "react";
import { z } from "zod";
import { getSessionToken } from "@/features/auth/current-user";
import { ApiError, serverApi } from "@/shared/api/server";
import { creationSchema } from "./schemas";

export const CREATION_PAGE_SIZE = 12;

const list = z.array(creationSchema);

export async function fetchCreations(sort: "latest" | "hot", page: number) {
  return serverApi(sort === "hot" ? "/creations/hot" : "/creations", list, {
    params: { limit: CREATION_PAGE_SIZE, offset: (page - 1) * CREATION_PAGE_SIZE },
  });
}

export async function fetchHotCreations(limit: number) {
  return (await serverApi("/creations/hot", list, { params: { limit, offset: 0 } })).data;
}

export async function fetchApplicationCreations(applicationId: string, limit: number) {
  return (await serverApi(`/creations/by-application/${applicationId}`, list, { params: { limit, offset: 0 } })).data;
}

export async function fetchUserCreations(userId: string, page: number) {
  return serverApi(`/creations/by-user/${userId}`, list, {
    params: { limit: CREATION_PAGE_SIZE, offset: (page - 1) * CREATION_PAGE_SIZE },
  });
}

// 公开接口只返回已发布作品；作者查看自己未公开的作品走本人接口。
// 所有渲染都是 no-store，个人数据不会进入共享缓存。
export const fetchCreation = cache(async (id: string) => {
  const token = await getSessionToken();
  try {
    return (await serverApi(`/creations/${id}`, creationSchema, token ? { token } : {})).data;
  } catch (error) {
    if (!(error instanceof ApiError) || (error.status !== 404 && error.status !== 401)) throw error;
  }
  if (!token) notFound();
  try {
    return (await serverApi(`/creations/mine/${id}`, creationSchema, { token })).data;
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 401)) notFound();
    throw error;
  }
});
