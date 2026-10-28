package com.aiplatform.campaign.dto;

import java.time.LocalDateTime;
import lombok.Data;

/** 管理视图包含发布状态和库存代次，便于暂停、恢复与对账。 */
@Data
public class AdminCampaignDTO {
  private Long id;
  private Long productId;
  private Long applicationId;
  private String applicationName;
  private String productTitle;
  private Long price;
  private Integer credits;
  private Integer validDays;
  private Integer initialStock;
  private Integer remainingStock;
  private LocalDateTime beginAt;
  private LocalDateTime endAt;
  private String publishState;
  private Integer generation;
  private LocalDateTime publishedAt;
  private LocalDateTime createdAt;
  private String state;
}
