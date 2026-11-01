"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { clsx } from "clsx";
import Image from "next/image";
import { useMemo } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { grantSchema } from "@/features/entitlement/schemas";
import { api } from "@/shared/api/client";
import { Button, LinkButton } from "@/shared/ui/button";
import { Badge, EmptyState, ErrorState, Notice } from "@/shared/ui/feedback";
import { Field, Select, Textarea } from "@/shared/ui/field";
import { Card } from "@/shared/ui/layout";
import Link from "@/shared/ui/link";
import { ListSkeleton } from "@/shared/ui/query-state";
import { getRandomRequestKey } from "./request-key";
import { generationStatusSchema, mediaAssetSchema, mediaSrc } from "./schemas";
import { specsFromGrants, type Spec } from "./specs";
import { useTaskSubmission } from "./submission";

const MAX_REFERENCE_MEDIA = 4;
const GRANT_PAGE_SIZE = 50;

const formSchema = z.object({
  applicationId: z.string().min(1, "请选择应用"),
  specKey: z.string().min(1, "请选择规格"),
  prompt: z.string().trim().min(1, "请填写提示词").max(2000, "提示词最多 2000 字"),
  negativePrompt: z.string().trim().max(1000, "最多 1000 字"),
  mediaIds: z.array(z.string()).max(MAX_REFERENCE_MEDIA, `最多选择 ${MAX_REFERENCE_MEDIA} 张素材`),
});
type FormValues = z.infer<typeof formSchema>;

async function fetchAllGrants() {
  const grants = [];
  for (let page = 1; ; page++) {
    const { data, total } = await api("/entitlement/grants", z.array(grantSchema), { params: { page, size: GRANT_PAGE_SIZE } });
    grants.push(...data);
    if (!data.length || grants.length >= (total ?? 0)) return grants;
  }
}

export function StudioForm({ initialApplicationId }: { initialApplicationId?: string | undefined }) {
  const grants = useQuery({ queryKey: ["entitlement", "all-grants"], queryFn: fetchAllGrants });
  const status = useQuery({
    queryKey: ["generation", "status"],
    queryFn: async () => (await api("/generation/status", generationStatusSchema)).data,
  });

  const specs = useMemo(() => specsFromGrants(grants.data ?? []), [grants.data]);

  if (grants.isPending) return <ListSkeleton rows={2} />;
  if (grants.isError) {
    return <ErrorState message="权益加载失败" onRetry={() => void grants.refetch()} />;
  }
  if (!specs.length) {
    return (
      <EmptyState
        title="还没有可用的权益"
        description="模型、尺寸和张数都来自已到账的使用包。先购买一个使用包，到账后就能开始创作。"
        action={<LinkButton href={initialApplicationId ? `/apps/${initialApplicationId}` : "/apps"}>去选使用包</LinkButton>}
      />
    );
  }
  return (
    <StudioFields
      specs={specs}
      initialApplicationId={initialApplicationId}
      submitEnabled={status.data?.submitEnabled ?? false}
      statusNotice={status.data?.notice ?? null}
      simulated={status.data?.simulatedChannel ?? null}
      channelName={status.data?.channelDisplayName ?? null}
    />
  );
}

