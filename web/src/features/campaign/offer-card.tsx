import { formatMoney, formatTime } from "@/shared/format";
import { Badge } from "@/shared/ui/feedback";
import { Card } from "@/shared/ui/layout";
import Link from "@/shared/ui/link";
import { offerStateView, type Offer } from "./schemas";

export function OfferCard({ offer }: { offer: Offer }) {
  const state = offerStateView(offer.state);
  return (
    <Card className="flex h-full flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <Badge tone={state.tone}>{state.label}</Badge>
        <span className="text-xs text-ink-muted">{offer.applicationName}</span>
      </div>
      <h3 className="font-medium">
        <Link href={`/campaigns/${offer.id}`} className="hover:text-accent">{offer.productTitle}</Link>
      </h3>
      {offer.productSubtitle && <p className="text-sm text-ink-muted">{offer.productSubtitle}</p>}
      <p className="mt-auto text-lg font-semibold text-accent">{formatMoney(offer.price)}</p>
      <p className="text-xs text-ink-muted">
        剩余 {offer.remainingStock ?? "—"} · 截止 {formatTime(offer.endAt)}
      </p>
    </Card>
  );
}
