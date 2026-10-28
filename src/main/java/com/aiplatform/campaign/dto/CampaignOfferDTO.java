package com.aiplatform.campaign.dto;

import java.time.LocalDateTime;
import lombok.Data;

/** 公开活动视图只用于展示；购买资格仍由 Redis 脚本和数据库事实判定。 */
@Data
public class CampaignOfferDTO {
  private Long id;
  private Long productId;
  private Long applicationId;
  private String applicationName;
  private String productTitle;
  private String productSubtitle;
  private String productRules;
  private String modelVersion;
  private String imageSize;
  private Long price;
  private Integer credits;
  private Integer validDays;
  private Integer outputCount;
  private LocalDateTime beginAt;
  private LocalDateTime endAt;
  private Integer remainingStock;
  private String publishState;
  private String state;
  private boolean sellable;
  private String displayNotice;
}
