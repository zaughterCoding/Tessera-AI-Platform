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
  /** 全部消费组的聚合水位；裁剪与恢复只认这些值。 */
  private int groupCount;
  private long totalPending;
  private long otherPending;
  private long maxGroupLag;
  private int unknownLagGroups;
  private boolean allGroupsCaughtUp;
  private String trimWatermark;
}
