package com.aiplatform.analytics.dto;

import com.aiplatform.analytics.entity.AnalyticsOutboxEvent;
import java.time.LocalDateTime;
import lombok.Data;

/** 分析任务复核视图；不提供内部消息载荷。 */
@Data
public class AnalyticsOutboxDTO {
  private String eventId;
  private Long applicationId;
  private Long userId;
  private String action;
  private String state;
  private Integer attempts;
  private LocalDateTime nextAttemptAt;
  private LocalDateTime sentAt;
  private String lastError;
  private LocalDateTime createdAt;

  public static AnalyticsOutboxDTO from(AnalyticsOutboxEvent event) {
    AnalyticsOutboxDTO dto = new AnalyticsOutboxDTO();
    dto.setEventId(event.getEventId());
    dto.setApplicationId(event.getApplicationId());
    dto.setUserId(event.getUserId());
    dto.setAction(event.getAction());
    dto.setState(event.getState());
    dto.setAttempts(event.getAttempts());
    dto.setNextAttemptAt(event.getNextAttemptAt());
    dto.setSentAt(event.getSentAt());
    dto.setLastError(event.getLastError());
    dto.setCreatedAt(event.getCreatedAt());
    return dto;
  }
}
