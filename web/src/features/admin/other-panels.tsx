"use client";

import { useState } from "react";
import { z } from "zod";
import { api, ApiError } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { Notice } from "@/shared/ui/feedback";
import { Field, Input, Select } from "@/shared/ui/field";
import { Card } from "@/shared/ui/layout";
import { QueryState } from "@/shared/ui/query-state";
import { useToast } from "@/shared/ui/toast";
import { AdminAction, FactList, ListSection, recordSchema, RecordTable, text, useAdminRecord, type AdminRecord } from "./parts";

const AUDIT_LIMIT = 50;

export function MessagingPanel() {
  const [state, setState] = useState("");
  return (
    <div className="flex flex-col gap-6">
      <div className="max-w-xs">
        <Field label="按状态筛选任务">
          <Select value={state} onChange={(event) => setState(event.target.value)}>
            <option value="">全部</option>
            {["PENDING", "SENT", "DONE", "REVIEW"].map((item) => <option key={item} value={item}>{item}</option>)}
          </Select>
        </Field>
      </div>
      <ListSection
        title="可靠任务"
        path="/admin/messaging/outbox"
        params={{ limit: 50, offset: 0, state }}
        rowKey="id"
        columns={[
          { key: "id", label: "ID" },
          { key: "kind", label: "类型" },
          { key: "state", label: "状态" },
          { key: "aggregateId", label: "聚合" },
          { key: "attempts", label: "投递" },
          { key: "consumeAttempts", label: "消费" },
          { key: "lastError", label: "错误" },
          { key: "consumedAt", label: "完成时间" },
        ]}
        actions={(row) => (row.state === "REVIEW" ? <AdminAction label="重放" path={`/admin/messaging/outbox/${text(row.id)}/retry`} /> : null)}
      />
      <ListSection
        title="消费失败"
        path="/admin/messaging/failures"
        params={{ limit: 50, offset: 0 }}
        rowKey="id"
        empty="没有失败记录"
        columns={[
          { key: "consumerGroup", label: "消费组" },
          { key: "eventId", label: "事件" },
          { key: "attempts", label: "尝试" },
          { key: "lastError", label: "错误" },
          { key: "createdAt", label: "时间" },
        ]}
      />
    </div>
  );
}

export function GenerationPanel() {
  const [state, setState] = useState("");
  const [selected, setSelected] = useState<string | null>(null);
  const overview = useAdminRecord("/admin/generation/overview");
  const delivery = useAdminRecord(`/admin/generation/tasks/${selected}/delivery`, undefined, Boolean(selected));

  const counts = (record: AdminRecord, key: string) => {
    const value = record[key];
    return value && typeof value === "object"
      ? Object.entries(value as Record<string, unknown>).map(([name, count]) => `${name} ${text(count)}`).join(" · ") || "—"
      : text(value);
  };

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <h2 className="mb-3 font-medium">概览</h2>
        <QueryState query={overview}>
          {(record) => (
            <dl className="flex flex-col gap-2 text-sm">
              {[["taskStates", "任务"], ["resultStates", "结果"], ["cancelStates", "取消"]].map(([key, label]) => (
                <div key={key}>
                  <dt className="text-ink-muted">{label}</dt>
                  <dd className="break-words">{counts(record, key ?? "")}</dd>
                </div>
              ))}
            </dl>
          )}
        </QueryState>
      </Card>

      <div className="max-w-xs">
        <Field label="按状态筛选">
          <Input value={state} onChange={(event) => setState(event.target.value.trim().toUpperCase())} placeholder="如 REVIEW" />
        </Field>
      </div>
      <ListSection
        title="任务"
        path="/admin/generation/tasks"
        params={{ state, limit: 20, offset: 0 }}
        rowKey="id"
        columns={[
          { key: "id", label: "任务" },
          { key: "userId", label: "用户" },
          { key: "state", label: "状态" },
          { key: "accountingState", label: "账务" },
          { key: "attemptState", label: "尝试" },
          { key: "frozenUnits", label: "冻结" },
          { key: "createdAt", label: "时间" },
        ]}
        actions={(row) => {
          const id = text(row.id);
          return (
            <div className="flex gap-2">
              <Button size="sm" variant="ghost" onClick={() => setSelected(id)}>交付</Button>
              <AdminAction label="重试保存" path={`/admin/generation/tasks/${id}/results/retry`} />
              <AdminAction label="补结算" path={`/admin/generation/tasks/${id}/settlement/recover`} />
              <AdminAction label="取消" danger path={`/admin/generation/tasks/${id}/cancel`} body={{ reason: "运营台请求取消" }} confirm="仍走正常取消流程，上游若已完成任务会照常交付。" />
            </div>
          );
        }}
      />
      {selected && (
        <Card>
          <h2 className="mb-3 font-medium">任务 {selected} 交付核对</h2>
          <QueryState query={delivery}>
            {(record) => (
              <div className="flex flex-col gap-4">
                <FactList
                  record={record}
                  fields={[
                    ["taskState", "任务状态"],
                    ["accountingState", "账务"],
                    ["savedCount", "已保存"],
                    ["outputCount", "应保存"],
                  ]}
                />
                <RecordTable
                  rows={z.array(recordSchema).catch([]).parse(record.results)}
                  rowKey="id"
                  columns={[
                    { key: "position", label: "位置" },
                    { key: "state", label: "状态" },
                    { key: "saveAttempts", label: "尝试" },
                    { key: "contentDigest", label: "摘要" },
                    { key: "lastError", label: "错误" },
                  ]}
                />
              </div>
            )}
          </QueryState>
        </Card>
      )}
    </div>
  );
}

