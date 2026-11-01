import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { CampaignPanel } from "@/features/campaign/campaign-panel";
import { fetchOffer } from "@/features/campaign/server";
import { Badge } from "@/shared/ui/feedback";
import { Card, DescriptionList } from "@/shared/ui/layout";
import Link from "@/shared/ui/link";
import { parseId } from "@/shared/url";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const id = parseId((await params).id);
  if (!id) notFound();
  const offer = await fetchOffer(id);
  const title = `${offer.applicationName} · ${offer.productTitle}`;
  const description = offer.productSubtitle ?? "限量活动";
  return { title, description, openGraph: { title, description, type: "website" } };
}

export default async function CampaignPage({ params }: Props) {
  const id = parseId((await params).id);
  if (!id) notFound();
  const offer = await fetchOffer(id);
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <div className="flex min-w-0 flex-col gap-4">
        <div>
          <Link href={`/apps/${offer.applicationId}`} className="text-sm text-ink-muted hover:text-ink">
            {offer.applicationName}
          </Link>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">{offer.productTitle}</h1>
          {offer.productSubtitle && <p className="mt-2 text-ink-soft">{offer.productSubtitle}</p>}
          <div className="mt-3"><Badge tone="warning">限量活动</Badge></div>
        </div>
        <Card>
          <DescriptionList
            items={[
              { label: "次数", value: `${offer.credits} 次` },
              { label: "有效期", value: `${offer.validDays} 天` },
              { label: "规格", value: `${offer.modelVersion ?? "—"} / ${offer.imageSize ?? "—"}` },
              { label: "每次输出", value: `${offer.outputCount} 张` },
            ]}
          />
        </Card>
        {offer.productRules && <p className="text-sm text-ink-muted">{offer.productRules}</p>}
      </div>
      <CampaignPanel initialOffer={offer} />
    </div>
  );
}
