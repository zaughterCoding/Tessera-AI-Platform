package com.aiplatform.campaign.dto;

import lombok.Data;

/** Stream 保留水位只由消费组、Pending 与恢复边界共同决定，禁止定长裁剪。 */
@Data
public class CampaignStreamMetricsDTO {
  private String stream;
  private String group;
  private long streamLength;
  private long pendingCount;
  private long streamLag;
  private Long oldestPendingAgeMillis;
  private String trimWatermark;
  private boolean safeToTrim;
  private String blockedReason;
  private long pendingHandoffs;
  private long reviewReservations;
}
