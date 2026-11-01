import type { Metadata } from "next";
import { OfferCard } from "@/features/campaign/offer-card";
import { fetchOffers } from "@/features/campaign/server";
import { LinkButton } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/feedback";
import { PageHeader } from "@/shared/ui/layout";

export const metadata: Metadata = {
  title: "限量活动",
  description: "限量使用包活动，受理后异步建单，名额以实时库存为准。",
};

export default async function CampaignsPage() {
  const offers = await fetchOffers();
  return (
    <>
      <PageHeader title="限量活动" description="抢购受理后异步建单，支付和到账分别显示。" />
      {offers.length ? (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {offers.map((offer) => (
            <li key={offer.id}><OfferCard offer={offer} /></li>
          ))}
        </ul>
      ) : (
        <EmptyState
          title="当前没有进行中的活动"
          description="可以先看看普通使用包。"
          action={<LinkButton href="/apps" variant="secondary">浏览应用</LinkButton>}
        />
      )}
    </>
  );
}
