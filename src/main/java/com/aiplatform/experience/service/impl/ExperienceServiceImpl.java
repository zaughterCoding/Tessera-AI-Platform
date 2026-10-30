package com.aiplatform.experience.service.impl;

import com.aiplatform.cache.CacheReadTrace;
import com.aiplatform.cache.RequestCacheScope;
import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.mapper.CampaignReservationMapper;
import com.aiplatform.catalog.dto.ApplicationDetailDTO;
import com.aiplatform.catalog.service.IApplicationService;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.experience.dto.CacheTraceDTO;
import com.aiplatform.experience.dto.OrderProgressDTO;
import com.aiplatform.experience.service.IExperienceService;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import com.aiplatform.trade.dto.PurchaseOrderDTO;
import com.aiplatform.trade.service.ITradeOrderService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** 只读技术观察：展示真实配置、真实缓存层与真实可靠任务，不制造数据。 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class ExperienceServiceImpl implements IExperienceService {
  private final IApplicationService applications;
  private final ITradeOrderService orders;
  private final CampaignReservationMapper reservations;
  private final BusinessOutboxMapper outbox;
  private final MeterRegistry metrics;
  private final boolean enabled;
  private final boolean cacheEnabled;
  private final boolean memcacheEnabled;
  private final boolean analyticsEnabled;
  private final boolean cdcEnabled;
  private final String transport;

  public ExperienceServiceImpl(
      IApplicationService applications,
      ITradeOrderService orders,
      CampaignReservationMapper reservations,
      BusinessOutboxMapper outbox,
      MeterRegistry metrics,
      @Value("${app.experience.enabled:false}") boolean enabled,
      @Value("${app.cache.enabled:true}") boolean cacheEnabled,
      @Value("${app.cache.memcache.enabled:false}") boolean memcacheEnabled,
      @Value("${app.analytics.enabled:false}") boolean analyticsEnabled,
      @Value("${app.cdc.enabled:false}") boolean cdcEnabled,
      @Value("${app.messaging.transport:local}") String transport) {
    this.applications = applications;
    this.orders = orders;
    this.reservations = reservations;
    this.outbox = outbox;
    this.metrics = metrics;
    this.enabled = enabled;
    this.cacheEnabled = cacheEnabled;
    this.memcacheEnabled = memcacheEnabled;
    this.analyticsEnabled = analyticsEnabled;
    this.cdcEnabled = cdcEnabled;
    this.transport = transport;
  }

  @Override
  public Map<String, Object> capabilities() {
    requireEnabled();
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("enabled", true);
    view.put("cacheEnabled", cacheEnabled);
    view.put("memcacheEnabled", memcacheEnabled);
    view.put("messagingTransport", transport);
    view.put("analyticsEnabled", analyticsEnabled);
    view.put("cdcEnabled", cdcEnabled);
    view.put("cdcConnected", gauge("aiplatform.cdc.connected"));
    view.put("analyticsConsumerRunning", gauge("aiplatform.analytics.consumer.running"));
    view.put("analyticsConsumerLag", gauge("aiplatform.analytics.consumer.lag"));
    view.put("outboxPending", gauge("aiplatform.outbox.backlog", "state", "PENDING"));
    view.put("sampledAt", System.currentTimeMillis());
    return view;
  }

  @Override
  public CacheTraceDTO traceCache(long applicationId) {
    requireEnabled();
    CacheTraceDTO view = new CacheTraceDTO();
    view.setApplicationId(applicationId);
    List<CacheTraceDTO.ReadSample> samples = new ArrayList<>();
    // 同一请求作用域内读取两次，观察请求内复用与真实缓存层，不主动清空缓存。
    try (RequestCacheScope scope = RequestCacheScope.open()) {
      for (int index = 0; index < 2; index++) {
        try (CacheReadTrace trace = CacheReadTrace.open()) {
          long started = System.nanoTime();
          ApplicationDetailDTO detail = applications.detail(applicationId).getData();
          view.setApplicationName(detail == null ? null : detail.getName());
          CacheTraceDTO.ReadSample sample = new CacheTraceDTO.ReadSample();
          sample.setSequence(index + 1);
          sample.setDurationMicros(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - started));
          sample.setEvents(trace.snapshot());
          samples.add(sample);
        }
      }
    }
    view.setReads(samples);
    return view;
  }

  @Override
  public OrderProgressDTO orderProgress(long userId, long orderId) {
    requireEnabled();
    PurchaseOrderDTO order = orders.detail(userId, orderId);
    CampaignReservation reservation = order.getCampaignId() == null
        ? null : reservations.selectReservation(orderId);
    return OrderProgressDTO.of(order, reservation,
        outbox.selectOrderTasks(orderId));
  }

  private void requireEnabled() {
    if (!enabled) {
      // 关闭时对外表现为不存在，避免暴露内部诊断入口。
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
  }

  private Double gauge(String name, String... tags) {
    Gauge gauge = metrics.find(name).tags(tags).gauge();
    if (gauge == null) {
      return null;
    }
    double value = gauge.value();
    return Double.isFinite(value) ? value : null;
  }
}
