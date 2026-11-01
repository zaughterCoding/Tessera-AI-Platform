import type { Metadata } from "next";
import { StudioForm } from "@/features/generation/studio-form";
import { LinkButton } from "@/shared/ui/button";
import { PageHeader } from "@/shared/ui/layout";
import { parseId, type SearchParams } from "@/shared/url";

export const metadata: Metadata = { title: "创作工作台" };

export default async function StudioPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const applicationId = parseId((await searchParams).app);
  return (
    <>
      <PageHeader
        title="创作工作台"
        description="选择已到账的规格，描述画面，提交后可以关闭页面。"
        actions={<LinkButton href="/tasks" variant="secondary">我的任务</LinkButton>}
      />
      <StudioForm initialApplicationId={applicationId} />
    </>
  );
}