export function AnalyticsPanel() {
  const backfill = useAdminRecord("/admin/analytics/backfill");
  return (
    <div className="flex flex-col gap-6">
      <Card>
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 className="font-medium">热榜补数</h2>
          <AdminAction label="重新开始补数" path="/admin/analytics/backfill" confirm="会从当前记录的起点重新推进。" />
        </div>
        {backfill.isError && backfill.error instanceof ApiError && backfill.error.status === 404 ? (
          <p className="text-sm text-ink-muted">还没有补数记录。</p>
        ) : (
          <QueryState query={backfill}>
            {(record) => (
              <FactList
                record={record}
                fields={[
                  ["state", "状态"],
                  ["processed", "已处理"],
                  ["cursorEventId", "游标"],
                  ["rangeFrom", "起点"],
                  ["updatedAt", "更新时间"],
                  ["lastError", "错误"],
                ]}
              />
            )}
          </QueryState>
        )}
      </Card>
      <ListSection
        title="分析事件投递"
        path="/admin/analytics/outbox"
        params={{ limit: 50, offset: 0 }}
        rowKey="eventId"
        empty="没有待投递事件"
        columns={[
          { key: "eventId", label: "事件" },
          { key: "action", label: "动作" },
          { key: "applicationId", label: "应用" },
          { key: "state", label: "状态" },
          { key: "attempts", label: "尝试" },
        ]}
        actions={(row) => <AdminAction label="重放" path={`/admin/analytics/outbox/${text(row.eventId)}/retry`} />}
      />
      <ListSection
        title="消费失败"
        path="/admin/analytics/failures"
        params={{ limit: 50, offset: 0 }}
        rowKey="id"
        empty="没有失败记录"
        columns={[
          { key: "topic", label: "主题" },
          { key: "partition", label: "分区" },
          { key: "offset", label: "位点" },
          { key: "errorCode", label: "错误" },
          { key: "createdAt", label: "时间" },
        ]}
      />
    </div>
  );
}

export function OperationsPanel() {
  const [action, setAction] = useState("");
  const [offset, setOffset] = useState(0);
  const retention = useAdminRecord("/admin/operations/retention");
  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-3">
        <h2 className="font-medium">数据保留</h2>
        <QueryState query={retention}>
          {(record) => (
            <RecordTable
              rows={z.array(recordSchema).catch([]).parse(record.database)}
              rowKey="target"
              columns={[
                { key: "label", label: "数据" },
                { key: "retentionDays", label: "保留天数" },
                { key: "eligible", label: "可清理" },
                { key: "rule", label: "规则" },
              ]}
              actions={(row) => (
                <AdminAction
                  label="清理"
                  danger
                  path={`/admin/operations/retention/${text(row.target)}/run`}
                  body={{ maxBatches: 10 }}
                  confirm={`按保留期清理 ${text(row.label)}，最多 10 批。在途、待复核和仍被引用的数据不受影响。`}
                  successText="清理已完成"
                />
              )}
            />
          )}
        </QueryState>
      </section>

      <div className="max-w-xs">
        <Field label="按动作筛选审计">
          <Input value={action} onChange={(event) => { setAction(event.target.value.trim()); setOffset(0); }} />
        </Field>
      </div>
      <ListSection
        title="操作审计"
        path="/admin/operations/audit"
        params={{ limit: AUDIT_LIMIT, offset, action }}
        rowKey="id"
        columns={[
          { key: "occurredAt", label: "时间" },
          { key: "operatorId", label: "操作人" },
          { key: "action", label: "动作" },
          { key: "objectType", label: "对象" },
          { key: "objectId", label: "对象 ID" },
          { key: "outcome", label: "结果" },
          { key: "requestId", label: "请求号" },
        ]}
      />
      <div className="flex gap-2">
        <Button size="sm" variant="secondary" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - AUDIT_LIMIT))}>上一页</Button>
        <Button size="sm" variant="secondary" onClick={() => setOffset(offset + AUDIT_LIMIT)}>下一页</Button>
      </div>
    </div>
  );
}

