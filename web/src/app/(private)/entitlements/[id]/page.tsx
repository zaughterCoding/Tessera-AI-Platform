import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { GrantDetail } from "@/features/entitlement/entitlement-views";
import { parseId, parsePage, type SearchParams } from "@/shared/url";

export const metadata: Metadata = { title: "权益详情" };

export default async function EntitlementPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<SearchParams>;
}) {
  const id = parseId((await params).id);
  if (!id) notFound();
  return <GrantDetail id={id} page={parsePage((await searchParams).page)} />;
}
