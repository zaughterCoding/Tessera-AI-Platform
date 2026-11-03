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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class OutboxRelayServiceImpl implements IOutboxRelayService {
  private static final int MAX_BATCHES_PER_POLL = 10;
  private static final long NANOS_PER_SECOND = 1_000_000_000L;
  private final BusinessOutboxMapper outbox;
  private final ObjectProvider<EventPublisher> publisher;
  private final DeliveryRetryPolicy retryPolicy;
  private final MeterRegistry metrics;
  private final TransactionTemplate transaction;
  private final int batchSize;
  private final long leaseSeconds;
  private final long redriveSentSeconds;

  public OutboxRelayServiceImpl(
      BusinessOutboxMapper outbox,
      ObjectProvider<EventPublisher> publisher,
      DeliveryRetryPolicy retryPolicy,
      MeterRegistry metrics,
      PlatformTransactionManager transactionManager,
      @Value("${app.messaging.batch-size:50}") int batchSize,
      @Value("${app.messaging.lease-seconds:30}") long leaseSeconds,
      @Value("${app.messaging.redrive-sent-seconds:30}") long redriveSentSeconds) {
    this.outbox = outbox;
    this.publisher = publisher;
    this.retryPolicy = retryPolicy;
    this.metrics = metrics;
    this.transaction = new TransactionTemplate(transactionManager);
    // 认领只锁选中的行：读已提交没有间隙锁，不会挡住业务事务写入新事件。
    this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.batchSize = Math.max(1, Math.min(batchSize, 200));
    this.leaseSeconds = Math.max(1, leaseSeconds);
    this.redriveSentSeconds = Math.max(0, redriveSentSeconds);
  }

  @Override
  public void relayOutboxBatch() {
    relayBatch();
  }

  @Override
  public void relayDueBatches() {
    // 整批都满说明还有积压，继续投递不再等下一轮；限制批数以便重投扫描按时执行。
    for (int batch = 0; batch < MAX_BATCHES_PER_POLL; batch++) {
      if (relayBatch() < batchSize) {
        return;
      }
    }
  }

  private int relayBatch() {
    String owner = UUID.randomUUID().toString();
    List<BusinessOutboxTask> claimed = transaction.execute(status -> claim(owner));
    if (claimed == null || claimed.isEmpty()) {
      return 0;
    }
    long claimedAt = System.nanoTime();
    for (BusinessOutboxTask task : claimed) {
      deliver(task, owner, claimedAt);
    }
    return claimed.size();
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

  private void deliver(BusinessOutboxTask task, String owner, long claimedAt) {
    // 认领时已写入租约，过半才续：每次续约都是一次刷盘，逐条续会占掉一半投递时间。
    long heldSeconds = (System.nanoTime() - claimedAt) / NANOS_PER_SECOND;
    if (heldSeconds * 2 >= leaseSeconds
        && outbox.renewLease(task.getId(), owner, leaseSeconds) != 1) {
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
    for (long id : outbox.selectStaleSentIds(redriveSentSeconds, batchSize)) {
      outbox.redriveSentEvent(id, redriveSentSeconds);
    }
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
