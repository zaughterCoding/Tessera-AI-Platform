package com.aiplatform.campaign.dto;

import lombok.Data;

/** 受理结果只说明请求已记录，不宣称建单成功、付款完成或权益到账。 */
@Data
public class CampaignAdmissionDTO {
  private Long orderId;
  private Long campaignId;
  private Integer generation;
  private String state;
  private String requestKey;
  private boolean existing;
  private String acceptanceNotice;
  private String reservationNotice;
  private String entitlementNotice;
}
