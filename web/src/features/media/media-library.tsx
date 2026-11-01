"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2, Upload } from "lucide-react";
import Image from "next/image";
import { useRef, useState } from "react";
import { z } from "zod";
import { mediaAssetSchema, mediaSrc, type MediaAsset } from "@/features/generation/schemas";
import { api, ApiError, noData } from "@/shared/api/client";
import { formatBytes } from "@/shared/format";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { EmptyState, Notice } from "@/shared/ui/feedback";
import { Pagination } from "@/shared/ui/pagination";
import { QueryState } from "@/shared/ui/query-state";
import { useToast } from "@/shared/ui/toast";
import { totalPagesOf } from "@/shared/url";
import { uploadMedia, validateMediaFile } from "./upload";

const PAGE_SIZE = 24;

const quotaSchema = z.object({
  usedBytes: z.union([z.string(), z.number()]),
  assetCount: z.number(),
  byteLimit: z.union([z.string(), z.number()]),
  assetLimit: z.number(),
});

export function MediaLibrary({ page }: { page: number }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<MediaAsset | null>(null);

  const quota = useQuery({
    queryKey: ["media", "quota"],
    queryFn: async () => (await api("/media/quota", quotaSchema)).data,
  });
  const assets = useQuery({
    queryKey: ["media", "list", page],
    queryFn: () => api("/media/mine", z.array(mediaAssetSchema), { params: { limit: PAGE_SIZE, offset: (page - 1) * PAGE_SIZE } }),
    // 删除由后台异步完成，处理中的素材需要刷新才能看到最终状态。
    refetchInterval: (current) => (current.state.data?.data.some((asset) => asset.state === "DELETING" || asset.state === "PENDING") ? 3000 : false),
  });

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ["media"] });

  async function onFile(file: File | undefined) {
    if (!file) return;
    setUploadError(null);
    const problem = validateMediaFile(file);
    if (problem) {
      setUploadError(problem);
      return;
    }
    setProgress(0);
    try {
      await uploadMedia(file, setProgress);
      toast("上传成功，素材默认仅自己可见", "success");
      refresh();
    } catch (error) {
      setUploadError(error instanceof Error ? error.message : "上传失败");
    } finally {
      setProgress(null);
      if (input.current) input.current.value = "";
    }
  }

  const remove = useMutation({
    mutationFn: async (asset: MediaAsset) => api(`/media/${asset.id}`, noData, { method: "DELETE" }),
    onSuccess: () => {
      setDeleting(null);
      toast("素材已删除");
      refresh();
    },
    onError: (error) => {
      setDeleting(null);
      toast(error instanceof ApiError && error.status === 409 ? "素材正被作品或任务使用，不能删除" : "删除失败", "error");
    },
  });

  const usedPercent = quota.data ? Math.min(100, (Number(quota.data.usedBytes) / Math.max(Number(quota.data.byteLimit), 1)) * 100) : 0;

  return (
    <>
      <div className="mb-6 flex flex-wrap items-center gap-4 rounded-lg border border-line bg-surface p-4">
        <input
          ref={input}
          type="file"
          accept="image/png,image/jpeg,image/webp"
          className="sr-only"
          aria-label="选择要上传的图片"
          onChange={(event) => void onFile(event.target.files?.[0])}
        />
        <Button loading={progress !== null} onClick={() => input.current?.click()}>
          <Upload aria-hidden className="size-4" />
          {progress === null ? "上传素材" : `上传中 ${progress}%`}
        </Button>
        <p className="text-xs text-ink-muted">PNG、JPEG、WebP，单张不超过 5 MB。</p>
        {quota.data && (
          <div className="ml-auto w-full sm:w-64">
            <div aria-hidden className="h-1.5 overflow-hidden rounded-full bg-sunken">
              <div className="h-full bg-accent" style={{ width: `${usedPercent}%` }} />
            </div>
            <p className="mt-1 text-xs text-ink-muted">
              已用 {formatBytes(quota.data.usedBytes)} / {formatBytes(quota.data.byteLimit)} · {quota.data.assetCount}/{quota.data.assetLimit} 个
            </p>
          </div>
        )}
      </div>
      {uploadError && <div className="mb-4"><Notice tone="danger" title="上传失败">{uploadError}</Notice></div>}

      <QueryState query={assets}>
        {({ data, total }) =>
          data.length ? (
            <>
              <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
                {data.map((asset) => {
                  const src = mediaSrc(asset.url);
                  return (
                    <li key={asset.id} className="overflow-hidden rounded-lg border border-line bg-surface">
                      <div className="relative aspect-square bg-sunken">
                        {src && asset.state === "ACTIVE" && (
                          <Image src={src} alt={asset.originalName ?? `素材 ${asset.id}`} fill sizes="(min-width: 1024px) 16vw, (min-width: 640px) 25vw, 50vw" className="object-cover" />
                        )}
                      </div>
                      <div className="flex items-center justify-between gap-1 p-2 text-xs text-ink-muted">
                        <span className="min-w-0 truncate">{asset.state === "ACTIVE" ? formatBytes(asset.sizeBytes) : "处理中"}</span>
                        {asset.state === "ACTIVE" && (
                          <button
                            type="button"
                            aria-label={`删除素材 ${asset.originalName ?? asset.id}`}
                            onClick={() => setDeleting(asset)}
                            className="shrink-0 rounded p-1 hover:bg-sunken hover:text-danger"
                          >
                            <Trash2 aria-hidden className="size-3.5" />
                          </button>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ul>
              <Pagination
                page={page}
                totalPages={totalPagesOf(total, PAGE_SIZE)}
                hrefFor={(target) => (target === 1 ? "/media" : `/media?page=${target}`)}
              />
            </>
          ) : (
            <EmptyState title="还没有素材" description="上传的图片可以作为生成参考，也可以放进作品。" />
          )
        }
      </QueryState>

      <ConfirmDialog
        open={Boolean(deleting)}
        onOpenChange={(open) => { if (!open) setDeleting(null); }}
        title="删除这张素材？"
        description="删除后不能恢复。被作品或任务引用的素材不能删除。"
        confirmLabel="确认删除"
        danger
        loading={remove.isPending}
        onConfirm={() => { if (deleting) remove.mutate(deleting); }}
      />
    </>
  );
}
