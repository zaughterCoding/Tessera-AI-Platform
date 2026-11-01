"use client";

import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { api } from "@/shared/api/client";
import { formatMoney, formatTime } from "@/shared/format";
import { LinkButton } from "@/shared/ui/button";
import { Badge, EmptyState } from "@/shared/ui/feedback";
import { Card } from "@/shared/ui/layout";
import Link from "@/shared/ui/link";
import { Pagination } from "@/shared/ui/pagination";
import { QueryState } from "@/shared/ui/query-state";
import { totalPagesOf } from "@/shared/url";
import { orderSchema, orderStatusView } from "./schemas";

const PAGE_SIZE = 10;

export function OrderList({ page }: { page: number }) {
  const query = useQuery({
    queryKey: ["orders", page],
    queryFn: () => api("/trade/orders", z.array(orderSchema), { params: { page, size: PAGE_SIZE } }),
  });

  return (
    <QueryState query={query}>
      {({ data: orders, total }) =>
        orders.length ? (
          <>
            <ul className="flex flex-col gap-3">
              {orders.map((order) => {
                const status = orderStatusView(order.status);
                return (
                  <li key={order.id}>
                    <Card className="flex flex-wrap items-center gap-x-4 gap-y-2">
                      <div className="min-w-0 flex-1 basis-64">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge tone={status.tone}>{status.label}</Badge>
                          {order.campaignId && <Badge tone="warning">限量活动</Badge>}
                        </div>
                        <Link href={`/orders/${order.id}`} className="mt-1 block font-medium hover:text-accent">
                          {order.applicationName} · {order.productTitle}
                        </Link>
                        <p className="text-xs text-ink-muted">
                          {formatTime(order.createdAt)} · {order.entitlementNotice}
                        </p>
                      </div>
                      <p className="font-semibold">{formatMoney(order.amount)}</p>
                    </Card>
                  </li>
                );
              })}
            </ul>
            <Pagination
              page={page}
              totalPages={totalPagesOf(total, PAGE_SIZE)}
              hrefFor={(target) => (target === 1 ? "/orders" : `/orders?page=${target}`)}
            />
          </>
        ) : (
          <EmptyState
            title="还没有订单"
            description="购买使用包后，订单会显示在这里。"
            action={<LinkButton href="/apps" variant="secondary">浏览应用</LinkButton>}
          />
        )
      }
    </QueryState>
  );
}
