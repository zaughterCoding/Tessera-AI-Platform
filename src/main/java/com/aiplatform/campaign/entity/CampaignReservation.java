package com.aiplatform.campaign.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 订单号在入口受理前生成，预约行先于订单存在，因此不设置订单外键。 */
@Data
@TableName("campaign_reservation")
public class CampaignReservation {
  @TableId(type = IdType.INPUT)
  private Long orderId;
  private Long campaignId;
  private Long userId;
  private Integer generation;
  private String requestKey;
  private String state;
  private Boolean stockReleased;
  private LocalDateTime releasedAt;
  private Integer attempts;
  private String lastError;
  private Long activeUser;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
