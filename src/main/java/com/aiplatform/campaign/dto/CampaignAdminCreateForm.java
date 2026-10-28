package com.aiplatform.campaign.dto;

import java.time.LocalDateTime;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
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
