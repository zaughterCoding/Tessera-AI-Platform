package com.aiplatform.campaign.dto;

import com.aiplatform.campaign.entity.CampaignDeadLetter;
import java.time.LocalDateTime;
import lombok.Data;

/** 无法自动处理的预约消息；复核后可安全重放。 */
@Data
public class CampaignDeadLetterDTO {
  private String messageId;
  private Long campaignId;
  private Long orderId;
  private String payload;
  private String reason;
  private LocalDateTime createdAt;

  public static CampaignDeadLetterDTO from(CampaignDeadLetter letter) {
    CampaignDeadLetterDTO view = new CampaignDeadLetterDTO();
    view.setMessageId(letter.getMessageId());
    view.setCampaignId(letter.getCampaignId());
    view.setOrderId(letter.getOrderId());
    view.setPayload(letter.getPayload());
    view.setReason(letter.getReason());
    view.setCreatedAt(letter.getCreatedAt());
    return view;
  }
}
