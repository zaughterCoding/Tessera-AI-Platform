package com.aiplatform.messaging.service.impl;

import com.aiplatform.messaging.BusinessEvent;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.DeliveryRetryPolicy;
import com.aiplatform.messaging.FailureMessages;
import com.aiplatform.messaging.ProjectionResult;
import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.messaging.entity.MessageFailure;
import com.aiplatform.messaging.enums.OutboxState;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import com.aiplatform.messaging.mapper.MessageMapper;
import com.aiplatform.messaging.service.IBusinessEventService;
import com.aiplatform.messaging.service.IBusinessProjectionService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 先核对数据库任务，再按消费组去重；消息字段从不直接决定投影目标。 */
@Slf4j
@Service
public class BusinessEventServiceImpl implements IBusinessEventService {
  private final BusinessOutboxMapper outbox;
  private final MessageMapper messages;
  private final IBusinessProjectionService projection;
  private final DeliveryRetryPolicy retryPolicy;
  private final MeterRegistry metrics;
  private final TransactionTemplate transaction;
  private final boolean brokerTransport;

  public BusinessEventServiceImpl(
      BusinessOutboxMapper outbox,
      MessageMapper messages,
      IBusinessProjectionService projection,
      DeliveryRetryPolicy retryPolicy,
      MeterRegistry metrics,
      PlatformTransactionManager transactionManager,
      @Value("${app.messaging.transport:local}") String transport) {
    this.outbox = outbox;
    this.messages = messages;
    this.projection = projection;
    this.retryPolicy = retryPolicy;
    this.metrics = metrics;
    this.transaction = new TransactionTemplate(transactionManager);
    this.brokerTransport = "rocketmq".equals(transport);
  }

  @Override
  public void consume(BusinessEvent event, String consumerGroup) {
    BusinessEventType type = validateEnvelope(event, consumerGroup);
    transaction.executeWithoutResult(status -> {
      BusinessOutboxTask task = outbox.selectEventForUpdate(event.getEventId());
      verifyFields(task, event, type);
      boolean inboxDone = messages.countInboxEvent(consumerGroup, event.getEventId()) > 0;
      if (OutboxState.DONE.matches(task.getState()) || inboxDone) {
        if (!OutboxState.DONE.matches(task.getState())) {
          outbox.markDuplicateDone(task.getId());
        }
        metrics.counter("aiplatform.messaging.events",
            "consumer", consumerGroup, "outcome", "duplicate").increment();
        return;
      }
      if (OutboxState.REVIEW.matches(task.getState())) {
        throw new IllegalStateException("任务已隔离，等待人工复核");
      }
      ProjectionResult result = projection.project(task);
      messages.insertInboxEvent(consumerGroup, event.getEventId());
      if (result.isContinuation()) {
        // 续传任务与当前任务的完成状态同事务提交；任一步失败都从原游标重放。
        outbox.insertFeedContinuation(task.getAggregateId(), task.getUserId(), result.cursor());
      }
      outbox.markConsumed(task.getId(), brokerTransport);
      metrics.counter("aiplatform.messaging.events",
          "consumer", consumerGroup, "outcome", "consumed").increment();
    });
  }

