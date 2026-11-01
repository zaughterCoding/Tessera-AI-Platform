import type { Metadata } from "next";
import { headers } from "next/headers";
import Image from "next/image";
import { notFound } from "next/navigation";
import { FollowButton } from "@/features/community/follow-button";
import { LikeButton } from "@/features/community/like-button";
import { OwnerActions } from "@/features/community/owner-actions";
import { kindLabel } from "@/features/community/schemas";
import { fetchCreation } from "@/features/community/server";
import { mediaSrc } from "@/features/generation/schemas";
import { formatTime } from "@/shared/format";
import { Badge } from "@/shared/ui/feedback";
import Link from "@/shared/ui/link";
import { parseId } from "@/shared/url";

type Props = { params: Promise<{ id: string }> };

async function absoluteUrl(path: string): Promise<string> {
  const headerList = await headers();
  const host = headerList.get("x-forwarded-host") ?? headerList.get("host");
  const protocol = headerList.get("x-forwarded-proto") ?? "http";
  return `${protocol}://${host}${path}`;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const id = parseId((await params).id);
  if (!id) notFound();
  const creation = await fetchCreation(id);
  const published = creation.state === "PUBLISHED";
  const description = creation.content?.slice(0, 120) ?? undefined;
  const image = published ? mediaSrc(creation.media[0]?.url) : null;
  return {
    title: creation.title,
    description,
    // 作者查看未公开作品时，不让搜索引擎收录。
    robots: published ? undefined : { index: false, follow: false },
    openGraph: {
      title: creation.title,
      description,
      type: "article",
      ...(image ? { images: [await absoluteUrl(image)] } : {}),
    },
  };
}

export default async function CreationPage({ params }: Props) {
  const id = parseId((await params).id);
  if (!id) notFound();
  const creation = await fetchCreation(id);
  const published = creation.state === "PUBLISHED";

  return (
    <article className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <div className="flex min-w-0 flex-col gap-3">
        {creation.media.length ? (
          creation.media.map((media, index) => {
            const src = mediaSrc(media.url);
            return src ? (
              <div key={media.mediaId} className="relative aspect-square overflow-hidden rounded-lg border border-line bg-sunken">
                <Image
                  src={src}
                  alt={`作品《${creation.title}》第 ${index + 1} 张`}
                  fill
                  priority={index === 0}
                  sizes="(min-width: 1024px) 60vw, 100vw"
                  className="object-contain"
                />
              </div>
            ) : null;
          })
        ) : (
          <div className="rounded-lg border border-dashed border-line-strong p-10 text-center text-sm text-ink-muted">这篇内容没有图片</div>
        )}
      </div>

      <div className="flex min-w-0 flex-col gap-4">
        <div className="flex flex-wrap items-center gap-2">
          <Badge>{kindLabel(creation.kind)}</Badge>
          {!published && <Badge tone="warning">未公开，仅自己可见</Badge>}
        </div>
        <h1 className="text-2xl font-semibold tracking-tight">{creation.title}</h1>
        <div className="flex items-center justify-between gap-3">
          <Link href={`/users/${creation.userId}`} className="min-w-0 truncate text-sm font-medium hover:text-accent">
            {creation.authorNickName ?? "匿名创作者"}
          </Link>
          <FollowButton userId={creation.userId} />
        </div>
        {creation.content && <p className="text-sm break-words whitespace-pre-wrap text-ink-soft">{creation.content}</p>}
        <p className="text-xs text-ink-muted">
          {formatTime(creation.publishedAt ?? creation.createdAt)}
          {creation.applicationId && creation.applicationName && (
            <>
              {" · "}
              <Link href={`/apps/${creation.applicationId}`} className="hover:text-ink">{creation.applicationName}</Link>
            </>
          )}
        </p>
        {published && (
          <div><LikeButton creationId={creation.id} initial={{ liked: creation.liked ?? false, likeCount: creation.likeCount }} /></div>
        )}
        <OwnerActions creationId={creation.id} authorId={creation.userId} published={published} />
      </div>
    </article>
  );
}
