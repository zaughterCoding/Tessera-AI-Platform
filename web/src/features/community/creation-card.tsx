import { Heart } from "lucide-react";
import Image from "next/image";
import { mediaSrc } from "@/features/generation/schemas";
import { Badge, EmptyState } from "@/shared/ui/feedback";
import Link from "@/shared/ui/link";
import type { Creation } from "./schemas";

const GRID_SIZES = "(min-width: 1024px) 25vw, (min-width: 640px) 33vw, 50vw";

// 比例固定，图片加载前后不跳动。图片不走优化缓存，取消发布后立即失效。
export function CreationCard({ creation }: { creation: Creation }) {
  const cover = mediaSrc(creation.media[0]?.url);
  return (
    <article className="flex h-full flex-col overflow-hidden rounded-lg border border-line bg-surface shadow-card">
      <Link href={`/creations/${creation.id}`} className="group block">
        <div className="relative aspect-square bg-sunken">
          {cover ? (
            <Image src={cover} alt={`作品《${creation.title}》`} fill sizes={GRID_SIZES} className="object-cover" />
          ) : (
            <p className="flex size-full items-center justify-center p-4 text-center text-sm text-ink-muted">{creation.title}</p>
          )}
        </div>
        <h3 className="truncate px-3 pt-3 text-sm font-medium group-hover:text-accent">{creation.title}</h3>
      </Link>
      <div className="mt-auto flex items-center justify-between gap-2 px-3 pt-1 pb-3 text-xs text-ink-muted">
        <Link href={`/users/${creation.userId}`} className="min-w-0 truncate hover:text-ink">
          {creation.authorNickName ?? "匿名创作者"}
        </Link>
        <span className="inline-flex shrink-0 items-center gap-1">
          <Heart aria-hidden className="size-3" />
          <span className="sr-only">点赞数</span>
          {creation.likeCount}
        </span>
      </div>
      {creation.state !== "PUBLISHED" && <div className="px-3 pb-3"><Badge>未公开</Badge></div>}
    </article>
  );
}

export function CreationGrid({ creations }: { creations: Creation[] }) {
  return (
    <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
      {creations.map((creation) => (
        <li key={creation.id}><CreationCard creation={creation} /></li>
      ))}
    </ul>
  );
}

export function NoCreations({ description }: { description?: string }) {
  return <EmptyState title="还没有作品" description={description ?? "生成结果后，可以主动分享到这里。"} />;
}
