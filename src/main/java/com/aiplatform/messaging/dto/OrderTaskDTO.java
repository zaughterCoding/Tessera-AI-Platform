package com.aiplatform.messaging.dto;

import java.time.LocalDateTime;
import lombok.Data;

/** 技术体验页只读展示订单关联的可靠任务状态，不暴露消息载荷。 */
@Data
public class OrderTaskDTO {
  private Long id;
  private String kind;
  private String state;
  private Integer attempts;
  private Integer consumeAttempts;
  private LocalDateTime createdAt;
  private LocalDateTime sentAt;
  private LocalDateTime consumedAt;
}
