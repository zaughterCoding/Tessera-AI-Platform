"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api, ApiError, noData } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { Notice } from "@/shared/ui/feedback";
import { Field, Input } from "@/shared/ui/field";
import { hardNavigate } from "./session-provider";

const CODE_COOLDOWN_SECONDS = 60;

const loginSchema = z.object({
  phone: z.string().regex(/^1[3-9][0-9]{9}$/, "请输入 11 位手机号"),
  code: z.string().regex(/^[0-9]{6}$/, "请输入 6 位验证码"),
});
type LoginValues = z.infer<typeof loginSchema>;

const codeResponseSchema = z
  .object({ delivery: z.string().optional(), demoCode: z.string().optional() })
  .nullish();

function loginMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return "登录未完成，请重试";
  if (error.code === "INVALID_CODE") return "验证码错误或已过期";
  return error.message;
}

export function LoginForm({ returnTo }: { returnTo: string }) {
  const {
    register,
    handleSubmit,
    getValues,
    setValue,
    trigger,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { phone: "", code: "" } });
  const [formError, setFormError] = useState<string | null>(null);
  const [demoCode, setDemoCode] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setTimeout(() => setCooldown((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [cooldown]);

  async function sendCode() {
    if (!(await trigger("phone"))) return;
    setSending(true);
    setFormError(null);
    try {
      const { data } = await api("/user/code", codeResponseSchema, {
        method: "POST",
        params: { phone: getValues("phone") },
      });
      setDemoCode(data?.demoCode ?? null);
      setCooldown(CODE_COOLDOWN_SECONDS);
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : "验证码发送失败");
    } finally {
      setSending(false);
    }
  }

  async function login(values: LoginValues) {
    setFormError(null);
    try {
      await api("/auth/login", noData, { method: "POST", body: values });
      hardNavigate(returnTo);
    } catch (error) {
      setFormError(loginMessage(error));
    }
  }

  return (
    <form onSubmit={(event) => void handleSubmit(login)(event)} noValidate className="flex flex-col gap-4">
      {formError && <Notice tone="danger">{formError}</Notice>}
      <Field label="手机号" required error={errors.phone?.message}>
        <Input {...register("phone")} inputMode="numeric" maxLength={11} autoComplete="tel" placeholder="11 位手机号" />
      </Field>
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <Field label="验证码" required error={errors.code?.message}>
            <Input {...register("code")} inputMode="numeric" maxLength={6} autoComplete="one-time-code" placeholder="6 位验证码" />
          </Field>
        </div>
        <Button variant="secondary" className="mt-[1.65rem]" loading={sending} disabled={cooldown > 0} onClick={() => void sendCode()}>
          {cooldown > 0 ? `${cooldown} 秒后重发` : "获取验证码"}
        </Button>
      </div>
      {demoCode && (
        <Notice
          title={`演示验证码：${demoCode}`}
          action={
            <Button size="sm" variant="secondary" onClick={() => setValue("code", demoCode, { shouldValidate: true })}>
              填入
            </Button>
          }
        >
          当前是演示环境，验证码直接显示在页面上，不发送短信。
        </Notice>
      )}
      <Button type="submit" loading={isSubmitting}>登录</Button>
      <p className="text-xs text-ink-muted">首次登录会自动创建账号。</p>
    </form>
  );
}
