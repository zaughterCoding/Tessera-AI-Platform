import { Search } from "lucide-react";
import Form from "next/form";

// 使用原生 GET 表单：无 JS 时也能搜索，条件进入 URL。
export function SearchForm({ className }: { className?: string }) {
  return (
    <Form prefetch={false} action="/apps" role="search" className={className}>
      <div className="relative">
        <Search aria-hidden className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-ink-muted" />
        <input
          name="keyword"
          type="search"
          maxLength={50}
          aria-label="搜索 AI 应用"
          placeholder="搜索 AI 应用"
          className="h-9 w-full rounded-md border border-line-strong bg-surface pr-3 pl-8 text-sm placeholder:text-ink-muted"
        />
      </div>
    </Form>
  );
}
