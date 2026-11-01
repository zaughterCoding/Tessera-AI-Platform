import type { Metadata } from "next";
import { LinkButton } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/feedback";

export const metadata: Metadata = { title: "页面不存在" };

export default function NotFound() {
  return (
    <EmptyState
      title="页面不存在"
      description="内容可能已下架、已取消发布，或者你没有查看权限。"
      action={<LinkButton href="/" variant="secondary">返回首页</LinkButton>}
    />
  );
}
