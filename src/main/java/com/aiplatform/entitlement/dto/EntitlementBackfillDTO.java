package com.aiplatform.entitlement.dto;

import lombok.Data;

/** 一批补发只登记幂等任务；实际发放结果由权益包和流水对账确认。 */
@Data
public class EntitlementBackfillDTO {
  private Long afterOrderId;
  private Long nextAfterOrderId;
  private int scanned;
  private int trusted;
  private int registered;
  private int existingTasks;
  private int existingGrants;
  private int anomalies;
  private boolean hasMore;
  private long remainingOrders;
}
