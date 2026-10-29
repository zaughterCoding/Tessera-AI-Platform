package com.aiplatform.analytics.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 订单事件历史的补数来源；身份沿用业务任务号，与实时路径一致。 */
@Data
public class AnalyticsBackfillEvent {
  private Long taskId;
  private Long orderId;
  private String eventType;
  private LocalDateTime occurredAt;
  private Long applicationId;
  private Long userId;
}
