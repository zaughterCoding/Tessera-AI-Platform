package com.aiplatform.catalog.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UsageProductDTO {
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
  /** 由实时数据库状态计算，不读取可能过期的缓存状态。 */
  private boolean orderable;
}
