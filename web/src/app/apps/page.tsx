import { clsx } from "clsx";
import { Search } from "lucide-react";
import type { Metadata } from "next";
import Form from "next/form";
import Link from "@/shared/ui/link";
import { AppGrid } from "@/features/catalog/app-card";
import { APPLICATION_SORTS, type ApplicationSort } from "@/features/catalog/schemas";
import { APPLICATION_PAGE_SIZE, fetchApplications, fetchCategories } from "@/features/catalog/server";
import { Button, LinkButton } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/feedback";
import { PageHeader } from "@/shared/ui/layout";
import { Pagination } from "@/shared/ui/pagination";
import { firstParam, hrefWith, parseId, parsePage, totalPagesOf, type SearchParams } from "@/shared/url";

export const metadata: Metadata = {
  title: "AI 应用",
  description: "按分类、关键词和价格筛选 AI 创作应用。",
};

function parseSort(value: string | undefined): ApplicationSort {
  return APPLICATION_SORTS.find((item) => item.value === value)?.value ?? "score";
}

const CHIP = "rounded-full border px-3 py-1 text-sm whitespace-nowrap transition-colors";
const CHIP_ON = "border-ink bg-ink text-white";
const CHIP_OFF = "border-line-strong text-ink-soft hover:text-ink";

// 筛选全部走 URL 和服务端渲染：刷新、分享、前进后退都能恢复，旧响应也不会覆盖新结果。
export default async function AppsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const raw = await searchParams;
  const keyword = firstParam(raw.keyword)?.trim().slice(0, 50) || undefined;
  const categoryId = parseId(raw.categoryId);
  const sort = parseSort(firstParam(raw.sort));
  const page = parsePage(raw.page);
  const current = { keyword, categoryId, sort: sort === "score" ? undefined : sort };

  const [categories, { data: applications, total }] = await Promise.all([
    fetchCategories(),
    fetchApplications({ keyword, categoryId, sort, page, size: APPLICATION_PAGE_SIZE }),
  ]);

  return (
    <>
      <PageHeader title="AI 应用" description={`共 ${total ?? 0} 个应用${keyword ? `，关键词“${keyword}”` : ""}`} />

      <Form prefetch={false} action="/apps" role="search" className="mb-4 flex max-w-md gap-2">
        <div className="relative min-w-0 flex-1">
          <Search aria-hidden className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-muted" />
          <input
            name="keyword"
            type="search"
            defaultValue={keyword}
            maxLength={50}
            aria-label="按关键词筛选应用"
            placeholder="名称或用途"
            className="h-10 w-full rounded-md border border-line-strong bg-surface pr-3 pl-9 text-sm placeholder:text-ink-muted"
          />
        </div>
        {categoryId && <input type="hidden" name="categoryId" value={categoryId} />}
        {sort !== "score" && <input type="hidden" name="sort" value={sort} />}
        <Button type="submit" variant="secondary">搜索</Button>
      </Form>

      <nav aria-label="应用分类" className="mb-3 flex gap-2 overflow-x-auto pb-1">
        <Link
          href={hrefWith("/apps", current, { categoryId: undefined })}
          aria-current={categoryId ? undefined : "true"}
          className={clsx(CHIP, categoryId ? CHIP_OFF : CHIP_ON)}
        >
          全部
        </Link>
        {categories.map((category) => {
          const active = category.id === categoryId;
          return (
            <Link
              key={category.id}
              href={hrefWith("/apps", current, { categoryId: category.id })}
              aria-current={active ? "true" : undefined}
              className={clsx(CHIP, active ? CHIP_ON : CHIP_OFF)}
            >
              {category.name}
            </Link>
          );
        })}
      </nav>

      <nav aria-label="排序方式" className="mb-6 flex flex-wrap gap-3 text-sm">
        {APPLICATION_SORTS.map((item) => {
          const active = item.value === sort;
          return (
            <Link
              key={item.value}
              href={hrefWith("/apps", current, { sort: item.value === "score" ? undefined : item.value })}
              aria-current={active ? "true" : undefined}
              className={active ? "font-medium text-accent" : "text-ink-muted hover:text-ink"}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>

      {applications.length ? (
        <AppGrid applications={applications} priorityCount={3} />
      ) : (
        <EmptyState
          title="没有符合条件的应用"
          description="换个关键词或分类试试。"
          action={<LinkButton href="/apps" variant="secondary">查看全部应用</LinkButton>}
        />
      )}

      <Pagination
        page={page}
        totalPages={totalPagesOf(total, APPLICATION_PAGE_SIZE)}
        hrefFor={(target) => hrefWith("/apps", current, { page: target === 1 ? undefined : target })}
      />
    </>
  );
}
