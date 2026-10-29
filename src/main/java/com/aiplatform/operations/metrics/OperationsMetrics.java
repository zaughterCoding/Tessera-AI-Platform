package com.aiplatform.operations.metrics;

import com.aiplatform.analytics.mapper.AnalyticsFailureMapper;
import com.aiplatform.media.mapper.MediaAssetMapper;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import com.aiplatform.messaging.mapper.MessageMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 采集可靠任务与失败隔离的真实积压；采集失败时保留上次值并暴露失败标记，不写成 0。
 */
@Slf4j
@Component
public class OperationsMetrics {
  private final BusinessOutboxMapper outbox;
  private final MessageMapper messages;
  private final AnalyticsFailureMapper analyticsFailures;
  private final MediaAssetMapper media;
  private final AtomicLong pending = new AtomicLong(-1);
  private final AtomicLong sending = new AtomicLong(-1);
  private final AtomicLong sent = new AtomicLong(-1);
  private final AtomicLong review = new AtomicLong(-1);
  private final AtomicLong oldestPendingSeconds = new AtomicLong(-1);
  private final AtomicLong messageFailures = new AtomicLong(-1);
  private final AtomicLong analyticsFailureCount = new AtomicLong(-1);
  private final AtomicLong mediaPending = new AtomicLong(-1);
  private final AtomicLong mediaDeleting = new AtomicLong(-1);
  private final AtomicLong collectionFailed = new AtomicLong(0);

  public OperationsMetrics(
      BusinessOutboxMapper outbox,
      MessageMapper messages,
      AnalyticsFailureMapper analyticsFailures,
      MediaAssetMapper media,
      MeterRegistry metrics) {
    this.outbox = outbox;
    this.messages = messages;
    this.analyticsFailures = analyticsFailures;
    this.media = media;
    gauge(metrics, "aiplatform.outbox.backlog", pending, "state", "PENDING");
    gauge(metrics, "aiplatform.outbox.backlog", sending, "state", "SENDING");
    gauge(metrics, "aiplatform.outbox.backlog", sent, "state", "SENT");
    gauge(metrics, "aiplatform.outbox.backlog", review, "state", "REVIEW");
    gauge(metrics, "aiplatform.outbox.oldest.pending.age.seconds", oldestPendingSeconds);
    gauge(metrics, "aiplatform.message.failures", messageFailures);
    gauge(metrics, "aiplatform.analytics.failures", analyticsFailureCount);
    gauge(metrics, "aiplatform.media.pending", mediaPending);
    gauge(metrics, "aiplatform.media.deleting", mediaDeleting);
    Gauge.builder("aiplatform.operations.metrics.collection.failed", collectionFailed,
        AtomicLong::doubleValue).register(metrics);
  }

  @Scheduled(fixedDelayString = "${app.operations.metrics-interval-ms:15000}",
      initialDelayString = "${app.operations.metrics-initial-delay-ms:5000}")
  public void collect() {
    try {
      pending.set(outbox.countByState("PENDING"));
      sending.set(outbox.countByState("SENDING"));
      sent.set(outbox.countByState("SENT"));
      review.set(outbox.countByState("REVIEW"));
      LocalDateTime oldest = outbox.oldestPendingReadyAt();
      oldestPendingSeconds.set(oldest == null ? 0 : Math.max(0,
          Duration.between(oldest, LocalDateTime.now(ZoneOffset.UTC)).getSeconds()));
      messageFailures.set(messages.countFailures());
      analyticsFailureCount.set(analyticsFailures.countFailures());
      mediaPending.set(media.countByState("PENDING"));
      mediaDeleting.set(media.countByState("DELETING"));
      collectionFailed.set(0);
    } catch (RuntimeException failure) {
      // 不覆盖上次已知值，失败标记由告警规则捕获。
      collectionFailed.set(1);
      log.warn("运维指标采集失败，type={}", failure.getClass().getSimpleName());
    }
  }

  private void gauge(MeterRegistry registry, String name, AtomicLong value,
      String... tags) {
    Gauge.Builder<AtomicLong> builder = Gauge.builder(name, value,
        holder -> holder.get() < 0 ? Double.NaN : holder.get());
    for (int index = 0; index + 1 < tags.length; index += 2) {
      builder.tag(tags[index], tags[index + 1]);
    }
    builder.register(registry);
  }
}
