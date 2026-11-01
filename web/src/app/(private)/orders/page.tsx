import type { Metadata } from "next";
import { OrderList } from "@/features/trade/order-list";
import { PageHeader } from "@/shared/ui/layout";
import { parsePage, type SearchParams } from "@/shared/url";

export const metadata: Metadata = { title: "我的订单" };

export default async function OrdersPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const page = parsePage((await searchParams).page);
  return (
    <>
      <PageHeader title="我的订单" />
      <OrderList page={page} />
    </>
  );
}
