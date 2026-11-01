"use client";

import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useSession } from "@/features/auth/session-provider";
import { api, ApiError, noData } from "@/shared/api/client";
import { Button, LinkButton } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { useToast } from "@/shared/ui/toast";

type Pending = "publish" | "unpublish" | "delete" | null;

const COPY = {
  publish: { title: "公开这个作品？", description: "公开后所有访客都能看到作品和所选图片。", confirm: "确认公开" },
  unpublish: { title: "取消发布？", description: "作品详情和图片地址会立即失效，只有你自己能看到。", confirm: "取消发布" },
  delete: { title: "删除这个作品？", description: "删除后不能恢复。仍被其他作品引用的图片不会被删除。", confirm: "确认删除" },
} as const;

export function OwnerActions({ creationId, authorId, published }: { creationId: string; authorId: string; published: boolean }) {
  const { user } = useSession();
  const router = useRouter();
  const toast = useToast();
  const [pending, setPending] = useState<Pending>(null);

  const action = useMutation({
    mutationFn: async (kind: Exclude<Pending, null>) => {
      if (kind === "delete") {
        await api(`/creations/${creationId}`, noData, { method: "DELETE" });
      } else {
        await api(`/creations/${creationId}/visibility`, noData, { method: "PUT", body: { published: kind === "publish" } });
      }
      return kind;
    },
    onSuccess: (kind) => {
      setPending(null);
      if (kind === "delete") {
        toast("作品已删除");
        router.replace("/mine");
      } else {
        toast(kind === "publish" ? "作品已公开" : "已取消发布", "success");
      }
      router.refresh();
    },
    onError: (error) => {
      setPending(null);
      toast(error instanceof ApiError ? error.message : "操作没有成功", "error");
    },
  });

  if (user?.id !== authorId) return null;
  const copy = pending ? COPY[pending] : null;
  return (
    <div className="flex flex-wrap gap-2">
      <Button size="sm" variant="secondary" onClick={() => setPending(published ? "unpublish" : "publish")}>
        {published ? "取消发布" : "公开作品"}
      </Button>
      <LinkButton size="sm" variant="secondary" href={`/create?edit=${creationId}`}>编辑</LinkButton>
      <Button size="sm" variant="danger" onClick={() => setPending("delete")}>删除</Button>
      <ConfirmDialog
        open={Boolean(pending)}
        onOpenChange={(open) => { if (!open) setPending(null); }}
        title={copy?.title ?? ""}
        description={copy?.description ?? ""}
        confirmLabel={copy?.confirm ?? ""}
        danger={pending !== "publish"}
        loading={action.isPending}
        onConfirm={() => { if (pending) action.mutate(pending); }}
      />
    </div>
  );
}
