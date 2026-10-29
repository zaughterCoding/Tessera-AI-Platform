package com.aiplatform.analytics.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Data;

/** 持久投递任务；与订单投影同事务登记，Kafka 确认后才标记已发送。 */
@Data
public class AnalyticsOutboxEvent {
  private String eventId;
  private Long applicationId;
  private Long userId;
  private String action;
  private Long occurredAtMs;
  private String payload;
  private String state;
  private Integer attempts;
  private LocalDateTime nextAttemptAt;
  private String leaseOwner;
  private LocalDateTime leaseUntil;
  private LocalDateTime sentAt;
  private String lastError;
  private LocalDateTime createdAt;

  public boolean matches(long expectedApplicationId, Long expectedUserId,
      String expectedAction, long expectedOccurredAtMs) {
    return Objects.equals(applicationId, expectedApplicationId)
        && Objects.equals(userId, expectedUserId)
        && Objects.equals(action, expectedAction)
        && Objects.equals(occurredAtMs, expectedOccurredAtMs);
  }
}
