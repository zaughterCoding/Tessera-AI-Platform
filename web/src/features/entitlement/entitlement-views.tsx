"use client";

import { useQuery } from "@tanstack/react-query";
import { z } from "zod";
import { api } from "@/shared/api/client";
import { formatDate, formatTime } from "@/shared/format";
import { LinkButton } from "@/shared/ui/button";
import { Badge, EmptyState } from "@/shared/ui/feedback";
import { Card, DescriptionList, PageHeader, Table } from "@/shared/ui/layout";
import Link from "@/shared/ui/link";
import { Pagination } from "@/shared/ui/pagination";
import { QueryState } from "@/shared/ui/query-state";
import { totalPagesOf } from "@/shared/url";
import { balanceSchema, grantSchema, ledgerEntrySchema, operationLabel, type Grant } from "./schemas";

const GRANT_PAGE_SIZE = 10;
const LEDGER_PAGE_SIZE = 20;

function CreditMeter({ grant }: { grant: Grant }) {
  const segments = [
    { key: "可用", value: grant.availableCredits, className: "bg-success" },
    { key: "冻结", value: grant.frozenCredits, className: "bg-info" },
    { key: "已用", value: grant.consumedCredits, className: "bg-ink-muted" },
    { key: "过期", value: grant.expiredCredits, className: "bg-line-strong" },
  ];
  return (
    <div>
      <div aria-hidden className="flex h-1.5 overflow-hidden rounded-full bg-sunken">
        {segments.map((segment) => (
          <span key={segment.key} className={segment.className} style={{ width: `${(segment.value / Math.max(grant.totalCredits, 1)) * 100}%` }} />
        ))}
      </div>
      <p className="mt-1 text-xs text-ink-muted">
        {segments.map((segment) => `${segment.key} ${segment.value}`).join(" · ")}
      </p>
    </div>
  );
}

export function BalanceSummary() {
  const query = useQuery({
    queryKey: ["entitlement", "balance"],
    queryFn: async () => (await api("/entitlement/balance", balanceSchema)).data,
  });
  return (
    <QueryState query={query} skeleton={<div className="h-20" />}>
      {(balance) => (
        <dl className="mb-6 grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-line bg-line sm:grid-cols-4">
          {[
            { label: "可用次数", value: balance.availableCredits, strong: true },
            { label: "冻结中", value: balance.frozenCredits },
            { label: "已消费", value: balance.consumedCredits },
            { label: "已过期", value: balance.expiredCredits },
          ].map((item) => (
            <div key={item.label} className="bg-surface p-4">
              <dt className="text-xs text-ink-muted">{item.label}</dt>
              <dd className={item.strong ? "text-2xl font-semibold text-accent" : "text-2xl font-semibold"}>{item.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </QueryState>
  );
}

export function GrantList({ page }: { page: number }) {
  const query = useQuery({
    queryKey: ["entitlement", "grants", page],
    queryFn: () => api("/entitlement/grants", z.array(grantSchema), { params: { page, size: GRANT_PAGE_SIZE } }),
  });
  return (
    <QueryState query={query}>
      {({ data: grants, total }) =>
        grants.length ? (
          <>
            <ul className="flex flex-col gap-3">
              {grants.map((grant) => (
                <li key={grant.id}>
                  <Card className="flex flex-col gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge tone={grant.status === "ACTIVE" ? "success" : "neutral"}>{grant.status === "ACTIVE" ? "有效" : "已过期"}</Badge>
                      <Link href={`/entitlements/${grant.id}`} className="font-medium hover:text-accent">{grant.applicationName}</Link>
                      <span className="text-sm text-ink-muted">{grant.modelVersion} / {grant.imageSize} / 每次 {grant.outputCount} 张</span>
                    </div>
                    <CreditMeter grant={grant} />
                    <p className="text-xs text-ink-muted">有效期至 {formatDate(grant.expiresAt)}</p>
                  </Card>
                </li>
              ))}
            </ul>
            <Pagination
              page={page}
              totalPages={totalPagesOf(total, GRANT_PAGE_SIZE)}
              hrefFor={(target) => (target === 1 ? "/entitlements" : `/entitlements?page=${target}`)}
            />
          </>
        ) : (
          <EmptyState
            title="还没有权益"
            description="购买使用包并完成支付后，次数会异步发放到这里。"
            action={<LinkButton href="/apps" variant="secondary">浏览应用</LinkButton>}
          />
        )
      }
    </QueryState>
  );
}

function signed(value: number): string {
  return value > 0 ? `+${value}` : String(value);
}

export function GrantDetail({ id, page }: { id: string; page: number }) {
  const grantQuery = useQuery({
    queryKey: ["entitlement", "grant", id],
    queryFn: async () => (await api(`/entitlement/grants/${id}`, grantSchema)).data,
  });
  const ledgerQuery = useQuery({
    queryKey: ["entitlement", "ledger", id, page],
    queryFn: () => api(`/entitlement/grants/${id}/ledger`, z.array(ledgerEntrySchema), { params: { page, size: LEDGER_PAGE_SIZE } }),
  });

  return (
    <QueryState query={grantQuery} backHref="/entitlements">
      {(grant) => (
        <>
          <PageHeader
            title={grant.applicationName}
            description={`权益包 ${grant.id}`}
            actions={<LinkButton href={`/studio?app=${grant.applicationId}`}>去创作</LinkButton>}
          />
          <Card className="mb-6 flex flex-col gap-4">
            <CreditMeter grant={grant} />
            <DescriptionList
              items={[
                { label: "规格", value: `${grant.modelVersion} / ${grant.imageSize} / 每次 ${grant.outputCount} 张` },
                { label: "来源订单", value: <Link href={`/orders/${grant.orderId}`} className="text-accent">{grant.orderId}</Link> },
                { label: "生效时间", value: formatTime(grant.effectiveFrom) },
                { label: "到期时间", value: formatTime(grant.expiresAt) },
              ]}
            />
          </Card>
          <h2 className="mb-3 font-medium">流水</h2>
          <QueryState query={ledgerQuery}>
            {({ data: entries, total }) => (
              <>
                <Table>
                  <thead>
                    <tr><th>时间</th><th>类型</th><th>可用</th><th>冻结</th><th>已用</th><th>说明</th></tr>
                  </thead>
                  <tbody>
                    {entries.map((entry) => (
                      <tr key={entry.id}>
                        <td className="whitespace-nowrap">{formatTime(entry.createdAt)}</td>
                        <td>
                          <Badge>{operationLabel(entry.operationType)}</Badge>
                          {entry.referenceType === "GENERATION_TASK" && entry.referenceId && (
                            <Link href={`/tasks/${entry.referenceId}`} className="ml-2 text-xs text-accent">任务 {entry.referenceId}</Link>
                          )}
                        </td>
                        <td>{signed(entry.deltaAvailable)} → {entry.availableAfter}</td>
                        <td>{signed(entry.deltaFrozen)} → {entry.frozenAfter}</td>
                        <td>{signed(entry.deltaConsumed)} → {entry.consumedAfter}</td>
                        <td className="text-ink-muted">{entry.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
                <Pagination
                  page={page}
                  totalPages={totalPagesOf(total, LEDGER_PAGE_SIZE)}
                  hrefFor={(target) => (target === 1 ? `/entitlements/${id}` : `/entitlements/${id}?page=${target}`)}
                />
              </>
            )}
          </QueryState>
        </>
      )}
    </QueryState>
  );
}
