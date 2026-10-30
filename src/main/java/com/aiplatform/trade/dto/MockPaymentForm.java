package com.aiplatform.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 客户端声明的应付金额只用于一致性核对，实际金额来自订单快照。 */
@Data
public class MockPaymentForm {
  @NotNull
  @Min(0)
  private Long expectedAmount;
}
