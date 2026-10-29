package com.aiplatform.analytics.service.impl;

import com.aiplatform.analytics.AnalyticsProperties;
import com.aiplatform.analytics.dto.ApplicationActivityDTO;
import com.aiplatform.analytics.dto.PopularityItemDTO;
import com.aiplatform.analytics.entity.AnalyticsOverviewRow;
import com.aiplatform.analytics.entity.PopularityRow;
import com.aiplatform.analytics.mapper.AnalyticsEventMapper;
import com.aiplatform.analytics.service.IAnalyticsQueryService;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 窗口只支持 5 与 60 分钟；计数来自持久分钟桶，不依赖缓存快照。 */
@Service
public class AnalyticsQueryServiceImpl implements IAnalyticsQueryService {
  private static final long MINUTE_MS = 60000L;
  private static final int MAX_LIMIT = 50;

  private final AnalyticsProperties properties;
  private final AnalyticsEventMapper events;

  public AnalyticsQueryServiceImpl(AnalyticsProperties properties, AnalyticsEventMapper events) {
    this.properties = properties;
    this.events = events;
  }

  @Override
  public ApplicationActivityDTO overview(long applicationId, int minutes) {
    Window window = window(minutes);
    String name = events.selectApplicationName(applicationId);
    if (name == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    AnalyticsOverviewRow totals = events.selectBucketTotals(applicationId,
        window.from, window.to);
    long visits = value(totals == null ? null : totals.getVisits());
    long orders = value(totals == null ? null : totals.getOrders());
    long payments = value(totals == null ? null : totals.getPayments());
    long claims = value(totals == null ? null : totals.getClaims());
    long closed = value(totals == null ? null : totals.getClosedOrders());
    long uv = events.selectUv(applicationId, window.from, window.to);
    ApplicationActivityDTO dto = new ApplicationActivityDTO();
    dto.setApplicationId(applicationId);
    dto.setApplicationName(name);
    dto.setWindowMinutes(minutes);
    dto.setWindowFrom(Instant.ofEpochMilli(window.from).toString());
    dto.setWindowTo(Instant.ofEpochMilli(window.to).toString());
    dto.setVisits(visits);
    dto.setUv(uv);
    dto.setOrders(orders);
    dto.setPayments(payments);
    dto.setClaims(claims);
    dto.setClosedOrders(closed);
    dto.setHeat(heat(visits, uv, orders, payments, claims));
    return dto;
  }

  @Override
  public List<PopularityItemDTO> popularity(int minutes, int limit) {
    Window window = window(minutes);
    int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
    List<PopularityRow> rows = events.selectPopularity(window.from, window.to,
        properties.getHeatVisitWeight(), properties.getHeatUvWeight(),
        properties.getHeatOrderWeight(), properties.getHeatPaymentWeight(),
        properties.getHeatClaimWeight(), safeLimit);
    List<PopularityItemDTO> items = new ArrayList<>();
    int rank = 1;
    for (PopularityRow row : rows) {
      items.add(PopularityItemDTO.from(rank++, row));
    }
    return items;
  }

  private long heat(long visits, long uv, long orders, long payments, long claims) {
    return visits * properties.getHeatVisitWeight()
        + uv * properties.getHeatUvWeight()
        + orders * properties.getHeatOrderWeight()
        + payments * properties.getHeatPaymentWeight()
        + claims * properties.getHeatClaimWeight();
  }

  private Window window(int minutes) {
    if (minutes != 5 && minutes != 60) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    long now = System.currentTimeMillis();
    long currentMinute = now - Math.floorMod(now, MINUTE_MS);
    long from = currentMinute - (minutes - 1) * MINUTE_MS;
    return new Window(from, currentMinute + MINUTE_MS);
  }

  private long value(Long value) {
    return value == null ? 0L : value;
  }

  private static final class Window {
    private final long from;
    private final long to;

    private Window(long from, long to) {
      this.from = from;
      this.to = to;
    }
  }
}
