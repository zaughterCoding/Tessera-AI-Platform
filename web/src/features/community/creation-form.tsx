"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery } from "@tanstack/react-query";
import { clsx } from "clsx";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { applicationSummarySchema } from "@/features/catalog/schemas";
import { mediaAssetSchema, mediaSrc } from "@/features/generation/schemas";
import { api, ApiError } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { Notice } from "@/shared/ui/feedback";
import { Field, Input, Select, Textarea } from "@/shared/ui/field";
import { Card } from "@/shared/ui/layout";
import Link from "@/shared/ui/link";
import { QueryState } from "@/shared/ui/query-state";
import { useToast } from "@/shared/ui/toast";
import { creationSchema, type Creation } from "./schemas";

const MAX_MEDIA = 9;

const formSchema = z.object({
  applicationId: z.string().min(1, "请选择应用"),
  kind: z.enum(["WORK", "REVIEW"]),
  title: z.string().trim().min(1, "请填写标题").max(128, "标题最多 128 字"),
  content: z.string().trim().min(1, "请填写正文").max(20000, "正文太长"),
  mediaIds: z.array(z.string()).max(MAX_MEDIA, `最多选择 ${MAX_MEDIA} 张`),
  published: z.boolean(),
});
type FormValues = z.infer<typeof formSchema>;

export function CreationEditor({ editId }: { editId?: string | undefined }) {
  const existing = useQuery({
    queryKey: ["creation", "mine", editId],
    enabled: Boolean(editId),
    queryFn: async () => (await api(`/creations/mine/${editId}`, creationSchema)).data,
  });
  if (!editId) return <CreationForm />;
  return (
    <QueryState query={existing} backHref="/mine">
      {(creation) => <CreationForm creation={creation} />}
    </QueryState>
  );
}

function CreationForm({ creation }: { creation?: Creation }) {
  const router = useRouter();
  const toast = useToast();
  const editing = Boolean(creation);
  const [pendingPublic, setPendingPublic] = useState<FormValues | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const applications = useQuery({
    queryKey: ["catalog", "applications", "all"],
    enabled: !editing,
    queryFn: async () => (await api("/catalog/applications", z.array(applicationSummarySchema), { params: { size: 20 } })).data,
  });
  const assets = useQuery({
    queryKey: ["media", "picker"],
    queryFn: async () =>
      (await api("/media/mine", z.array(mediaAssetSchema), { params: { limit: 50, offset: 0 } })).data.filter((asset) => asset.state === "ACTIVE"),
  });

  const {
    register,
    handleSubmit,
    setValue,
    control,
    formState: { errors, isDirty },
  } = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      applicationId: creation?.applicationId ?? "",
      kind: creation?.kind === "REVIEW" ? "REVIEW" : "WORK",
      title: creation?.title ?? "",
      content: creation?.content ?? "",
      mediaIds: creation?.media.map((media) => media.mediaId) ?? [],
      published: false,
    },
  });
  const mediaIds = useWatch({ control, name: "mediaIds" });

  // 有未保存内容时，关闭或刷新页面前提示。
  useEffect(() => {
    if (!isDirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [isDirty]);

  const save = useMutation({
    mutationFn: async (values: FormValues) => {
      const body = { title: values.title, content: values.content, mediaIds: values.mediaIds };
      if (creation) return (await api(`/creations/${creation.id}`, creationSchema, { method: "PUT", body })).data;
      return (await api("/creations", creationSchema, {
        method: "POST",
        body: { ...body, applicationId: values.applicationId, kind: values.kind, published: values.published },
      })).data;
    },
    onSuccess: (saved) => {
      setPendingPublic(null);
      toast(saved.state === "PUBLISHED" ? "作品已发布" : "已保存，仅自己可见", "success");
      router.push(`/creations/${saved.id}`);
    },
    onError: (error) => {
      setPendingPublic(null);
      setFormError(error instanceof ApiError ? error.message : "保存失败，请重试");
    },
  });

  function onSubmit(values: FormValues) {
    setFormError(null);
    if (values.published) setPendingPublic(values);
    else save.mutate(values);
  }

  function toggleMedia(id: string) {
    const next = mediaIds.includes(id) ? mediaIds.filter((item) => item !== id) : [...mediaIds, id];
    if (next.length <= MAX_MEDIA) setValue("mediaIds", next, { shouldDirty: true, shouldValidate: true });
  }

  return (
    <form onSubmit={(event) => void handleSubmit(onSubmit)(event)} noValidate className="flex max-w-3xl flex-col gap-4">
      {formError && <Notice tone="danger" title="未能保存">{formError}</Notice>}
      <Card className="flex flex-col gap-4">
        {!editing && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="关联应用" required error={errors.applicationId?.message}>
              <Select {...register("applicationId")}>
                <option value="">请选择</option>
                {applications.data?.map((application) => (
                  <option key={application.id} value={application.id}>{application.name}</option>
                ))}
              </Select>
            </Field>
            <Field label="类型">
              <Select {...register("kind")}>
                <option value="WORK">作品</option>
                <option value="REVIEW">应用评测</option>
              </Select>
            </Field>
          </div>
        )}
        <Field label="标题" required error={errors.title?.message}>
          <Input {...register("title")} maxLength={128} />
        </Field>
        <Field label="正文" required error={errors.content?.message}>
          <Textarea {...register("content")} rows={6} />
        </Field>
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium">图片（最多 {MAX_MEDIA} 张）</legend>
          {assets.data?.length ? (
            <ul className="grid grid-cols-4 gap-2 sm:grid-cols-6">
              {assets.data.map((asset) => {
                const selected = mediaIds.includes(asset.id);
                const src = mediaSrc(asset.url);
                return (
                  <li key={asset.id}>
                    <button
                      type="button"
                      aria-pressed={selected}
                      aria-label={`选择图片 ${asset.originalName ?? asset.id}`}
                      onClick={() => toggleMedia(asset.id)}
                      className={clsx("relative block aspect-square w-full overflow-hidden rounded-md border-2 bg-sunken", selected ? "border-accent" : "border-transparent")}
                    >
                      {src && <Image src={src} alt="" fill sizes="96px" className="object-cover" />}
                    </button>
                  </li>
                );
              })}
            </ul>
          ) : (
            <p className="text-sm text-ink-muted">
              没有素材，可以到<Link href="/media" className="text-accent underline">我的素材</Link>上传。
            </p>
          )}
          <p className="mt-1.5 text-xs text-ink-muted">发布时只有选中的图片会公开，其他上传仍然私有。</p>
        </fieldset>
        {!editing && (
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="accent-accent" {...register("published")} />
            立即公开到社区（默认仅自己可见）
          </label>
        )}
      </Card>
      <div>
        <Button type="submit" loading={save.isPending}>{editing ? "保存修改" : "保存作品"}</Button>
      </div>
      <ConfirmDialog
        open={Boolean(pendingPublic)}
        onOpenChange={(open) => { if (!open) setPendingPublic(null); }}
        title="确认公开？"
        description="公开后所有访客都能看到作品和所选图片。"
        confirmLabel="确认公开"
        loading={save.isPending}
        onConfirm={() => { if (pendingPublic) save.mutate(pendingPublic); }}
      />
    </form>
  );
}
