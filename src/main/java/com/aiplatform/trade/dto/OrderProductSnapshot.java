package com.aiplatform.trade.dto;

import lombok.Data;

/** 下单事务内读取的可售快照，包含可信价格与展示字段。 */
@Data
public class OrderProductSnapshot {
  private Long productId;
  private Long applicationId;
  private String applicationName;
  private Integer applicationVersion;
  private String productTitle;
  private String productSubtitle;
  private String productRules;
  private Long price;
  private Integer credits;
  private Integer validDays;
  private Integer saleMode;
  private String modelVersion;
  private String imageSize;
  private Integer outputCount;
  private Integer productStatus;
  private Integer applicationStatus;
}
