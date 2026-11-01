import type { Metadata } from "next";
import { FeedList } from "@/features/community/feed-list";
import { PageHeader } from "@/shared/ui/layout";

export const metadata: Metadata = { title: "关注动态" };

export default function FeedPage() {
  return (
    <>
      <PageHeader title="关注动态" description="你关注的创作者最新发布的作品。" />
      <FeedList />
    </>
  );
}
