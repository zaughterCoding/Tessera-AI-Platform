package com.aiplatform.analytics.service.impl;

import com.aiplatform.analytics.AnalyticsProperties;
import com.aiplatform.analytics.mapper.AnalyticsOutboxMapper;
import com.aiplatform.analytics.service.IAnalyticsRecoveryService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** 消息存储重建后，唤醒已确认但未被消费的事实；消费端按事件号幂等吸收重复。 */
@Slf4j
@Service
public class AnalyticsRecoveryServiceImpl implements IAnalyticsRecoveryService {
  private static final String REASON = "sent-unconsumed-recovery";

  private final AnalyticsProperties properties;
  private final AnalyticsOutboxMapper outbox;
  private final MeterRegistry metrics;

  public AnalyticsRecoveryServiceImpl(
      AnalyticsProperties properties,
      AnalyticsOutboxMapper outbox,
      MeterRegistry metrics) {
    this.properties = properties;
    this.outbox = outbox;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${app.analytics.sent-recovery-interval-ms:15000}",
      initialDelayString = "${app.analytics.sent-recovery-interval-ms:15000}")
  public void recoverOnSchedule() {
    recoverUnconsumed();
  }

  @Override
  public int recoverUnconsumed() {
    if (!properties.isEnabled() || !properties.isRelayEnabled()) {
      return 0;
    }
    try {
      int recovered = outbox.requeueUnconsumed(properties.getSentRecoverySeconds(),
          System.currentTimeMillis() - properties.retentionMs(),
          properties.getRelayBatch(), REASON);
      if (recovered > 0) {
        metrics.counter("aiplatform.analytics.outbox", "outcome", "recovered")
            .increment(recovered);
        log.info("分析任务恢复重发，count={}", recovered);
      }
      return recovered;
    } catch (RuntimeException failure) {
      log.warn("分析任务恢复轮询失败，type={}", failure.getClass().getSimpleName());
      return 0;
    }
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public int recoverAsAdmin() {
    return recoverUnconsumed();
  }
}
