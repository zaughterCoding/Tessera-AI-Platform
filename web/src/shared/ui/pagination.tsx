import { ChevronLeft, ChevronRight } from "lucide-react";
import { LinkButton } from "./button";

// 分页状态放在 URL 中，刷新和分享链接都能恢复到同一页。
export function Pagination({
  page,
  totalPages,
  hrefFor,
}: {
  page: number;
  totalPages: number;
  hrefFor: (page: number) => string;
}) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="分页" className="mt-6 flex items-center justify-center gap-3 text-sm">
      {page > 1 ? (
        <LinkButton variant="secondary" size="sm" href={hrefFor(page - 1)} rel="prev">
          <ChevronLeft aria-hidden className="size-4" />
          上一页
        </LinkButton>
      ) : null}
      <span className="text-ink-muted" aria-current="page">
        第 {page} / {totalPages} 页
      </span>
      {page < totalPages ? (
        <LinkButton variant="secondary" size="sm" href={hrefFor(page + 1)} rel="next">
          下一页
          <ChevronRight aria-hidden className="size-4" />
        </LinkButton>
      ) : null}
    </nav>
  );
}
