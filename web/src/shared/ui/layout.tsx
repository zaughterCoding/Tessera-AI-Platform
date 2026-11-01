import { clsx } from "clsx";
import type { ComponentProps, ReactNode } from "react";

export function Card({ className, ...props }: ComponentProps<"div">) {
  return <div className={clsx("rounded-lg border border-line bg-surface p-4 shadow-card sm:p-5", className)} {...props} />;
}

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
        {description && <p className="mt-1 text-sm text-ink-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

// 表格在窄屏横向滚动，页面本身不产生横向滚动。
export function Table({ className, ...props }: ComponentProps<"table">) {
  return (
    <div className="overflow-x-auto rounded-lg border border-line bg-surface">
      <table
        className={clsx(
          "w-full min-w-max border-collapse text-left text-sm",
          "[&_th]:border-b [&_th]:border-line [&_th]:bg-sunken [&_th]:px-3 [&_th]:py-2 [&_th]:font-medium [&_th]:text-ink-soft",
          "[&_td]:border-b [&_td]:border-line [&_td]:px-3 [&_td]:py-2 [&_tr:last-child_td]:border-b-0",
          className,
        )}
        {...props}
      />
    </div>
  );
}

export function DescriptionList({ items }: { items: Array<{ label: string; value: ReactNode }> }) {
  return (
    <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
      {items.map((item) => (
        <div key={item.label} className="min-w-0">
          <dt className="text-ink-muted">{item.label}</dt>
          <dd className="break-words text-ink">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
