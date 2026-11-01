import type { Metadata } from "next";
import { AdminConsole } from "@/features/admin/admin-console";
import { parseAdminTab } from "@/features/admin/tabs";
import { requireAdmin } from "@/features/auth/current-user";
import { PageHeader } from "@/shared/ui/layout";
import { firstParam, type SearchParams } from "@/shared/url";

export const metadata: Metadata = { title: "运营台" };

// 服务端校验角色，普通用户拿不到运营台代码以外的任何数据；每个接口后端仍单独鉴权。
export default async function AdminPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  await requireAdmin();
  const tab = parseAdminTab(firstParam((await searchParams).tab));
  return (
    <>
      <PageHeader title="运营台" description="查看后端真实状态并执行受控的恢复操作。" />
      <AdminConsole tab={tab} />
    </>
  );
}
