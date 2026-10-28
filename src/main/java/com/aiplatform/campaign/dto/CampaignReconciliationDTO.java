package com.aiplatform.campaign.dto;

import lombok.Data;

/** 对账只读结果；Redis 差异单独列出，任何情况下都不自动覆盖库存。 */
@Data
public class CampaignReconciliationDTO {
  private Long campaignId;
  private Long productId;
  private Integer generation;
  private Integer initialStock;
  private Integer remainingStock;
  private long committedOrders;
  private long inFlightReservations;
  private long reviewReservations;
  private boolean formulaMatched;
  private Long redisStock;
  private Integer redisGeneration;
  private Long redisDifference;
  /** CONFIRMED、IN_FLIGHT、UNEXPLAINED 或 UNKNOWN；只有 CONFIRMED 允许 exact。 */
  private String stockDifferenceKind;
  private long pendingMessages;
  private long streamLag;
  private int unknownLagGroups;
  /** 未完成的建单、重试或库存释放事件。 */
  private long pendingEvents;
  private boolean exact;
  private String note;
}
