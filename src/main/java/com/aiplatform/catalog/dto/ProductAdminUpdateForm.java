package com.aiplatform.catalog.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import lombok.Data;

/** 商品管理写白名单：归属、版本与统计字段不可由请求改写。 */
@Data
public class ProductAdminUpdateForm {
  @Size(max = 128)
  private String title;

  @Size(max = 255)
  private String subtitle;

  @Size(max = 1024)
  private String rules;

  @Min(0)
  private Long price;

  @Min(1)
  private Integer credits;

  @Min(1)
  private Integer validDays;

  @Min(0)
  @Max(1)
  private Integer saleMode;

  @Size(max = 32)
  private String imageSize;

  @Min(1)
  private Integer outputCount;

  @Min(1)
  @Max(2)
  private Integer status;
}
