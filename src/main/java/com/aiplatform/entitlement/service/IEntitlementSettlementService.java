package com.aiplatform.entitlement.service;

/**
 * 任务最终结算的内部能力：只允许在调用方短事务内，针对已冻结任务执行一次。
 * 不提供任意改余额入口；成功只消费，确定失败或取消才释放，已消费次数不可退回。
 */
public interface IEntitlementSettlementService {
  SettlementResult consumeForTask(long taskId, long grantId, int units);

  SettlementResult releaseForTask(long taskId, long grantId, int units, String reason);

  record SettlementResult(String kind, String releaseTarget, int units, long ledgerId,
      int totalAfter, int availableAfter, int frozenAfter, int consumedAfter,
      int expiredAfter) {
  }
}
