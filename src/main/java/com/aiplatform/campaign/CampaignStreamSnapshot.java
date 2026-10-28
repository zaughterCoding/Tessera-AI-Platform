package com.aiplatform.campaign;

import lombok.Data;

/** Stream 只读快照；裁剪与对账都以消费组和 Pending 的真实水位为准。 */
@Data
public class CampaignStreamSnapshot {
  private boolean streamExists;
  private boolean groupExists;
  private long length;
  private long pending;
  private long lag;
  private Long oldestPendingAgeMillis;
  private String lastDeliveredId;
}
