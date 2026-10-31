package com.aiplatform.entitlement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 补发按订单主键稳定推进；不接收用户、次数、规格或有效期参数。 */
@Data
public class EntitlementBackfillForm {
  @Positive
  private Long afterOrderId;

  @Min(1)
  @Max(200)
  private Integer limit;
}
