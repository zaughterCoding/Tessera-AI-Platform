package com.aiplatform.campaign.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import lombok.Data;

/** 管理端创建活动只接受商品、库存和时间窗，状态、代次与归属由服务端确定。 */
@Data
public class CampaignAdminCreateForm {
  @NotNull
  @Positive
  private Long productId;

  @NotNull
  @Min(1)
  @Max(1000000)
  private Integer initialStock;

  @NotNull
  private LocalDateTime beginAt;

  @NotNull
  private LocalDateTime endAt;
}
