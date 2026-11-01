import type { Metadata } from "next";
import { TaskList } from "@/features/generation/task-list";
import { LinkButton } from "@/shared/ui/button";
import { PageHeader } from "@/shared/ui/layout";
import { parsePage, type SearchParams } from "@/shared/url";

export const metadata: Metadata = { title: "我的任务" };

export default async function TasksPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const page = parsePage((await searchParams).page);
  return (
    <>
      <PageHeader title="我的任务" actions={<LinkButton href="/studio">开始创作</LinkButton>} />
      <TaskList page={page} />
    </>
  );
}
