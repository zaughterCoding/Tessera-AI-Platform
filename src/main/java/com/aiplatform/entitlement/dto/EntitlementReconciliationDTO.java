package com.aiplatform.entitlement.dto;

import lombok.Data;

/** 对账只读结果；扫描进度不能代替任务和权益包的实际数量。 */
@Data
public class EntitlementReconciliationDTO {
  private long eligibleOrderItems;
  private long registeredTasks;
  private long issuedGrants;
  private long pendingTasks;
  private long reviewTasks;
  private long failedMessages;
  private long expiredGrants;
  private long missingTasks;
  private long doneWithoutGrant;
  private long duplicateGrants;
  private long balanceViolations;
  private long ledgerMismatches;
  private long openAnomalies;
  private boolean exact;
  private String note;
}
