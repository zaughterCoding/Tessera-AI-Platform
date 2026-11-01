import type { Metadata } from "next";
import { ProfilePanel } from "@/features/auth/profile-panel";
import { PageHeader } from "@/shared/ui/layout";
import { parsePage, type SearchParams } from "@/shared/url";

export const metadata: Metadata = { title: "个人中心" };

export default async function MinePage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const page = parsePage((await searchParams).page);
  return (
    <>
      <PageHeader title="个人中心" />
      <ProfilePanel page={page} />
    </>
  );
}
