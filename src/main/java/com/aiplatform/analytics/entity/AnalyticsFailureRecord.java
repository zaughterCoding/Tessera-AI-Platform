package com.aiplatform.analytics.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 无法安全归属的消息按 Kafka 位点隔离；载荷只保留在本地表。 */
@Data
public class AnalyticsFailureRecord {
  private String topic;
  private Integer partitionId;
  private Long messageOffset;
  private String payload;
  private String reason;
  private LocalDateTime createdAt;
}
