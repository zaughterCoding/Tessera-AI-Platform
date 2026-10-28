package com.aiplatform.campaign.dto;

import com.aiplatform.trade.dto.PurchaseOrderDTO;
import java.time.LocalDateTime;
import lombok.Data;

/** 预约查询把“已受理”“建单成功”“已付款”拆成独立提示，不冒充权益到账。 */
@Data
public class CampaignReservationDTO {
  private Long orderId;
  private Long campaignId;
  private Long userId;
  private Integer generation;
  private String state;
  private boolean stockReleased;
  private String requestKey;
  private Integer attempts;
  private String lastError;
  private LocalDateTime createdAt;
  private LocalDateTime releasedAt;
  private PurchaseOrderDTO order;
  private String acceptanceNotice;
  private String reservationNotice;
  private String entitlementNotice;
  private String consistencyNotice;
}
