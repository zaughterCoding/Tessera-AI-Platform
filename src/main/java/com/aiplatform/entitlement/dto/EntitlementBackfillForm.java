package com.aiplatform.entitlement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 补发按订单主键稳定推进；不接收用户、次数、规格或有效期参数。 */
@Data
public class EntitlementBackfillForm {
  /** 0 表示从头扫描；省略时与 0 等价，负数拒绝。 */
  @Min(0)
  private Long afterOrderId;

  @Min(1)
  @Max(200)
  private Integer limit;
}
