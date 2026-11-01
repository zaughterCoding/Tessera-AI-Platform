export const ADMIN_TABS = [
  { key: "campaigns", label: "限量活动" },
  { key: "messaging", label: "可靠任务" },
  { key: "generation", label: "生成交付" },
  { key: "analytics", label: "行为分析" },
  { key: "operations", label: "审计与保留" },
  { key: "experience", label: "技术体验" },
] as const;

export type AdminTab = (typeof ADMIN_TABS)[number]["key"];

export function parseAdminTab(value: string | undefined): AdminTab {
  return ADMIN_TABS.find((tab) => tab.key === value)?.key ?? "campaigns";
}
