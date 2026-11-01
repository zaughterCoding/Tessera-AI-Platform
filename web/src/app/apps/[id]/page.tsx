import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { AppCover } from "@/features/catalog/app-cover";
import { ProductCard } from "@/features/catalog/product-card";
import { fetchApplication, fetchProducts } from "@/features/catalog/server";
import { formatDate, formatScore } from "@/shared/format";
import { LinkButton } from "@/shared/ui/button";
import { Badge, EmptyState } from "@/shared/ui/feedback";
import { Card, DescriptionList } from "@/shared/ui/layout";
import { parseId } from "@/shared/url";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const id = parseId((await params).id);
  if (!id) notFound();
  const application = await fetchApplication(id);
  const description = application.summary ?? application.description ?? undefined;
  return {
    title: application.name,
    description,
    openGraph: {
      title: application.name,
      description,
      type: "website",
      ...(application.coverUrl ? { images: [application.coverUrl] } : {}),
    },
  };
}

export default async function ApplicationPage({ params }: Props) {
  const id = parseId((await params).id);
  if (!id) notFound();
  const [application, products] = await Promise.all([fetchApplication(id), fetchProducts(id)]);
  const capabilities = application.capabilities?.split(",").map((item) => item.trim()).filter(Boolean) ?? [];

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <div className="flex min-w-0 flex-col gap-6">
        <AppCover
          name={application.name}
          coverUrl={application.coverUrl}
          priority
          sizes="(min-width: 1024px) 60vw, 100vw"
          className="rounded-lg border border-line"
        />
        <div>
          <p className="text-sm text-ink-muted">{application.categoryName}</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">{application.name}</h1>
          {application.summary && <p className="mt-2 text-ink-soft">{application.summary}</p>}
          {capabilities.length > 0 && (
            <ul className="mt-3 flex flex-wrap gap-2">
              {capabilities.map((capability) => (
                <li key={capability}><Badge>{capability}</Badge></li>
              ))}
            </ul>
          )}
        </div>
        {application.description && (
          <section>
            <h2 className="mb-2 font-medium">应用介绍</h2>
            <p className="text-sm whitespace-pre-line text-ink-soft">{application.description}</p>
          </section>
        )}
        <Card>
          <DescriptionList
            items={[
              { label: "评分", value: `${formatScore(application.score)}（${application.reviewCount ?? 0} 条评价）` },
              { label: "已售", value: application.soldCount ?? 0 },
              { label: "模型", value: `${application.modelName ?? "—"} ${application.modelVersion ?? ""}` },
              { label: "上架时间", value: formatDate(application.publishedAt) },
            ]}
          />
        </Card>
      </div>

      <section aria-labelledby="products-heading" className="flex min-w-0 flex-col gap-3">
        <div className="flex items-center justify-between gap-3">
          <h2 id="products-heading" className="font-medium">选择使用包</h2>
          <LinkButton href={`/studio?app=${application.id}`} variant="secondary" size="sm">已有权益，开始创作</LinkButton>
        </div>
        {products.length ? (
          products.map((product) => <ProductCard key={product.id} product={product} />)
        ) : (
          <EmptyState title="暂无可购买的使用包" />
        )}
      </section>
    </div>
  );
}
