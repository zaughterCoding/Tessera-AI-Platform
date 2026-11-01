"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { CreationGrid } from "@/features/community/creation-card";
import { creationSchema } from "@/features/community/schemas";
import { api, ApiError, noData } from "@/shared/api/client";
import { Button, LinkButton } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/feedback";
import { Field, Input } from "@/shared/ui/field";
import { Card } from "@/shared/ui/layout";
import { Pagination } from "@/shared/ui/pagination";
import { QueryState } from "@/shared/ui/query-state";
import { useToast } from "@/shared/ui/toast";
import { totalPagesOf } from "@/shared/url";
import { useSession } from "./session-provider";

const CREATION_PAGE_SIZE = 12;

const profileSchema = z.object({
  nickName: z.string().trim().min(1, "昵称不能为空").max(32, "昵称最多 32 字"),
});
type ProfileValues = z.infer<typeof profileSchema>;

const signSchema = z.object({
  alreadySigned: z.boolean().nullish(),
  consecutiveDays: z.number().nullish(),
});

function ProfileCard() {
  const { user } = useSession();
  const router = useRouter();
  const toast = useToast();
  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
    reset,
  } = useForm<ProfileValues>({ resolver: zodResolver(profileSchema), defaultValues: { nickName: user?.nickName ?? "" } });

  const save = useMutation({
    mutationFn: async (values: ProfileValues) => api("/user/me", noData, { method: "PUT", body: values }),
    onSuccess: (_result, values) => {
      reset(values);
      toast("昵称已更新", "success");
      router.refresh();
    },
    onError: (error) => toast(error instanceof ApiError ? error.message : "保存失败", "error"),
  });

  return (
    <Card>
      <h2 className="mb-3 font-medium">个人资料</h2>
      <form onSubmit={(event) => void handleSubmit((values) => save.mutate(values))(event)} noValidate className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <Field label="昵称" error={errors.nickName?.message}>
            <Input {...register("nickName")} maxLength={32} autoComplete="nickname" />
          </Field>
        </div>
        <Button type="submit" variant="secondary" className="mt-[1.65rem]" loading={save.isPending} disabled={!isDirty}>保存</Button>
      </form>
    </Card>
  );
}

function SignInCard() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const status = useQuery({
    queryKey: ["sign-in"],
    queryFn: async () => (await api("/user/sign/status", signSchema)).data,
  });
  const sign = useMutation({
    mutationFn: async () => (await api("/user/sign", signSchema, { method: "POST" })).data,
    onSuccess: (result) => {
      queryClient.setQueryData(["sign-in"], { ...result, alreadySigned: true });
      toast("签到成功", "success");
    },
    onError: () => toast("签到失败，请重试", "error"),
  });
  const signed = status.data?.alreadySigned ?? false;
  return (
    <Card className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-3">
        <CalendarCheck aria-hidden className="size-5 text-accent" />
        <div>
          <h2 className="font-medium">每日签到</h2>
          <p className="text-sm text-ink-muted">本月连续 {status.data?.consecutiveDays ?? 0} 天。签到只记录天数，不发放次数。</p>
        </div>
      </div>
      <Button variant="secondary" disabled={signed || status.isPending} loading={sign.isPending} onClick={() => sign.mutate()}>
        {signed ? "今日已签到" : "签到"}
      </Button>
    </Card>
  );
}

export function ProfilePanel({ page }: { page: number }) {
  const creations = useQuery({
    queryKey: ["creations", "mine", page],
    queryFn: () =>
      api("/creations/mine", z.array(creationSchema), {
        params: { limit: CREATION_PAGE_SIZE, offset: (page - 1) * CREATION_PAGE_SIZE },
      }),
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-4 md:grid-cols-2">
        <ProfileCard />
        <SignInCard />
      </div>
      <section aria-labelledby="my-creations">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 id="my-creations" className="font-medium">我的作品</h2>
          <LinkButton href="/create" size="sm" variant="secondary">发布作品</LinkButton>
        </div>
        <QueryState query={creations}>
          {({ data, total }) =>
            data.length ? (
              <>
                <CreationGrid creations={data} />
                <Pagination
                  page={page}
                  totalPages={totalPagesOf(total, CREATION_PAGE_SIZE)}
                  hrefFor={(target) => (target === 1 ? "/mine" : `/mine?page=${target}`)}
                />
              </>
            ) : (
              <EmptyState title="还没有作品" description="生成结果后可以分享为作品，也可以直接发布。" />
            )
          }
        </QueryState>
      </section>
    </div>
  );
}