function StudioFields({
  specs,
  initialApplicationId,
  submitEnabled,
  statusNotice,
  simulated,
  channelName,
}: {
  specs: Spec[];
  initialApplicationId?: string | undefined;
  submitEnabled: boolean;
  statusNotice: string | null;
  simulated: boolean | null;
  channelName: string | null;
}) {
  const applications = useMemo(() => {
    const seen = new Map<string, string>();
    specs.forEach((spec) => seen.set(spec.applicationId, spec.applicationName));
    return [...seen.entries()].map(([id, name]) => ({ id, name }));
  }, [specs]);

  const defaultApplication = applications.find((item) => item.id === initialApplicationId)?.id ?? applications[0]?.id ?? "";
  const {
    register,
    handleSubmit,
    setValue,
    control,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      applicationId: defaultApplication,
      specKey: specs.find((spec) => spec.applicationId === defaultApplication)?.key ?? "",
      prompt: "",
      negativePrompt: "",
      mediaIds: [],
    },
  });
  const applicationId = useWatch({ control, name: "applicationId" });
  const specKey = useWatch({ control, name: "specKey" });
  const mediaIds = useWatch({ control, name: "mediaIds" });
  const applicationSpecs = specs.filter((spec) => spec.applicationId === applicationId);

  const assets = useQuery({
    queryKey: ["media", "picker"],
    queryFn: async () =>
      (await api("/media/mine", z.array(mediaAssetSchema), { params: { limit: 50, offset: 0 } })).data.filter((asset) => asset.state === "ACTIVE"),
  });

  const submission = useTaskSubmission();

  function onSubmit(values: FormValues) {
    const spec = specs.find((item) => item.key === values.specKey);
    if (!spec) return;
    submission.submit({
      requestKey: getRandomRequestKey(),
      applicationId: spec.applicationId,
      applicationVersion: spec.applicationVersion,
      modelVersion: spec.modelVersion,
      imageSize: spec.imageSize,
      outputCount: spec.outputCount,
      prompt: values.prompt,
      negativePrompt: values.negativePrompt,
      mediaIds: values.mediaIds,
    });
  }

  function toggleMedia(id: string) {
    const next = mediaIds.includes(id) ? mediaIds.filter((item) => item !== id) : [...mediaIds, id];
    if (next.length <= MAX_REFERENCE_MEDIA) setValue("mediaIds", next, { shouldValidate: true });
  }

  return (
    <div className="flex flex-col gap-4">
      {submission.unknownDraft && (
        <Notice
          tone="warning"
          title="上一次提交结果未知"
          action={
            <div className="flex flex-col gap-1.5">
              <Button size="sm" onClick={submission.recover}>找回原任务</Button>
              <Button size="sm" variant="ghost" onClick={submission.discardDraft}>放弃</Button>
            </div>
          }
        >
          <p>网络中断，任务可能已经受理。找回不会重复冻结次数。</p>
          <p className="mt-1 line-clamp-2 text-xs">提示词：{submission.unknownDraft.prompt}</p>
          <p className="mt-1 text-xs">放弃只是不再追踪这次请求；如果已受理，可在<Link href="/tasks" className="text-accent">我的任务</Link>中看到。</p>
        </Notice>
      )}
      {submission.rejection && <Notice tone="danger" title="未能提交">{submission.rejection}</Notice>}
      {!submitEnabled && statusNotice && <Notice tone="warning" title="当前不能提交">{statusNotice}</Notice>}

      <form onSubmit={(event) => void handleSubmit(onSubmit)(event)} noValidate className="flex flex-col gap-4">
        <Card className="flex flex-col gap-4">
          <Field label="应用" required error={errors.applicationId?.message}>
            <Select
              {...register("applicationId", {
                onChange: (event: { target: { value: string } }) => {
                  const first = specs.find((spec) => spec.applicationId === event.target.value);
                  setValue("specKey", first?.key ?? "");
                },
              })}
            >
              {applications.map((item) => (
                <option key={item.id} value={item.id}>{item.name}</option>
              ))}
            </Select>
          </Field>

          <fieldset>
            <legend className="mb-1.5 text-sm font-medium">规格（来自已到账权益，不可自定义）</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {applicationSpecs.map((spec) => (
                <label
                  key={spec.key}
                  className={clsx(
                    "flex cursor-pointer items-start gap-2 rounded-md border p-3 text-sm has-focus-visible:outline-2 has-focus-visible:outline-accent",
                    spec.key === specKey ? "border-accent bg-accent-soft" : "border-line-strong",
                  )}
                >
                  <input type="radio" value={spec.key} className="mt-1 accent-accent" {...register("specKey")} />
                  <span className="min-w-0">
                    <span className="block font-medium">{spec.imageSize} · 每次 {spec.outputCount} 张</span>
                    <span className="block text-xs text-ink-muted">
                      模型 {spec.modelVersion} · 可用 {spec.available} 次{spec.grantCount > 1 ? `（${spec.grantCount} 个权益包）` : ""}
                    </span>
                  </span>
                </label>
              ))}
            </div>
            {errors.specKey?.message && <p className="mt-1 text-xs text-danger">{errors.specKey.message}</p>}
          </fieldset>

          <Field label="提示词" required error={errors.prompt?.message}>
            <Textarea {...register("prompt")} rows={4} maxLength={2000} placeholder="描述你想要的画面" />
          </Field>
          <Field label="负向提示词" error={errors.negativePrompt?.message} hint="可选，描述不希望出现的内容">
            <Textarea {...register("negativePrompt")} rows={2} maxLength={1000} className="min-h-16" />
          </Field>

          <fieldset>
            <legend className="mb-1.5 text-sm font-medium">参考素材（可选，最多 {MAX_REFERENCE_MEDIA} 张）</legend>
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
                        aria-label={`选择素材 ${asset.originalName ?? asset.id}`}
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
                没有素材。可以到<Link href="/media" className="text-accent">我的素材</Link>上传。
              </p>
            )}
          </fieldset>
        </Card>

        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" loading={submission.submitting} disabled={!submitEnabled || Boolean(submission.unknownDraft)}>
            提交生成
          </Button>
          {simulated !== null && (
            <Badge tone={simulated ? "warning" : "info"}>{channelName ?? (simulated ? "模拟渠道" : "真实渠道")}</Badge>
          )}
          <p className="text-xs text-ink-muted">提交时先冻结次数；全部结果交付后才消费，失败或取消会退回。</p>
        </div>
      </form>
    </div>
  );
}
