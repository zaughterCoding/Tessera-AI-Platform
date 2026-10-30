package com.aiplatform.campaign.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 人工判定预约未成立的原因；仅用于审计与排查。 */
@Data
public class CampaignFailForm {
  @Size(max = 200)
  private String reason;
}
