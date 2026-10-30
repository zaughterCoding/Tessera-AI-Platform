package com.aiplatform.analytics.service.impl;

import com.aiplatform.analytics.AnalyticsAction;
import com.aiplatform.analytics.AnalyticsConflictException;
import com.aiplatform.analytics.AnalyticsEvent;
import com.aiplatform.analytics.AnalyticsEventCodec;
import com.aiplatform.analytics.AnalyticsMalformedException;
import com.aiplatform.analytics.AnalyticsProperties;
import com.aiplatform.analytics.entity.AnalyticsBackfillEvent;
import com.aiplatform.analytics.entity.AnalyticsBackfillState;
import com.aiplatform.analytics.entity.AnalyticsOutboxEvent;
import com.aiplatform.analytics.mapper.AnalyticsBackfillMapper;
import com.aiplatform.analytics.mapper.AnalyticsFailureMapper;
import com.aiplatform.analytics.mapper.AnalyticsOutboxMapper;
import com.aiplatform.analytics.service.IAnalyticsBackfillService;
import com.aiplatform.messaging.FailureMessages;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.ZoneOffset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 从订单事件历史补数；事件号沿用业务任务号，实时与补数天然收敛。 */
@Slf4j
@Service
public class AnalyticsBackfillServiceImpl implements IAnalyticsBackfillService {
  private static final int STATE_ID = 1;
  private static final String BACKFILL_TOPIC = "backfill";

  private final AnalyticsProperties properties;
  private final AnalyticsBackfillMapper backfill;
  private final AnalyticsOutboxMapper outbox;
  private final AnalyticsFailureMapper failures;
  private final AnalyticsEventCodec codec;
  private final MeterRegistry metrics;
  private final TransactionTemplate transaction;

  public AnalyticsBackfillServiceImpl(
      AnalyticsProperties properties,
      AnalyticsBackfillMapper backfill,
      AnalyticsOutboxMapper outbox,
      AnalyticsFailureMapper failures,
      AnalyticsEventCodec codec,
      MeterRegistry metrics,
      PlatformTransactionManager transactionManager) {
    this.properties = properties;
    this.backfill = backfill;
    this.outbox = outbox;
    this.failures = failures;
    this.codec = codec;
    this.metrics = metrics;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /** 首次启动全量补数；中断后按已提交游标续跑；补数完成后每次启动只追新增。 */
  @Override
  @EventListener(ContextRefreshedEvent.class)
  public void startOnBoot() {
    if (!properties.isEnabled() || !properties.isBackfillEnabled()) {
      return;
    }
    try {
      transaction.executeWithoutResult(status -> {
        AnalyticsBackfillState state = backfill.selectStateForUpdate(STATE_ID);
        if (state == null || "RUNNING".equals(state.getState())
            || "FAILED".equals(state.getState())) {
          return;
        }
        long cursor = "DONE".equals(state.getState()) ? state.getCursorEventId() : 0L;
        long rangeFrom = System.currentTimeMillis() - properties.backfillRangeMs();
        backfill.start(STATE_ID, cursor, rangeFrom);
      });
    } catch (RuntimeException failure) {
      log.warn("分析历史补数启动失败，type={}", failure.getClass().getSimpleName());
    }
  }

  @Override
  @Scheduled(fixedDelayString = "${app.analytics.backfill-interval-ms:500}")
  public void runBatch() {
    if (!properties.isEnabled() || !properties.isBackfillEnabled()) {
      return;
    }
    try {
      transaction.execute(status -> processBatch());
    } catch (RuntimeException failure) {
      markFailed(FailureMessages.summarize(failure));
      metrics.counter("aiplatform.analytics.backfill", "outcome", "failed").increment();
      log.warn("分析历史补数批次失败，type={}", failure.getClass().getSimpleName());
    }
  }

  /** 批次写入与游标推进同事务；失败整体回滚，重启后从原游标重试。 */
  private boolean processBatch() {
    AnalyticsBackfillState state = backfill.selectStateForUpdate(STATE_ID);
    if (state == null || !"RUNNING".equals(state.getState())) {
      return false;
    }
    long rangeFromSeconds = state.getRangeFromMs() / 1000;
    List<AnalyticsBackfillEvent> batch = backfill.selectEvents(state.getCursorEventId(),
        rangeFromSeconds, properties.getBackfillBatch());
    long cursor = state.getCursorEventId();
    for (AnalyticsBackfillEvent row : batch) {
      cursor = row.getTaskId();
      register(row);
    }
    if (batch.size() < properties.getBackfillBatch()) {
      backfill.complete(STATE_ID, cursor, batch.size());
      metrics.counter("aiplatform.analytics.backfill", "outcome", "done").increment();
      log.info("分析历史补数完成，processed={}", state.getProcessed());
      return false;
    }
    backfill.advance(STATE_ID, cursor, batch.size());
    metrics.counter("aiplatform.analytics.backfill", "outcome", "batch").increment();
    return true;
  }

  private void register(AnalyticsBackfillEvent row) {
    String eventId = "order:" + row.getTaskId();
    try {
      AnalyticsAction action = AnalyticsAction.fromOrderKind(row.getEventType());
      if (action == null || row.getApplicationId() == null || row.getUserId() == null
          || row.getOccurredAt() == null) {
        throw new AnalyticsMalformedException("订单历史缺少可分析字段");
      }
      long occurredAt = row.getOccurredAt().toInstant(ZoneOffset.UTC).toEpochMilli();
      AnalyticsEvent event = AnalyticsEvent.order(eventId, row.getApplicationId(),
          row.getUserId(), action, occurredAt);
      event.validate(System.currentTimeMillis(), properties.retentionMs(), properties.maxSkewMs());
      String payload = codec.encode(event);
      int inserted = outbox.insertIfAbsent(eventId, row.getApplicationId(), row.getUserId(),
          action.name(), occurredAt, payload);
      if (inserted == 0) {
        AnalyticsOutboxEvent existing = outbox.selectFactForUpdate(eventId);
        if (existing == null || !existing.matches(row.getApplicationId(), row.getUserId(),
            action.name(), occurredAt)) {
          throw new AnalyticsConflictException("补数事件与实时登记内容不一致");
        }
      }
    } catch (AnalyticsMalformedException | AnalyticsConflictException invalid) {
      // 异常历史不阻断整批恢复，按业务任务号隔离等待人工复核。
      failures.insertFailure(BACKFILL_TOPIC, 0, row.getTaskId(),
          FailureMessages.truncate(eventId, 64), FailureMessages.summarize(invalid));
    }
  }

  private void markFailed(String reason) {
    try {
      transaction.executeWithoutResult(status -> backfill.markFailed(STATE_ID, reason));
    } catch (RuntimeException failure) {
      log.warn("分析补数失败状态记录失败，type={}", failure.getClass().getSimpleName());
    }
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public AnalyticsBackfillState state() {
    return backfill.selectState(STATE_ID);
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public boolean restart() {
    if (!properties.isEnabled()) {
      return false;
    }
    long rangeFrom = System.currentTimeMillis() - properties.backfillRangeMs();
    return backfill.restart(STATE_ID, rangeFrom) == 1;
  }
}
