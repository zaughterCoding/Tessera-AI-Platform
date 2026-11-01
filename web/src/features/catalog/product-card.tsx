import { PurchaseButton } from "@/features/trade/purchase-button";
import { formatMoney } from "@/shared/format";
import { Badge } from "@/shared/ui/feedback";
import { Card } from "@/shared/ui/layout";
import type { Product } from "./schemas";

const LIMITED_SALE = 1;

export function ProductCard({ product }: { product: Product }) {
  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="font-medium">{product.title}</h3>
          {product.subtitle && <p className="text-sm text-ink-muted">{product.subtitle}</p>}
        </div>
        <p className="text-lg font-semibold whitespace-nowrap text-accent">{formatMoney(product.price)}</p>
      </div>
      <ul className="flex flex-wrap gap-2">
        <li><Badge>{product.credits} 次</Badge></li>
        <li><Badge>有效 {product.validDays} 天</Badge></li>
        {product.imageSize && <li><Badge>{product.imageSize}</Badge></li>}
        <li><Badge>每次 {product.outputCount} 张</Badge></li>
        {product.saleMode === LIMITED_SALE && <li><Badge tone="warning">限量活动专享</Badge></li>}
      </ul>
      {product.rules && <p className="text-xs text-ink-muted">{product.rules}</p>}
      <PurchaseButton product={product} />
    </Card>
  );
}
