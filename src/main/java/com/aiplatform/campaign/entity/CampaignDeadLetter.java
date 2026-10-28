package com.aiplatform.campaign.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 只保存无法自动处理的 Stream 消息；不参与自动库存释放。 */
@Data
@TableName("campaign_dead_letter")
public class CampaignDeadLetter {
  @TableId(type = IdType.INPUT)
  private String messageId;
  private Long campaignId;
  private Long orderId;
  private String payload;
  private String reason;
  private LocalDateTime createdAt;
}
