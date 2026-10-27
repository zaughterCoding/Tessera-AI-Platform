package com.aiplatform.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 稳定商品元数据；可售状态与活动库存不进入该缓存对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageProductMeta {
  private Long id;
  private Long applicationId;
  private String title;
  private String subtitle;
  private String rules;
  private Long price;
  private Integer credits;
  private Integer validDays;
  private Integer saleMode;
  private Integer applicationVersion;
  private String modelVersion;
  private String imageSize;
  private Integer outputCount;
}
