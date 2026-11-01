import type { Metadata } from "next";
import { MediaLibrary } from "@/features/media/media-library";
import { PageHeader } from "@/shared/ui/layout";
import { parsePage, type SearchParams } from "@/shared/url";

export const metadata: Metadata = { title: "我的素材" };

export default async function MediaPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const page = parsePage((await searchParams).page);
  return (
    <>
      <PageHeader title="我的素材" description="素材默认仅自己可见，只有放进已发布作品的图片才会公开。" />
      <MediaLibrary page={page} />
    </>
  );
}
