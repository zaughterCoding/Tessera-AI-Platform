import { clsx } from "clsx";
import { Check, Loader2, X } from "lucide-react";

export type StepState = "done" | "active" | "pending" | "failed";
export type Step = { label: string; state: StepState; detail?: string | undefined };

const MARK: Record<StepState, string> = {
  done: "bg-success text-white",
  active: "bg-info text-white",
  pending: "bg-sunken text-ink-muted",
  failed: "bg-danger text-white",
};

// 分开展示每个事实，受理、支付、到账不能混成一个“成功”。
export function Steps({ steps }: { steps: Step[] }) {
  return (
    <ol className="flex flex-col gap-3">
      {steps.map((step) => (
        <li key={step.label} className="flex items-start gap-3" aria-current={step.state === "active" ? "step" : undefined}>
          <span className={clsx("mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full text-xs", MARK[step.state])}>
            {step.state === "done" && <Check aria-hidden className="size-3" />}
            {step.state === "failed" && <X aria-hidden className="size-3" />}
            {step.state === "active" && <Loader2 aria-hidden className="size-3 animate-spin" />}
          </span>
          <div className="min-w-0">
            <p className={clsx("text-sm", step.state === "pending" ? "text-ink-muted" : "font-medium text-ink")}>{step.label}</p>
            {step.detail && <p className="text-xs text-ink-muted">{step.detail}</p>}
          </div>
        </li>
      ))}
    </ol>
  );
}
