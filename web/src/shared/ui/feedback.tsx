import { clsx } from "clsx";
import { AlertCircle, CheckCircle2, Info, TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "./button";

export type Tone = "neutral" | "info" | "success" | "warning" | "danger";

const BADGE_TONES: Record<Tone, string> = {
  neutral: "bg-sunken text-ink-soft",
  info: "bg-info-soft text-info",
  success: "bg-success-soft text-success",
  warning: "bg-warning-soft text-warning",
  danger: "bg-danger-soft text-danger",
};

export function Badge({ tone = "neutral", pulse = false, children }: { tone?: Tone; pulse?: boolean; children: ReactNode }) {
  return (
    <span className={clsx("inline-flex items-center gap-1.5 rounded px-2 py-0.5 text-xs font-medium whitespace-nowrap", BADGE_TONES[tone])}>
      {pulse && <span aria-hidden className="size-1.5 animate-pulse rounded-full bg-current" />}
      {children}
    </span>
  );
}

const NOTICE_ICONS = { info: Info, success: CheckCircle2, warning: TriangleAlert, danger: AlertCircle } as const;

export function Notice({
  tone = "info",
  title,
  children,
  action,
}: {
  tone?: keyof typeof NOTICE_ICONS;
  title?: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  const Icon = NOTICE_ICONS[tone];
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className={clsx("flex items-start gap-3 rounded-md p-3 text-sm", BADGE_TONES[tone])}
    >
      <Icon aria-hidden className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 flex-1 text-ink-soft">
        {title && <p className="font-medium text-ink">{title}</p>}
        {children}
      </div>
      {action}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={clsx("animate-pulse rounded-md bg-sunken", className)} />;
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-lg border border-dashed border-line-strong px-6 py-12 text-center">
      <p className="font-medium text-ink">{title}</p>
      {description && <p className="max-w-md text-sm text-ink-muted">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function ErrorState({
  title = "加载失败",
  message,
  requestId,
  onRetry,
}: {
  title?: string;
  message?: string;
  requestId?: string | null | undefined;
  onRetry?: () => void;
}) {
  return (
    <div role="alert" className="flex flex-col items-center gap-2 rounded-lg border border-line bg-surface px-6 py-12 text-center">
      <AlertCircle aria-hidden className="size-6 text-danger" />
      <p className="font-medium text-ink">{title}</p>
      {message && <p className="max-w-md text-sm text-ink-soft">{message}</p>}
      {requestId && <p className="font-mono text-xs break-all text-ink-muted">请求号 {requestId}</p>}
      {onRetry && (
        <Button variant="secondary" size="sm" className="mt-2" onClick={onRetry}>
          重试
        </Button>
      )}
    </div>
  );
}
