package com.aiplatform.campaign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 参与活动只提交活动身份和请求键；价格、库存与资格一律由服务端判定。 */
@Data
public class CampaignAdmissionForm {
  @NotNull
  @Positive
  private Long campaignId;

  @NotBlank
  @Pattern(regexp = "[A-Za-z0-9_-]{8,64}")
  private String requestKey;
}
