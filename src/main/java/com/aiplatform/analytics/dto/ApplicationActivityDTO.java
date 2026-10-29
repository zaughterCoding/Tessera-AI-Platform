package com.aiplatform.analytics.dto;

import lombok.Data;

/** 单应用窗口视图；窗口边界为左闭右开，时间使用 UTC。 */
@Data
public class ApplicationActivityDTO {
  private Long applicationId;
  private String applicationName;
  private int windowMinutes;
  private String windowFrom;
  private String windowTo;
  private long visits;
  private long uv;
  private long orders;
  private long payments;
  private long claims;
  private long closedOrders;
  private long heat;
}
