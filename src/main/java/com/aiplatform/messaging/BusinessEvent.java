package com.aiplatform.messaging;

import com.aiplatform.messaging.entity.BusinessOutboxTask;
import lombok.Data;

/** 跨进程消息只携带定位任务的字段；消费端仍以数据库任务为事实来源。 */
@Data
public class BusinessEvent {
  public static final int SCHEMA_VERSION = 1;
  private int schemaVersion = SCHEMA_VERSION;
  private long eventId;
  private String kind;
  private long aggregateId;
  private Long userId;
  private long occurredAt;
  private Long deliverAt;

  public static BusinessEvent from(BusinessOutboxTask task) {
    BusinessEvent event = new BusinessEvent();
    event.setEventId(task.getId());
    event.setKind(task.getKind());
    event.setAggregateId(task.getAggregateId());
    event.setUserId(task.getUserId());
    event.setOccurredAt(task.getCreatedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
    if (task.getDeliverAt() != null) {
      event.setDeliverAt(task.getDeliverAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
    }
    return event;
  }
}
