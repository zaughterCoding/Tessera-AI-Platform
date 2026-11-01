import type { Metadata } from "next";
import { BalanceSummary, GrantList } from "@/features/entitlement/entitlement-views";
import { PageHeader } from "@/shared/ui/layout";
import { parsePage, type SearchParams } from "@/shared/url";

export const metadata: Metadata = { title: "我的权益" };

export default async function EntitlementsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const page = parsePage((await searchParams).page);
  return (
    <>
      <PageHeader title="我的权益" description="次数按权益包独立计算，提交任务时先冻结，交付后才消费。" />
      <BalanceSummary />
      <GrantList page={page} />
    </>
  );
}
