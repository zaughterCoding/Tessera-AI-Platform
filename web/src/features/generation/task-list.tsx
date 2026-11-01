"use client";

import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { api } from "@/shared/api/client";
import { formatTime } from "@/shared/format";
import { LinkButton } from "@/shared/ui/button";
import { Badge, EmptyState } from "@/shared/ui/feedback";
import { Card } from "@/shared/ui/layout";
import Link from "@/shared/ui/link";
import { Pagination } from "@/shared/ui/pagination";
import { QueryState } from "@/shared/ui/query-state";
import { totalPagesOf } from "@/shared/url";
import { accountingView, taskSchema, taskStateView } from "./schemas";

const PAGE_SIZE = 10;
const LIST_POLL_MS = 5000;

export function TaskList({ page }: { page: number }) {
  const query = useQuery({
    queryKey: ["tasks", page],
    queryFn: () => api("/generation/tasks", z.array(taskSchema), { params: { page, size: PAGE_SIZE } }),
    // 只有当前页存在进行中的任务才轮询。
    refetchInterval: (current) =>
      current.state.data?.data.some((task) => taskStateView(task.state).active) ? LIST_POLL_MS : false,
  });

  return (
    <QueryState query={query}>
      {({ data: tasks, total }) =>
        tasks.length ? (
          <>
            <ul className="flex flex-col gap-3">
              {tasks.map((task) => {
                const state = taskStateView(task.state);
                const accounting = accountingView(task.accountingState);
                return (
                  <li key={task.id}>
                    <Card className="flex flex-col gap-1.5">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge tone={state.tone} pulse={state.active}>{state.label}</Badge>
                        <Badge tone={accounting.tone}>{accounting.label}</Badge>
                        {task.simulatedChannel && <Badge>模拟渠道</Badge>}
                      </div>
                      <Link href={`/tasks/${task.id}`} className="font-medium hover:text-accent">
                        {task.applicationName}：<span className="font-normal">{task.prompt}</span>
                      </Link>
                      <p className="text-xs text-ink-muted">
                        {task.imageSize} · 结果 {task.results.filter((result) => result.downloadAvailable).length}/{task.outputCount} · 冻结 {task.frozenUnits} 次 · {formatTime(task.createdAt)}
                      </p>
                    </Card>
                  </li>
                );
              })}
            </ul>
            <Pagination
              page={page}
              totalPages={totalPagesOf(total, PAGE_SIZE)}
              hrefFor={(target) => (target === 1 ? "/tasks" : `/tasks?page=${target}`)}
            />
          </>
        ) : (
          <EmptyState
            title="还没有任务"
            description="用已到账的权益提交第一个生成任务。"
            action={<LinkButton href="/studio">开始创作</LinkButton>}
          />
        )
      }
    </QueryState>
  );
}
