"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api, ApiError } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { Field, Input } from "@/shared/ui/field";
import { Card } from "@/shared/ui/layout";
import { QueryState } from "@/shared/ui/query-state";
import { useToast } from "@/shared/ui/toast";
import { AdminAction, FactList, ListSection, RecordTable, text, useAdminList, useAdminRecord, type AdminRecord } from "./parts";

const formSchema = z
  .object({
    productId: z.string().regex(/^\d+$/, "请输入商品 ID"),
    initialStock: z.coerce.number().int("库存必须是整数").min(1, "库存至少为 1"),
    beginAt: z.string().min(1, "请选择开始时间"),
    endAt: z.string().min(1, "请选择结束时间"),
  })
  .refine((values) => new Date(values.endAt) > new Date(values.beginAt), { path: ["endAt"], message: "结束时间必须晚于开始时间" });
type FormInput = z.input<typeof formSchema>;
type FormValues = z.output<typeof formSchema>;

// 后端按 UTC 解析，输入框是本地时间。
function toUtc(value: string): string {
  return new Date(value).toISOString().slice(0, 19);
}

function CreateCampaignForm() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormInput, unknown, FormValues>({ resolver: zodResolver(formSchema) });
  const create = useMutation({
    mutationFn: (values: FormValues) =>
      api("/admin/campaigns", z.unknown(), {
        method: "POST",
        body: { ...values, beginAt: toUtc(values.beginAt), endAt: toUtc(values.endAt) },
      }),
    onSuccess: () => {
      toast("活动已创建，发布前不会对用户开放", "success");
      reset();
      void queryClient.invalidateQueries({ queryKey: ["admin"] });
    },
    onError: (error) => toast(error instanceof ApiError ? error.message : "创建失败", "error"),
  });
  return (
    <Card>
      <h2 className="mb-3 font-medium">创建活动</h2>
      <form onSubmit={(event) => void handleSubmit((values) => create.mutate(values))(event)} noValidate className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5 lg:items-start">
        <Field label="限量商品 ID" error={errors.productId?.message}><Input {...register("productId")} inputMode="numeric" /></Field>
        <Field label="初始库存" error={errors.initialStock?.message}><Input {...register("initialStock")} inputMode="numeric" /></Field>
        <Field label="开始时间" error={errors.beginAt?.message}><Input {...register("beginAt")} type="datetime-local" /></Field>
        <Field label="结束时间" error={errors.endAt?.message}><Input {...register("endAt")} type="datetime-local" /></Field>
        <Button type="submit" variant="secondary" className="lg:mt-[1.65rem]" loading={create.isPending}>创建</Button>
      </form>
    </Card>
  );
}

function CampaignDetail({ id }: { id: string }) {
  const reconciliation = useAdminRecord(`/admin/campaigns/${id}/reconciliation`);
  return (
    <div className="flex flex-col gap-4">
      <Card>
        <h2 className="mb-3 font-medium">活动 {id} 对账</h2>
        <QueryState query={reconciliation}>
          {(record) => (
            <FactList
              record={record}
              fields={[
                ["exact", "完全一致"],
                ["stockDifferenceKind", "差异结论"],
                ["initialStock", "初始库存"],
                ["remainingStock", "数据库剩余"],
                ["redisStock", "Redis 库存"],
                ["committedOrders", "已成单"],
                ["inFlightReservations", "处理中"],
                ["reviewReservations", "待复核"],
                ["streamLag", "消息积压"],
                ["note", "说明"],
              ]}
            />
          )}
        </QueryState>
      </Card>
      <ListSection
        title="待复核预约"
        path={`/admin/campaigns/${id}/review`}
        params={{ limit: 50 }}
        rowKey="orderId"
        empty="没有待复核的预约"
        columns={[
          { key: "orderId", label: "订单" },
          { key: "userId", label: "用户" },
          { key: "state", label: "状态" },
          { key: "attempts", label: "尝试" },
          { key: "lastError", label: "错误" },
        ]}
        actions={(row) => (
          <div className="flex gap-2">
            <AdminAction label="重试" path={`/admin/campaigns/reservations/${text(row.orderId)}/retry`} />
            <AdminAction
              label="判定失败"
              danger
              path={`/admin/campaigns/reservations/${text(row.orderId)}/fail`}
              body={{ reason: "运营人工复核判定失败" }}
              confirm="只有尚未建单的预约可以判定失败，名额会被释放。"
            />
          </div>
        )}
      />
    </div>
  );
}

export function CampaignsPanel() {
  const campaigns = useAdminList("/admin/campaigns");
  const metrics = useAdminRecord("/admin/campaigns/stream-metrics");
  const [selected, setSelected] = useState<string | null>(null);

  const actions = (row: AdminRecord) => {
    const id = text(row.id);
    const state = text(row.publishState);
    return (
      <div className="flex gap-2">
        <Button size="sm" variant="ghost" onClick={() => setSelected(id)}>对账</Button>
        {state === "DRAFT" && <AdminAction label="发布" path={`/admin/campaigns/${id}/publish`} confirm="发布后用户即可参与。" />}
        {state === "ACTIVE" && <AdminAction label="暂停" danger path={`/admin/campaigns/${id}/pause`} confirm="暂停后新请求会被拒绝。" />}
        {state === "PAUSED" && <AdminAction label="恢复" path={`/admin/campaigns/${id}/recover`} confirm="需要没有处理中的预约和未投递消息，否则恢复会被拒绝。" />}
      </div>
    );
  };

  return (
    <div className="flex flex-col gap-6">
      <CreateCampaignForm />
      <section className="flex flex-col gap-3">
        <h2 className="font-medium">活动列表</h2>
        <QueryState query={campaigns}>
          {(rows) => (
            <RecordTable
              rows={rows}
              rowKey="id"
              actions={actions}
              empty="还没有活动"
              columns={[
                { key: "id", label: "ID" },
                { key: "applicationName", label: "应用" },
                { key: "productTitle", label: "商品" },
                { key: "publishState", label: "状态" },
                { key: "initialStock", label: "初始" },
                { key: "remainingStock", label: "剩余" },
                { key: "beginAt", label: "开始" },
                { key: "endAt", label: "结束" },
              ]}
            />
          )}
        </QueryState>
      </section>
      {selected && <CampaignDetail id={selected} />}
      <Card>
        <h2 className="mb-3 font-medium">Stream 水位</h2>
        <QueryState query={metrics}>
          {(record) => (
            <FactList
              record={record}
              fields={[
                ["streamLength", "长度"],
                ["consumerGroups", "消费组"],
                ["pendingCount", "待确认"],
                ["streamLag", "积压"],
                ["safeToTrim", "可裁剪"],
                ["blockedReason", "阻塞原因"],
              ]}
            />
          )}
        </QueryState>
      </Card>
      <ListSection
        title="死信"
        path="/admin/campaigns/dead-letters"
        params={{ limit: 20 }}
        rowKey="messageId"
        empty="没有死信"
        columns={[
          { key: "messageId", label: "消息" },
          { key: "campaignId", label: "活动" },
          { key: "orderId", label: "订单" },
          { key: "reason", label: "原因" },
          { key: "createdAt", label: "时间" },
        ]}
        actions={(row) => <AdminAction label="重放" path={`/admin/campaigns/dead-letters/${text(row.messageId)}/replay`} />}
      />
    </div>
  );
}
