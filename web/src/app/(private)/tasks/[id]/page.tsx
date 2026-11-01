import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { TaskDetail } from "@/features/generation/task-detail";
import { parseId } from "@/shared/url";

export const metadata: Metadata = { title: "任务详情" };

export default async function TaskPage({ params }: { params: Promise<{ id: string }> }) {
  const id = parseId((await params).id);
  if (!id) notFound();
  return <TaskDetail id={id} />;
}