  @Override
  @Transactional
  public boolean recordConsumptionFailure(
      BusinessEvent event,
      String consumerGroup,
      String brokerMessageId,
      String payload,
      Throwable failure,
      int deliveries) {
    String reason = FailureMessages.summarize(failure);
    BusinessOutboxTask task;
    try {
      BusinessEventType type = validateEnvelope(event, consumerGroup);
      task = outbox.selectEvent(event.getEventId());
      verifyFields(task, event, type);
    } catch (RuntimeException invalid) {
      // 无法归因的消息按 Broker 身份隔离，绝不修改任何真实任务。
      storeBrokerFailure(consumerGroup, brokerMessageId, payload, reason);
      metrics.counter("aiplatform.messaging.events",
          "consumer", consumerGroup, "outcome", "forged").increment();
      return true;
    }
    if (OutboxState.DONE.matches(task.getState())
        || OutboxState.REVIEW.matches(task.getState())) {
      return true;
    }
    if (brokerMessageId == null || brokerMessageId.isEmpty()) {
      brokerMessageId = "local:" + event.getEventId();
    }
    if (outbox.recordConsumeFailure(task.getId(), reason) == 0) {
      return true;
    }
    int effective = Math.max(deliveries, value(task.getConsumeAttempts()) + 1);
    if (effective < retryPolicy.maxConsumeAttempts()) {
      metrics.counter("aiplatform.messaging.events",
          "consumer", consumerGroup, "outcome", "retry").increment();
      return false;
    }
    storeOutboxFailure(consumerGroup, task.getId(), brokerMessageId, payload, reason);
    outbox.markReview(task.getId());
    metrics.counter("aiplatform.messaging.events",
        "consumer", consumerGroup, "outcome", "quarantined").increment();
    log.warn("任务超过消费重试上限并隔离，eventId={}", task.getId());
    return true;
  }

  @Override
  public List<MessageFailure> listFailures(String consumerGroup, int limit, int offset) {
    return messages.listFailures(consumerGroup,
        Math.max(1, Math.min(limit, 200)), Math.max(0, offset));
  }

  private BusinessEventType validateEnvelope(BusinessEvent event, String consumerGroup) {
    if (event == null || event.getSchemaVersion() != BusinessEvent.SCHEMA_VERSION) {
      throw new IllegalArgumentException("消息版本不可信");
    }
    if (event.getEventId() <= 0 || event.getKind() == null || event.getKind().isEmpty()) {
      throw new IllegalArgumentException("消息标识不可信");
    }
    BusinessEventType type = BusinessEventType.from(event.getKind());
    if (consumerGroup == null || !type.consumerGroup().equals(consumerGroup)) {
      throw new IllegalArgumentException("消费组与事件类型不匹配");
    }
    return type;
  }

  private void verifyFields(
      BusinessOutboxTask task, BusinessEvent event, BusinessEventType type) {
    if (task == null) {
      throw new IllegalArgumentException("任务不存在");
    }
    if (!type.name().equals(task.getKind())) {
      throw new IllegalArgumentException("事件类型与任务不一致");
    }
    if (task.getAggregateId() == null || task.getAggregateId() != event.getAggregateId()) {
      throw new IllegalArgumentException("聚合身份与任务不一致");
    }
    if (!Objects.equals(task.getUserId(), event.getUserId())) {
      throw new IllegalArgumentException("用户身份与任务不一致");
    }
    long occurredAt = task.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
    if (occurredAt != event.getOccurredAt()) {
      throw new IllegalArgumentException("发生时间与任务不一致");
    }
    Long deliverAt = task.getDeliverAt() == null ? null
        : task.getDeliverAt().toInstant(ZoneOffset.UTC).toEpochMilli();
    if (!Objects.equals(deliverAt, event.getDeliverAt())) {
      throw new IllegalArgumentException("投递时间与任务不一致");
    }
  }

  private void storeBrokerFailure(
      String consumerGroup, String brokerMessageId, String payload, String reason) {
    String identity = brokerMessageId == null || brokerMessageId.isEmpty()
        ? "unknown-" + System.nanoTime() : brokerMessageId;
    String key = FailureMessages.truncate("broker:" + identity, 128);
    messages.insertFailure(consumerGroup, key, null,
        FailureMessages.truncate(identity, 128),
        FailureMessages.payload(payload), reason);
  }

  private void storeOutboxFailure(
      String consumerGroup, long eventId, String brokerMessageId, String payload, String reason) {
    messages.insertFailure(consumerGroup, "outbox:" + eventId, eventId,
        FailureMessages.truncate(brokerMessageId, 128),
        FailureMessages.payload(payload), reason);
  }

  private int value(Integer attempts) {
    return attempts == null ? 0 : attempts;
  }
}
