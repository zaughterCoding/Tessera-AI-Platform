"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { z } from "zod";
import { api, ApiError } from "@/shared/api/client";
import { formatTime } from "@/shared/format";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { EmptyState } from "@/shared/ui/feedback";
import { DescriptionList, Table } from "@/shared/ui/layout";
import { QueryState } from "@/shared/ui/query-state";
import { useToast } from "@/shared/ui/toast";

// 运营台只读展示后端事实，字段多且偏内部，按宽松对象读取，由各面板声明要显示的列。
export const recordSchema = z.looseObject({});
export type AdminRecord = z.infer<typeof recordSchema>;
const recordList = z.array(recordSchema);

type Params = Record<string, string | number | undefined | null>;

export function useAdminRecord(path: string, params?: Params, enabled = true) {
  return useQuery({
    queryKey: ["admin", path, params],
    enabled,
    queryFn: async () => (await api(path, recordSchema, params ? { params } : {})).data,
  });
}

export function useAdminList(path: string, params?: Params) {
  return useQuery({
    queryKey: ["admin", path, params],
    queryFn: async () => (await api(path, recordList, params ? { params } : {})).data,
  });
}

export function text(value: unknown): string {
  if (value == null || value === "") return "—";
  if (typeof value === "boolean") return value ? "是" : "否";
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value)) return formatTime(value);
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

export type Column = { key: string; label: string; render?: (row: AdminRecord) => ReactNode };

export function RecordTable({
  rows,
  columns,
  rowKey,
  actions,
  empty = "暂无数据",
}: {
  rows: AdminRecord[];
  columns: Column[];
  rowKey: string;
  actions?: (row: AdminRecord) => ReactNode;
  empty?: string;
}) {
  if (!rows.length) return <EmptyState title={empty} />;
  return (
    <Table>
      <thead>
        <tr>
          {columns.map((column) => <th key={column.key}>{column.label}</th>)}
          {actions && <th>操作</th>}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr key={text(row[rowKey]) + index}>
            {columns.map((column) => (
              <td key={column.key} className="max-w-64 break-words">
                {column.render ? column.render(row) : text(row[column.key])}
              </td>
            ))}
            {actions && <td className="whitespace-nowrap">{actions(row)}</td>}
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

export function ListSection({
  title,
  path,
  params,
  columns,
  rowKey,
  actions,
  empty,
}: {
  title: string;
  path: string;
  params?: Params;
  columns: Column[];
  rowKey: string;
  actions?: (row: AdminRecord) => ReactNode;
  empty?: string;
}) {
  const query = useAdminList(path, params);
  return (
    <section className="flex flex-col gap-3">
      <h2 className="font-medium">{title}</h2>
      <QueryState query={query}>
        {(rows) => <RecordTable rows={rows} columns={columns} rowKey={rowKey} {...(actions ? { actions } : {})} {...(empty ? { empty } : {})} />}
      </QueryState>
    </section>
  );
}

export function FactList({ record, fields }: { record: AdminRecord; fields: Array<[string, string]> }) {
  return <DescriptionList items={fields.map(([key, label]) => ({ label, value: text(record[key]) }))} />;
}

type ActionResult = { applied?: boolean; outcome?: string };

// 所有写操作共用：二次确认，成功后刷新运营台数据。applied=false 是“条件不满足”，不能显示成成功。
export function AdminAction({
  label,
  path,
  body,
  confirm,
  danger = false,
  successText = "操作已提交，以刷新后的状态为准",
}: {
  label: string;
  path: string;
  body?: unknown;
  confirm?: string;
  danger?: boolean;
  successText?: string;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const run = useMutation({
    mutationFn: async () => (await api(path, z.unknown(), { method: "POST", ...(body === undefined ? {} : { body }) })).data as ActionResult | null,
    onSuccess: (result) => {
      setOpen(false);
      if (result && result.applied === false) toast(`未生效：${result.outcome ?? "当前状态不满足条件"}`, "info");
      else toast(result?.outcome ? `已登记：${result.outcome}` : successText, "success");
      void queryClient.invalidateQueries({ queryKey: ["admin"] });
    },
    onError: (error) => {
      setOpen(false);
      toast(error instanceof ApiError ? error.message : "操作未完成", "error");
    },
  });
  return (
    <>
      <Button
        size="sm"
        variant={danger ? "danger" : "secondary"}
        loading={run.isPending && !confirm}
        onClick={() => (confirm ? setOpen(true) : run.mutate())}
      >
        {label}
      </Button>
      {confirm && (
        <ConfirmDialog
          open={open}
          onOpenChange={setOpen}
          title={`确认${label}？`}
          description={confirm}
          confirmLabel={`确认${label}`}
          danger={danger}
          loading={run.isPending}
          onConfirm={() => run.mutate()}
        />
      )}
    </>
  );
}
