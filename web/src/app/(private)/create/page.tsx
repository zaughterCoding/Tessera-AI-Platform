import type { Metadata } from "next";
import { CreationEditor } from "@/features/community/creation-form";
import { PageHeader } from "@/shared/ui/layout";
import { parseId, type SearchParams } from "@/shared/url";

export const metadata: Metadata = { title: "发布作品" };

export default async function CreatePage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const editId = parseId((await searchParams).edit);
  return (
    <>
      <PageHeader title={editId ? "编辑作品" : "发布作品"} />
      <CreationEditor editId={editId} />
    </>
  );
}
