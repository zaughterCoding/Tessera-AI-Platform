"use client";

import { clsx } from "clsx";
import Link from "@/shared/ui/link";
import { CampaignsPanel } from "./campaigns-panel";
import { AnalyticsPanel, ExperiencePanel, GenerationPanel, MessagingPanel, OperationsPanel } from "./other-panels";
import { ADMIN_TABS, type AdminTab } from "./tabs";

const PANELS: Record<AdminTab, () => React.ReactNode> = {
  campaigns: CampaignsPanel,
  messaging: MessagingPanel,
  generation: GenerationPanel,
  analytics: AnalyticsPanel,
  operations: OperationsPanel,
  experience: ExperiencePanel,
};

export function AdminConsole({ tab }: { tab: AdminTab }) {
  const Panel = PANELS[tab];
  return (
    <>
      <nav aria-label="运营台分区" className="mb-6 flex gap-4 overflow-x-auto border-b border-line text-sm">
        {ADMIN_TABS.map((item) => {
          const active = item.key === tab;
          return (
            <Link
              key={item.key}
              href={`/admin?tab=${item.key}`}
              aria-current={active ? "page" : undefined}
              className={clsx("-mb-px border-b-2 px-1 pb-2 whitespace-nowrap", active ? "border-accent font-medium text-ink" : "border-transparent text-ink-muted hover:text-ink")}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
      <Panel />
    </>
  );
}
