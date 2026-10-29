package com.aiplatform.analytics.entity;

import lombok.Data;

/** 单应用窗口计数；窗口 UV 由整段窗口去重得到。 */
@Data
public class AnalyticsOverviewRow {
  private Long applicationId;
  private String name;
  private Long visits;
  private Long uv;
  private Long orders;
  private Long payments;
  private Long claims;
  private Long closedOrders;
}
