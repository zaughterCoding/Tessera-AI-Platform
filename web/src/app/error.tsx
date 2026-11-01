"use client";

import { ErrorState } from "@/shared/ui/feedback";

export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <ErrorState
      title="页面加载失败"
      message="服务暂时不可用，请稍后重试。"
      requestId={error.digest}
      onRetry={reset}
    />
  );
}
