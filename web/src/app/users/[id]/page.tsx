import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { CreationGrid } from "@/features/community/creation-card";
import { FollowButton } from "@/features/community/follow-button";
import { CREATION_PAGE_SIZE, fetchUserCreations } from "@/features/community/server";
import { PageHeader } from "@/shared/ui/layout";
import { Pagination } from "@/shared/ui/pagination";
import { hrefWith, parseId, parsePage, totalPagesOf, type SearchParams } from "@/shared/url";

type Props = { params: Promise<{ id: string }>; searchParams: Promise<SearchParams> };

// 后端没有公开的用户资料接口，昵称来自公开作品。没有公开作品的账号一律 404，也避免枚举用户。
async function loadProfile(id: string, page: number) {
  const { data, total } = await fetchUserCreations(id, page);
  const author = data[0];
  if (!author && page === 1) notFound();
  return { creations: data, total, nickName: author?.authorNickName ?? "创作者" };
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const id = parseId((await params).id);
  if (!id) notFound();
  const { nickName } = await loadProfile(id, 1);
  return { title: nickName, description: `${nickName} 公开的 AI 作品。`, openGraph: { title: nickName, type: "profile" } };
}

export default async function UserPage({ params, searchParams }: Props) {
  const id = parseId((await params).id);
  if (!id) notFound();
  const page = parsePage((await searchParams).page);
  const { creations, total, nickName } = await loadProfile(id, page);
  return (
    <>
      <PageHeader title={nickName} description={`${total ?? 0} 个公开作品`} actions={<FollowButton userId={id} />} />
      <CreationGrid creations={creations} />
      <Pagination
        page={page}
        totalPages={totalPagesOf(total, CREATION_PAGE_SIZE)}
        hrefFor={(target) => hrefWith(`/users/${id}`, {}, { page: target === 1 ? undefined : target })}
      />
    </>
  );
}
