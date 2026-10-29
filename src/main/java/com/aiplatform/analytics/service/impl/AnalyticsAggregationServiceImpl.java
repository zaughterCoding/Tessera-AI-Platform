package com.aiplatform.analytics.service.impl;

import com.aiplatform.analytics.AnalyticsAction;
import com.aiplatform.analytics.AnalyticsConflictException;
import com.aiplatform.analytics.AnalyticsEvent;
import com.aiplatform.analytics.AnalyticsProperties;
import com.aiplatform.analytics.entity.AnalyticsEventRecord;
import com.aiplatform.analytics.entity.AnalyticsOutboxEvent;
import com.aiplatform.analytics.mapper.AnalyticsEventMapper;
import com.aiplatform.analytics.mapper.AnalyticsOutboxMapper;
import com.aiplatform.analytics.service.IAnalyticsAggregationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 去重记录与计数更新同事务；事务失败时不提交位点，重放只会命中幂等分支。 */
@Service
public class AnalyticsAggregationServiceImpl implements IAnalyticsAggregationService {
  private final AnalyticsProperties properties;
  private final AnalyticsEventMapper events;
  private final AnalyticsOutboxMapper outbox;
  private final MeterRegistry metrics;

  public AnalyticsAggregationServiceImpl(
      AnalyticsProperties properties,
      AnalyticsEventMapper events,
      AnalyticsOutboxMapper outbox,
      MeterRegistry metrics) {
    this.properties = properties;
    this.events = events;
    this.outbox = outbox;
    this.metrics = metrics;
  }

  @Override
  @Transactional
  public void accept(AnalyticsEvent event) {
    long now = System.currentTimeMillis();
    event.validate(now, properties.retentionMs(), properties.maxSkewMs());
    String action = event.getAction().name();
    if (event.getAction().isOrderFact()) {
      verifyOrderFact(event, action);
    }
    long bucket = event.bucketMs();
    int inserted = events.insertEvent(event.getEventId(), event.getApplicationId(),
        event.getUserId(), action, event.getOccurredAt(), bucket);
    if (inserted == 0) {
      AnalyticsEventRecord existing = events.selectEventForUpdate(event.getEventId());
      if (existing == null || !existing.matches(event.getApplicationId(), event.getUserId(),
          action, event.getOccurredAt(), bucket)) {
        throw new AnalyticsConflictException("同一分析事件号对应不同内容");
      }
      metrics.counter("aiplatform.analytics.events", "action", action, "outcome", "duplicate")
          .increment();
      return;
    }
    events.upsertBucket(event.getApplicationId(), bucket, action);
    if (event.getAction() == AnalyticsAction.VISIT && event.getUserId() != null) {
      events.insertVisitUser(event.getApplicationId(), event.getUserId(), bucket);
    }
    metrics.counter("aiplatform.analytics.events", "action", action, "outcome", "accepted")
        .increment();
    metrics.timer("aiplatform.analytics.event.delay")
        .record(Math.max(0, now - event.getOccurredAt()), TimeUnit.MILLISECONDS);
  }

  /** 订单动作必须能对应到本地已登记的事实，伪造成交或领取不会进入计数。 */
  private void verifyOrderFact(AnalyticsEvent event, String action) {
    AnalyticsOutboxEvent fact = outbox.selectFact(event.getEventId());
    if (fact == null || !fact.matches(event.getApplicationId(), event.getUserId(), action,
        event.getOccurredAt())) {
      throw new AnalyticsConflictException("订单分析事实未登记或内容不一致");
    }
  }
}
