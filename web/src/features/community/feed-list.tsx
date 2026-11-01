"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import { api, ApiError } from "@/shared/api/client";
import { Button, LinkButton } from "@/shared/ui/button";
import { EmptyState, ErrorState } from "@/shared/ui/feedback";
import { ListSkeleton } from "@/shared/ui/query-state";
import { CreationGrid } from "./creation-card";
import { feedResultSchema } from "./schemas";

const FEED_SIZE = 12;

export function FeedList() {
  // 游标原样回传：同一时间戳的作品靠 offset 接续，翻页不会漏项。
  const query = useInfiniteQuery({
    queryKey: ["feed"],
    initialPageParam: { lastTime: undefined as string | undefined, offset: 0 },
    queryFn: async ({ pageParam }) =>
      (await api("/creations/feed", feedResultSchema, {
        params: { lastTime: pageParam.lastTime, offset: pageParam.offset, size: FEED_SIZE },
      })).data,
    getNextPageParam: (last) =>
      last.hasMore && last.nextTime ? { lastTime: last.nextTime, offset: last.nextOffset ?? 0 } : undefined,
  });

  if (query.isPending) return <ListSkeleton />;
  if (query.isError) {
    return (
      <ErrorState
        message={query.error instanceof ApiError ? query.error.message : "请稍后重试"}
        onRetry={() => void query.refetch()}
      />
    );
  }
  const creations = query.data.pages.flatMap((page) => page.records);
  if (!creations.length) {
    return (
      <EmptyState
        title="关注动态是空的"
        description="关注创作者后，他们新发布的作品会显示在这里。"
        action={<LinkButton href="/community" variant="secondary">去社区看看</LinkButton>}
      />
    );
  }
  return (
    <>
      <CreationGrid creations={creations} />
      {query.hasNextPage && (
        <div className="mt-6 flex justify-center">
          <Button variant="secondary" loading={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
            加载更多
          </Button>
        </div>
      )}
    </>
  );
}
