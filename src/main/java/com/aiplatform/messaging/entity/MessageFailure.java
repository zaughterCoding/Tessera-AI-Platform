package com.aiplatform.messaging.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 无法归属到持久任务或超过重试上限的消息，仅供人工复核。 */
@Data
public class MessageFailure {
  private String consumerGroup;
  private String eventKey;
  private Long eventId;
  private String brokerMessageId;
  private String payload;
  private String reason;
  private LocalDateTime createdAt;
}
