package com.aiplatform.campaign.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 活动库存只在订单建成时扣减；Redis 库存是入口前置量，不是最终事实。 */
@Data
@TableName("limited_campaign")
public class LimitedCampaign {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long productId;
  private Integer initialStock;
  private Integer remainingStock;
  private LocalDateTime beginAt;
  private LocalDateTime endAt;
  private String publishState;
  private Integer generation;
  private LocalDateTime publishedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
