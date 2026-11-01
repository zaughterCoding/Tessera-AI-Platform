"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import Image from "next/image";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { api, ApiError } from "@/shared/api/client";
import { longString } from "@/shared/api/types";
import { LinkButton, Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { Notice } from "@/shared/ui/feedback";
import { Field, Input, Textarea } from "@/shared/ui/field";
import { Card } from "@/shared/ui/layout";
import { useToast } from "@/shared/ui/toast";
import { resultContentUrl, type Task } from "./schemas";

const shareSchema = z.object({
  resultIds: z.array(z.string()).min(1, "至少选择一张结果").max(9, "最多选择 9 张"),
  title: z.string().trim().min(1, "请填写标题").max(128, "标题最多 128 字"),
  content: z.string().trim().min(1, "请填写介绍").max(20000, "介绍太长"),
  published: z.boolean(),
});
type ShareValues = z.infer<typeof shareSchema>;

const shareResponse = z.object({ id: longString, state: z.string() });

export function ShareForm({ task }: { task: Task }) {
  const toast = useToast();
  const results = task.results.filter((result) => result.downloadAvailable);
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<ShareValues>({
    resolver: zodResolver(shareSchema),
    defaultValues: { resultIds: results.map((result) => result.id), title: "", content: task.prompt ?? "", published: false },
  });
  const [pendingPublic, setPendingPublic] = useState<ShareValues | null>(null);

  const share = useMutation({
    mutationFn: async (values: ShareValues) =>
      (await api(`/generation/tasks/${task.id}/share`, shareResponse, { method: "POST", body: values })).data,
    onSuccess: (creation) => {
      setPendingPublic(null);
      toast(creation.state === "PUBLISHED" ? "作品已公开" : "作品已保存，仅自己可见", "success");
    },
    onError: (error) => {
      setPendingPublic(null);
      toast(error instanceof ApiError ? error.message : "分享失败", "error");
    },
  });

  function onSubmit(values: ShareValues) {
    // 公开必须再次确认，结果默认只有自己能看。
    if (values.published) setPendingPublic(values);
    else share.mutate(values);
  }

  if (share.data) {
    return (
      <Notice
        tone="success"
        title={share.data.state === "PUBLISHED" ? "作品已公开" : "作品已保存，仅自己可见"}
        action={<LinkButton size="sm" variant="secondary" href={`/creations/${share.data.id}`}>查看作品</LinkButton>}
      />
    );
  }

  return (
    <Card>
      <h2 className="mb-3 font-medium">分享为作品</h2>
      <form onSubmit={(event) => void handleSubmit(onSubmit)(event)} noValidate className="flex flex-col gap-4">
        <Controller
          control={control}
          name="resultIds"
          render={({ field }) => (
            <fieldset>
              <legend className="mb-1.5 text-sm font-medium">选择结果</legend>
              <div className="flex flex-wrap gap-2">
                {results.map((result) => {
                  const checked = field.value.includes(result.id);
                  return (
                    <label key={result.id} className="relative block size-20 cursor-pointer overflow-hidden rounded-md border border-line-strong has-checked:border-accent has-checked:ring-2 has-checked:ring-accent has-focus-visible:outline-2">
                      <Image src={resultContentUrl(task.id, result.id)} alt={`第 ${result.position + 1} 张`} fill sizes="80px" className="object-cover" />
                      <input
                        type="checkbox"
                        checked={checked}
                        className="absolute top-1 left-1 accent-accent"
                        onChange={() => field.onChange(checked ? field.value.filter((id) => id !== result.id) : [...field.value, result.id])}
                      />
                    </label>
                  );
                })}
              </div>
              {errors.resultIds?.message && <p className="mt-1 text-xs text-danger">{errors.resultIds.message}</p>}
            </fieldset>
          )}
        />
        <Field label="标题" required error={errors.title?.message}>
          <Input {...register("title")} maxLength={128} />
        </Field>
        <Field label="介绍" required error={errors.content?.message}>
          <Textarea {...register("content")} rows={3} />
        </Field>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="accent-accent" {...register("published")} />
          立即公开到社区（默认仅自己可见）
        </label>
        <div><Button type="submit" variant="secondary" loading={share.isPending}>保存作品</Button></div>
      </form>
      <ConfirmDialog
        open={Boolean(pendingPublic)}
        onOpenChange={(open) => { if (!open) setPendingPublic(null); }}
        title="确认公开？"
        description="公开后所有访客都能看到所选图片。之后可以取消发布，图片地址会立即失效。"
        confirmLabel="确认公开"
        loading={share.isPending}
        onConfirm={() => { if (pendingPublic) share.mutate(pendingPublic); }}
      />
    </Card>
  );
}
