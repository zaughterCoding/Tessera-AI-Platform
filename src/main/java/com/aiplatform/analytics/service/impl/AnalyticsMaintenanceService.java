package com.aiplatform.analytics.service.impl;

import com.aiplatform.analytics.AnalyticsProperties;
import com.aiplatform.analytics.mapper.AnalyticsEventMapper;
import com.aiplatform.analytics.mapper.AnalyticsOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 只清理分析自身的过期数据；失败记录与待发任务保留给恢复和复核。 */
@Slf4j
@Service
public class AnalyticsMaintenanceService {
  private static final int BATCH = 5000;

  private final AnalyticsProperties properties;
  private final AnalyticsEventMapper events;
  private final AnalyticsOutboxMapper outbox;
  private final TransactionTemplate transaction;

  public AnalyticsMaintenanceService(
      AnalyticsProperties properties,
      AnalyticsEventMapper events,
      AnalyticsOutboxMapper outbox,
      PlatformTransactionManager transactionManager) {
    this.properties = properties;
    this.events = events;
    this.outbox = outbox;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /** 去重明细与分钟桶同时按保留期截断；更早的重放会被拒绝，不会重复计数。 */
  @Scheduled(fixedDelayString = "${app.analytics.cleanup-interval-ms:3600000}")
  public void cleanup() {
    if (!properties.isEnabled()) {
      return;
    }
    long cutoff = System.currentTimeMillis() - properties.retentionMs();
    try {
      int uv = transaction.execute(status -> events.deleteVisitUsersBefore(cutoff, BATCH));
      int buckets = transaction.execute(status -> events.deleteBucketsBefore(cutoff, BATCH));
      int detail = transaction.execute(status -> events.deleteEventsBefore(cutoff, BATCH));
      int sent = transaction.execute(status ->
          outbox.deleteSentBefore(properties.getRetentionDays(), BATCH));
      if ((uv + buckets + detail + sent) > 0) {
        log.info("分析保留期清理完成，uv={}，buckets={}，events={}，outbox={}",
            uv, buckets, detail, sent);
      }
    } catch (RuntimeException failure) {
      log.warn("分析保留期清理失败，type={}", failure.getClass().getSimpleName());
    }
  }
}
