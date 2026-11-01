import NextLink from "next/link";
import type { ComponentProps } from "react";

// 页面都是实时渲染，预取只会放大后端请求并污染访问统计，统一关闭。
export default function Link(props: ComponentProps<typeof NextLink>) {
  return <NextLink prefetch={false} {...props} />;
}
