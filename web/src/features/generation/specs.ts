import type { Grant } from "@/features/entitlement/schemas";

export type Spec = {
  key: string;
  applicationId: string;
  applicationName: string;
  applicationVersion: number;
  modelVersion: string;
  imageSize: string;
  outputCount: number;
  available: number;
  grantCount: number;
};

// 规格只能来自已到账权益包的购买快照；同规格多包合并展示，由后端决定冻结哪个包。
export function specsFromGrants(grants: Grant[]): Spec[] {
  const specs = new Map<string, Spec>();
  for (const grant of grants) {
    if (grant.status !== "ACTIVE" || grant.availableCredits <= 0) continue;
    const key = [grant.applicationId, grant.applicationVersion, grant.modelVersion, grant.imageSize, grant.outputCount].join("|");
    const existing = specs.get(key);
    if (existing) {
      existing.available += grant.availableCredits;
      existing.grantCount += 1;
    } else {
      specs.set(key, {
        key,
        applicationId: grant.applicationId,
        applicationName: grant.applicationName,
        applicationVersion: grant.applicationVersion,
        modelVersion: grant.modelVersion,
        imageSize: grant.imageSize,
        outputCount: grant.outputCount,
        available: grant.availableCredits,
        grantCount: 1,
      });
    }
  }
  return [...specs.values()];
}
