package com.aiplatform.analytics.service.impl;

import com.aiplatform.analytics.AnalyticsAction;
import com.aiplatform.analytics.AnalyticsConflictException;
import com.aiplatform.analytics.AnalyticsEvent;
import com.aiplatform.analytics.AnalyticsEventCodec;
import com.aiplatform.analytics.AnalyticsProperties;
import com.aiplatform.analytics.entity.AnalyticsOutboxEvent;
import com.aiplatform.analytics.mapper.AnalyticsOutboxMapper;
import com.aiplatform.analytics.service.IAnalyticsJournalService;
import com.aiplatform.analytics.transport.AnalyticsTransport;
import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.trade.entity.PurchaseOrder;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 订单事实与业务投影同事务登记；浏览事件只做有界尽力发送。 */
@Slf4j
@Service
public class AnalyticsJournalServiceImpl implements IAnalyticsJournalService {
  private final AnalyticsProperties properties;
  private final AnalyticsOutboxMapper outbox;
  private final AnalyticsEventCodec codec;
  private final ObjectProvider<AnalyticsTransport> transport;
  private final MeterRegistry metrics;

  public AnalyticsJournalServiceImpl(
      AnalyticsProperties properties,
      AnalyticsOutboxMapper outbox,
      AnalyticsEventCodec codec,
      ObjectProvider<AnalyticsTransport> transport,
      MeterRegistry metrics) {
    this.properties = properties;
    this.outbox = outbox;
    this.codec = codec;
    this.transport = transport;
    this.metrics = metrics;
  }

  @Override
  public void recordVisit(long applicationId, Long userId) {
    if (!properties.isEnabled()) {
      return;
    }
    try {
      AnalyticsTransport sender = transport.getIfAvailable();
      if (sender == null) {
        dropVisit();
        return;
      }
      // 匿名访问只累计访问量；登录身份只来自服务端会话，不读取客户端声明。
      AnalyticsEvent event = AnalyticsEvent.visit(UUID.randomUUID().toString(),
          applicationId, userId, System.currentTimeMillis());
      sender.submitVisit(event);
    } catch (RuntimeException failure) {
      // 采集链路的任何异常都不能影响目录读取。
      dropVisit();
      log.debug("浏览事件提交失败，type={}", failure.getClass().getSimpleName());
    }
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void appendOrderFact(PurchaseOrder order, BusinessOutboxTask task) {
    if (!properties.isEnabled()) {
      return;
    }
    AnalyticsAction action = AnalyticsAction.fromOrderKind(task.getKind());
    if (action == null || order.getApplicationId() == null || order.getUserId() == null) {
      throw new IllegalArgumentException("订单事件不包含可分析的应用或用户身份");
    }
    if (task.getCreatedAt() == null) {
      throw new IllegalArgumentException("订单事件缺少发生时间");
    }
    long occurredAt = task.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
    String eventId = "order:" + task.getId();
    String payload = codec.encode(AnalyticsEvent.order(eventId, order.getApplicationId(),
        order.getUserId(), action, occurredAt));
    int inserted = outbox.insertIfAbsent(eventId, order.getApplicationId(), order.getUserId(),
        action.name(), occurredAt, payload);
    if (inserted == 0) {
      AnalyticsOutboxEvent existing = outbox.selectFactForUpdate(eventId);
      if (existing == null || !existing.matches(order.getApplicationId(), order.getUserId(),
          action.name(), occurredAt)) {
        throw new AnalyticsConflictException("同一分析事件号对应不同内容");
      }
    }
  }

  private void dropVisit() {
    metrics.counter("aiplatform.analytics.visits", "result", "dropped").increment();
  }
}
