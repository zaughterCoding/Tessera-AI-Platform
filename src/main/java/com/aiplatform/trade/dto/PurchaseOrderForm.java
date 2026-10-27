package com.aiplatform.trade.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import lombok.Data;

/** 购买请求只接受商品与请求键；价格和归属一律由服务端可信数据确定。 */
@Data
public class PurchaseOrderForm {
  @NotNull
  @Positive
  private Long productId;

  @NotBlank
  @Pattern(regexp = "[A-Za-z0-9_-]{8,64}")
  private String requestKey;
}
