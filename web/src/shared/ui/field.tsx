import { clsx } from "clsx";
import { useId, type ComponentProps, type ReactElement, cloneElement } from "react";

const CONTROL =
  "w-full rounded-md border border-line-strong bg-surface px-3 text-sm text-ink placeholder:text-ink-muted " +
  "disabled:bg-sunken disabled:text-ink-muted aria-invalid:border-danger";

export function Input({ className, ...props }: ComponentProps<"input">) {
  return <input className={clsx(CONTROL, "h-10", className)} {...props} />;
}

export function Textarea({ className, ...props }: ComponentProps<"textarea">) {
  return <textarea className={clsx(CONTROL, "min-h-24 py-2", className)} {...props} />;
}

export function Select({ className, ...props }: ComponentProps<"select">) {
  return <select className={clsx(CONTROL, "h-10", className)} {...props} />;
}

type FieldProps = {
  label: string;
  hint?: string;
  error?: string | undefined;
  required?: boolean;
  children: ReactElement<{
    id?: string;
    "aria-invalid"?: boolean;
    "aria-describedby"?: string;
    required?: boolean;
  }>;
};

// 统一把标签、提示和错误关联到控件，避免各表单遗漏无障碍属性。
export function Field({ label, hint, error, required, children }: FieldProps) {
  const id = useId();
  const messageId = `${id}-message`;
  const message = error ?? hint;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
        {required && <span className="ml-0.5 text-danger" aria-hidden>*</span>}
      </label>
      {cloneElement(children, {
        id,
        required,
        "aria-invalid": error ? true : undefined,
        "aria-describedby": message ? messageId : undefined,
      })}
      {message && (
        <p id={messageId} className={clsx("text-xs", error ? "text-danger" : "text-ink-muted")}>
          {message}
        </p>
      )}
    </div>
  );
}
