package com.aiplatform.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 订单保存成交时快照；商品后续调价、下架或改名都不能改变这些字段。 */
@Data
@TableName("purchase_order")
public class PurchaseOrder {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private Long requestId;
  private Long campaignId;
  private Integer campaignGeneration;
  private Long productId;
  private Long applicationId;
  private String applicationName;
  private Integer applicationVersion;
  private String productTitle;
  private String productSubtitle;
  private String productRules;
  private String modelVersion;
  private String imageSize;
  private Integer outputCount;
  private Integer credits;
  private Integer validDays;
  private Integer saleMode;
  private String currency;
  private Long unitPrice;
  private Long amount;
  private String status;
  private String funding;
  private LocalDateTime expireAt;
  private LocalDateTime paidAt;
  private LocalDateTime closedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
