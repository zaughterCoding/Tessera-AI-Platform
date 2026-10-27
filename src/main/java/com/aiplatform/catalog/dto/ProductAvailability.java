package com.aiplatform.catalog.dto;

import lombok.Data;

/** 商品与应用的实时可售状态，仅用于当前请求判断。 */
@Data
public class ProductAvailability {
  private Long id;
  private Integer status;
  private Integer applicationStatus;
}
