import { Star } from "lucide-react";
import Link from "@/shared/ui/link";
import { formatMoney, formatScore } from "@/shared/format";
import { AppCover } from "./app-cover";
import type { ApplicationSummary } from "./schemas";

const GRID_SIZES = "(min-width: 1024px) 33vw, (min-width: 640px) 50vw, 100vw";

export function AppCard({ application, priority }: { application: ApplicationSummary; priority?: boolean }) {
  return (
    <Link
      href={`/apps/${application.id}`}
      className="group flex w-full flex-col overflow-hidden rounded-lg border border-line bg-surface shadow-card transition-colors hover:border-line-strong"
    >
      <AppCover name={application.name} coverUrl={application.coverUrl} sizes={GRID_SIZES} priority={priority ?? false} />
      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="flex items-center gap-2 text-xs text-ink-muted">
          {application.categoryName && <span>{application.categoryName}</span>}
          <span className="ml-auto inline-flex items-center gap-1">
            <Star aria-hidden className="size-3 fill-current" />
            {formatScore(application.score)}
          </span>
        </div>
        <h3 className="font-medium text-ink group-hover:text-accent">{application.name}</h3>
        {application.summary && <p className="line-clamp-2 text-sm text-ink-soft">{application.summary}</p>}
        <p className="mt-auto pt-1 text-sm font-medium text-ink">{formatMoney(application.priceFrom)} 起</p>
      </div>
    </Link>
  );
}

export function AppGrid({ applications, priorityCount = 0 }: { applications: ApplicationSummary[]; priorityCount?: number }) {
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {applications.map((application, index) => (
        <li key={application.id} className="flex">
          <AppCard application={application} priority={index < priorityCount} />
        </li>
      ))}
    </ul>
  );
}
