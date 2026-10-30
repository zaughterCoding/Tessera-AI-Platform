package com.aiplatform.messaging.service.impl;

import com.aiplatform.messaging.BusinessEvent;
import com.aiplatform.messaging.DeliveryRetryPolicy;
import com.aiplatform.messaging.EventPublisher;
import com.aiplatform.messaging.FailureMessages;
import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import com.aiplatform.messaging.service.IOutboxRelayService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class OutboxRelayServiceImpl implements IOutboxRelayService {
  private final BusinessOutboxMapper outbox;
  private final ObjectProvider<EventPublisher> publisher;
  private final DeliveryRetryPolicy retryPolicy;
  private final MeterRegistry metrics;
  private final TransactionTemplate transaction;
  private final int batchSize;
  private final long leaseSeconds;
  private final long redriveSentSeconds;
  private final long redriveFailedSeconds;

  public OutboxRelayServiceImpl(
      BusinessOutboxMapper outbox,
      ObjectProvider<EventPublisher> publisher,
      DeliveryRetryPolicy retryPolicy,
      MeterRegistry metrics,
      PlatformTransactionManager transactionManager,
      @Value("${app.messaging.batch-size:50}") int batchSize,
      @Value("${app.messaging.lease-seconds:30}") long leaseSeconds,
      @Value("${app.messaging.redrive-sent-seconds:30}") long redriveSentSeconds,
      @Value("${app.messaging.redrive-failed-seconds:300}") long redriveFailedSeconds) {
    this.outbox = outbox;
    this.publisher = publisher;
    this.retryPolicy = retryPolicy;
    this.metrics = metrics;
    this.transaction = new TransactionTemplate(transactionManager);
    this.batchSize = Math.max(1, Math.min(batchSize, 200));
    this.leaseSeconds = Math.max(1, leaseSeconds);
    this.redriveSentSeconds = Math.max(0, redriveSentSeconds);
    this.redriveFailedSeconds = Math.max(0, redriveFailedSeconds);
  }

  @Override
  public void relayOutboxBatch() {
    String owner = UUID.randomUUID().toString();
    List<BusinessOutboxTask> claimed = transaction.execute(status -> claim(owner));
    if (claimed == null || claimed.isEmpty()) {
      return;
    }
    for (BusinessOutboxTask task : claimed) {
      deliver(task, owner);
    }
  }

  private List<BusinessOutboxTask> claim(String owner) {
    List<BusinessOutboxTask> candidates = outbox.selectClaimableEvents(batchSize);
    List<BusinessOutboxTask> claimed = new ArrayList<>();
    for (BusinessOutboxTask candidate : candidates) {
      if (outbox.claimEvent(candidate.getId(), owner, leaseSeconds) == 1) {
        claimed.add(candidate);
      }
    }
    return claimed;
  }

  private void deliver(BusinessOutboxTask task, String owner) {
    if (outbox.renewLease(task.getId(), owner, leaseSeconds) != 1) {
      return;
    }
    BusinessEvent event = BusinessEvent.from(task);
    if (event.getDeliverAt() != null && event.getDeliverAt() > System.currentTimeMillis()) {
      outbox.deferDeadline(task.getId(), owner);
      metrics.counter("aiplatform.messaging.outbox", "outcome", "delayed").increment();
      return;
    }
    try {
      EventPublisher transport = publisher.getIfAvailable();
      if (transport == null) {
        throw new IllegalStateException("没有可用的消息传输");
      }
      transport.publish(event);
      outbox.markSent(task.getId(), owner);
      metrics.counter("aiplatform.messaging.outbox", "outcome", "sent").increment();
    } catch (RuntimeException failure) {
      long delay = retryPolicy.delaySeconds(value(task.getAttempts()) + 1);
      outbox.recordSendFailure(task.getId(), owner, FailureMessages.summarize(failure),
          delay, retryPolicy.maxSendAttempts());
      metrics.counter("aiplatform.messaging.outbox", "outcome", "retry").increment();
      log.warn("任务投递失败，eventId={}，type={}",
          task.getId(), failure.getClass().getSimpleName());
    }
  }

  @Override
  public void redriveUnconsumedEvents() {
    transaction.executeWithoutResult(status -> {
      outbox.redriveSentEvents(redriveSentSeconds);
      outbox.redriveFailedEvents(redriveFailedSeconds);
    });
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public List<BusinessOutboxTask> listTasks(String state, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, offset);
    return outbox.selectEvents(state, safeLimit, safeOffset);
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public BusinessOutboxTask getTask(long id) {
    return outbox.selectEvent(id);
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public boolean retryReview(long id) {
    return outbox.retryReview(id) == 1;
  }

  @Override
  public long countByState(String state) {
    return outbox.countByState(state);
  }

  private int value(Integer attempts) {
    return attempts == null ? 0 : attempts;
  }
}