export function ExperiencePanel() {
  const toast = useToast();
  const capabilities = useAdminRecord("/admin/experience/capabilities");
  const [applicationId, setApplicationId] = useState("");
  const [orderId, setOrderId] = useState("");
  const [trace, setTrace] = useState<AdminRecord | null>(null);
  const [progress, setProgress] = useState<AdminRecord | null>(null);

  // 这组接口默认关闭，关闭时后端返回 404。
  if (capabilities.error instanceof ApiError && capabilities.error.status === 404) {
    return (
      <Notice tone="info" title="技术体验未开启">
        设置环境变量 APP_EXPERIENCE_ENABLED=true 并重启后端后可用。
      </Notice>
    );
  }

  async function load(path: string, onData: (record: AdminRecord) => void) {
    try {
      onData((await api(path, recordSchema)).data);
    } catch (error) {
      toast(error instanceof ApiError ? error.message : "查询失败", "error");
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <h2 className="mb-3 font-medium">技术能力状态</h2>
        <QueryState query={capabilities}>
          {(record) => (
            <FactList
              record={record}
              fields={[
                ["cacheEnabled", "五层缓存"],
                ["memcacheEnabled", "Memcache"],
                ["cdcEnabled", "CDC 已开启"],
                ["cdcConnected", "CDC 已连接"],
                ["analyticsEnabled", "行为分析"],
                ["analyticsConsumerRunning", "分析消费者"],
                ["analyticsConsumerLag", "消费积压"],
                ["messagingTransport", "消息传输"],
                ["outboxPending", "待投递任务"],
                ["sampledAt", "采样时间"],
              ]}
            />
          )}
        </QueryState>
      </Card>

      <Card className="flex flex-col gap-3">
        <h2 className="font-medium">缓存读路径追踪</h2>
        <form
          className="flex items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (/^\d+$/.test(applicationId)) void load(`/admin/experience/cache-trace/${applicationId}`, setTrace);
          }}
        >
          <div className="min-w-0 flex-1"><Field label="应用 ID"><Input value={applicationId} inputMode="numeric" onChange={(event) => setApplicationId(event.target.value)} /></Field></div>
          <Button type="submit" variant="secondary">追踪</Button>
        </form>
        {trace && (
          <>
            <p className="text-sm">{text(trace.applicationName)}：连续读取的命中层</p>
            <pre className="overflow-x-auto rounded-md bg-sunken p-3 text-xs">{JSON.stringify(trace.reads, null, 2)}</pre>
          </>
        )}
      </Card>

      <Card className="flex flex-col gap-3">
        <h2 className="font-medium">订单可靠消息进度</h2>
        <form
          className="flex items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (/^\d+$/.test(orderId)) void load(`/admin/experience/orders/${orderId}/progress`, setProgress);
          }}
        >
          <div className="min-w-0 flex-1"><Field label="订单 ID"><Input value={orderId} inputMode="numeric" onChange={(event) => setOrderId(event.target.value)} /></Field></div>
          <Button type="submit" variant="secondary">查询</Button>
        </form>
        {progress && (
          <RecordTable
            rows={z.array(recordSchema).catch([]).parse(progress.tasks)}
            rowKey="id"
            empty="这个订单没有任务"
            columns={[
              { key: "kind", label: "类型" },
              { key: "state", label: "状态" },
              { key: "attempts", label: "投递" },
              { key: "consumedAt", label: "完成时间" },
            ]}
          />
        )}
      </Card>
      <Notice>这里只读展示后端真实状态，用于演示和排查。</Notice>
    </div>
  );
}
