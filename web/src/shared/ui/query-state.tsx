"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ApiError } from "@/shared/api/envelope";
import { LinkButton } from "./button";
import { EmptyState, ErrorState, Skeleton } from "./feedback";

export function ListSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div role="status" aria-label="正在加载" className="flex flex-col gap-3">
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-24 w-full" />
      ))}
    </div>
  );
}

// 统一处理查询的加载与失败状态；刷新失败时保留已有数据。
export function QueryState<T>({
  query,
  backHref,
  skeleton,
  children,
}: {
  query: UseQueryResult<T>;
  backHref?: string;
  skeleton?: ReactNode;
  children: (data: T) => ReactNode;
}) {
  if (query.data !== undefined) return <>{children(query.data)}</>;
  if (query.isPending) return <>{skeleton ?? <ListSkeleton />}</>;
  const error = query.error;
  if (error instanceof ApiError && error.status === 404) {
    return (
      <EmptyState
        title="内容不存在"
        description="可能已被删除，或者你没有查看权限。"
        action={backHref ? <LinkButton href={backHref} variant="secondary">返回列表</LinkButton> : undefined}
      />
    );
  }
  if (error instanceof ApiError && error.status === 401) {
    return <EmptyState title="登录已过期" description="请重新登录后查看。" />;
  }
  return (
    <ErrorState
      message={error instanceof ApiError ? error.message : "请稍后重试"}
      requestId={error instanceof ApiError ? error.requestId : null}
      onRetry={() => void query.refetch()}
    />
  );
}
