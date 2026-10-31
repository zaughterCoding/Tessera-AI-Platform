package com.aiplatform.entitlement.dto;

import lombok.Data;

/** 本人全部权益包的汇总余额；到期但尚未执行的清理已在查询时排除。 */
@Data
public class EntitlementBalanceDTO {
  private long totalCredits;
  private long availableCredits;
  private long frozenCredits;
  private long consumedCredits;
  private long expiredCredits;
  private long activeGrants;
  private long expiredGrants;
}
