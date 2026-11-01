import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { OrderDetail } from "@/features/trade/order-detail";
import { parseId } from "@/shared/url";

export const metadata: Metadata = { title: "订单详情" };

export default async function OrderPage({ params }: { params: Promise<{ id: string }> }) {
  const id = parseId((await params).id);
  if (!id) notFound();
  return <OrderDetail id={id} />;
}
