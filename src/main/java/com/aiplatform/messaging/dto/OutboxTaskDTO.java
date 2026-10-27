package com.aiplatform.messaging.dto;

import com.aiplatform.messaging.entity.BusinessOutboxTask;
import java.time.LocalDateTime;
import lombok.Data;

/** 管理复核视图；不提供业务载荷。 */
@Data
public class OutboxTaskDTO {
  private Long id;
  private String kind;
  private Long aggregateId;
  private Long userId;
  private String state;
  private Integer attempts;
  private Integer consumeAttempts;
  private LocalDateTime nextAttemptAt;
  private LocalDateTime deliverAt;
  private String leaseOwner;
  private LocalDateTime leaseUntil;
  private LocalDateTime sentAt;
  private LocalDateTime consumedAt;
  private String lastError;
  private LocalDateTime createdAt;

  public static OutboxTaskDTO from(BusinessOutboxTask task) {
    OutboxTaskDTO dto = new OutboxTaskDTO();
    dto.setId(task.getId());
    dto.setKind(task.getKind());
    dto.setAggregateId(task.getAggregateId());
    dto.setUserId(task.getUserId());
    dto.setState(task.getState());
    dto.setAttempts(task.getAttempts());
    dto.setConsumeAttempts(task.getConsumeAttempts());
    dto.setNextAttemptAt(task.getNextAttemptAt());
    dto.setDeliverAt(task.getDeliverAt());
    dto.setLeaseOwner(task.getLeaseOwner());
    dto.setLeaseUntil(task.getLeaseUntil());
    dto.setSentAt(task.getSentAt());
    dto.setConsumedAt(task.getConsumedAt());
    dto.setLastError(task.getLastError());
    dto.setCreatedAt(task.getCreatedAt());
    return dto;
  }
}
