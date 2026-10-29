package com.aiplatform.analytics.service.impl;

import com.aiplatform.analytics.AnalyticsEvent;
import com.aiplatform.analytics.AnalyticsEventCodec;
import com.aiplatform.analytics.AnalyticsMalformedException;
import com.aiplatform.analytics.AnalyticsProperties;
import com.aiplatform.analytics.entity.AnalyticsOutboxEvent;
import com.aiplatform.analytics.mapper.AnalyticsOutboxMapper;
import com.aiplatform.analytics.service.IAnalyticsRelayService;
import com.aiplatform.analytics.transport.AnalyticsTransport;
import com.aiplatform.messaging.FailureMessages;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 租约领取、确认发送与失败退避；Kafka 不可用时任务留在库中等待恢复。 */
@Slf4j
@Service
public class AnalyticsRelayServiceImpl implements IAnalyticsRelayService {
  private final AnalyticsProperties properties;
  private final AnalyticsOutboxMapper outbox;
  private final AnalyticsEventCodec codec;
  private final ObjectProvider<AnalyticsTransport> transport;
  private final MeterRegistry metrics;
  private final TransactionTemplate transaction;
  private final AtomicLong pending = new AtomicLong();

  public AnalyticsRelayServiceImpl(
      AnalyticsProperties properties,
      AnalyticsOutboxMapper outbox,
      AnalyticsEventCodec codec,
      ObjectProvider<AnalyticsTransport> transport,
      MeterRegistry metrics,
      PlatformTransactionManager transactionManager) {
    this.properties = properties;
    this.outbox = outbox;
    this.codec = codec;
    this.transport = transport;
    this.metrics = metrics;
    this.transaction = new TransactionTemplate(transactionManager);
    metrics.gauge("aiplatform.analytics.outbox.pending", pending);
  }

  @Override
  @Scheduled(fixedDelayString = "${app.analytics.poll-delay-ms:1000}")
  public void relayPending() {
    if (!properties.isEnabled() || !properties.isRelayEnabled()) {
      return;
    }
    AnalyticsTransport sender = transport.getIfAvailable();
    if (sender == null) {
      return;
    }
    try {
      pending.set(outbox.countPending());
      String owner = UUID.randomUUID().toString();
      List<AnalyticsOutboxEvent> claimed = transaction.execute(status -> claim(owner));
      if (claimed == null || claimed.isEmpty()) {
        return;
      }
      for (AnalyticsOutboxEvent event : claimed) {
        deliver(event, owner, sender);
      }
    } catch (RuntimeException failure) {
      log.warn("分析任务轮询失败，type={}", failure.getClass().getSimpleName());
    }
  }

  private List<AnalyticsOutboxEvent> claim(String owner) {
    List<AnalyticsOutboxEvent> candidates = outbox.selectClaimable(properties.getRelayBatch());
    List<AnalyticsOutboxEvent> claimed = new ArrayList<>();
    for (AnalyticsOutboxEvent candidate : candidates) {
      if (outbox.claim(candidate.getEventId(), owner, properties.getLeaseSeconds()) == 1) {
        claimed.add(candidate);
      }
    }
    return claimed;
  }

  private void deliver(AnalyticsOutboxEvent task, String owner, AnalyticsTransport sender) {
    String eventId = task.getEventId();
    if (outbox.renewLease(eventId, owner, properties.getLeaseSeconds()) != 1) {
      return;
    }
    AnalyticsEvent event;
    try {
      event = codec.decode(task.getPayload());
    } catch (AnalyticsMalformedException invalid) {
      outbox.reject(eventId, owner, FailureMessages.summarize(invalid));
      metrics.counter("aiplatform.analytics.outbox", "outcome", "review").increment();
      return;
    }
    int attempts = (task.getAttempts() == null ? 0 : task.getAttempts()) + 1;
    try {
      sender.publishAndAwait(event);
      if (outbox.markSent(eventId, owner) == 1) {
        metrics.counter("aiplatform.analytics.outbox", "outcome", "sent").increment();
      }
    } catch (RuntimeException failure) {
      long delay = retryDelaySeconds(attempts);
      outbox.recordFailure(eventId, owner, FailureMessages.summarize(failure), delay,
          properties.getMaxSendAttempts());
      String outcome = attempts >= properties.getMaxSendAttempts() ? "review" : "retry";
      metrics.counter("aiplatform.analytics.outbox", "outcome", outcome).increment();
      log.warn("分析任务发送失败，eventId={}，attempts={}，type={}",
          eventId, attempts, failure.getClass().getSimpleName());
    }
  }

  /** 指数退避并封顶；多次失败后进入隔离，等待人工重试或补数。 */
  private long retryDelaySeconds(int attempts) {
    long base = Math.max(1, properties.getRetryBackoffMs() / 1000);
    long factor = 1L << Math.min(Math.max(attempts, 0), 8);
    return Math.min(properties.getMaxRetrySeconds(), base * factor);
  }

  @Override
  public List<AnalyticsOutboxEvent> listTasks(String state, int limit, int offset) {
    return outbox.selectEvents(state, Math.max(1, Math.min(limit, 200)), Math.max(0, offset));
  }

  @Override
  public AnalyticsOutboxEvent getTask(String eventId) {
    return outbox.selectFact(eventId);
  }

  @Override
  public boolean retryReview(String eventId) {
    return outbox.retryReview(eventId) == 1;
  }

  @Override
  public long countPending() {
    return outbox.countPending();
  }
}
