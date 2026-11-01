import { clsx } from "clsx";
import type { Metadata } from "next";
import { CreationGrid, NoCreations } from "@/features/community/creation-card";
import { CREATION_PAGE_SIZE, fetchCreations } from "@/features/community/server";
import { PageHeader } from "@/shared/ui/layout";
import Link from "@/shared/ui/link";
import { Pagination } from "@/shared/ui/pagination";
import { firstParam, hrefWith, parsePage, totalPagesOf, type SearchParams } from "@/shared/url";

export const metadata: Metadata = {
  title: "作品社区",
  description: "创作者主动分享的 AI 作品。",
  openGraph: { title: "作品社区", description: "创作者主动分享的 AI 作品。", type: "website" },
};

export default async function CommunityPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const raw = await searchParams;
  const sort = firstParam(raw.sort) === "hot" ? "hot" : "latest";
  const page = parsePage(raw.page);
  const { data: creations, total } = await fetchCreations(sort, page);
  const current = { sort: sort === "hot" ? "hot" : undefined };

  return (
    <>
      <PageHeader title="作品社区" description="结果默认只有自己能看，这里都是创作者主动公开的作品。" />
      <nav aria-label="排序方式" className="mb-6 flex gap-4 border-b border-line text-sm">
        {[
          { value: "latest", label: "最新" },
          { value: "hot", label: "最热" },
        ].map((item) => {
          const active = item.value === sort;
          return (
            <Link
              key={item.value}
              href={hrefWith("/community", {}, { sort: item.value === "hot" ? "hot" : undefined })}
              aria-current={active ? "true" : undefined}
              className={clsx("-mb-px border-b-2 px-1 pb-2", active ? "border-accent font-medium text-ink" : "border-transparent text-ink-muted hover:text-ink")}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
      {creations.length ? <CreationGrid creations={creations} /> : <NoCreations />}
      <Pagination
        page={page}
        totalPages={totalPagesOf(total, CREATION_PAGE_SIZE)}
        hrefFor={(target) => hrefWith("/community", current, { page: target === 1 ? undefined : target })}
      />
    </>
  );
}
