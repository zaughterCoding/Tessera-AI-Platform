package com.aiplatform.analytics;

import lombok.Data;

/** 内部行为消息；事件号稳定，消费端据此去重，重放不会重复计数。 */
@Data
public class AnalyticsEvent {
  public static final int SCHEMA_VERSION = 1;
  private static final long MINUTE_MS = 60000L;
  private int schemaVersion = SCHEMA_VERSION;
  private String eventId;
  private Long applicationId;
  private Long userId;
  private AnalyticsAction action;
  private long occurredAt;

  public static AnalyticsEvent visit(String eventId, long applicationId, Long userId, long at) {
    return of(eventId, applicationId, userId, AnalyticsAction.VISIT, at);
  }

  public static AnalyticsEvent order(
      String eventId, long applicationId, long userId, AnalyticsAction action, long at) {
    return of(eventId, applicationId, userId, action, at);
  }

  private static AnalyticsEvent of(
      String eventId, long applicationId, Long userId, AnalyticsAction action, long at) {
    AnalyticsEvent event = new AnalyticsEvent();
    event.setEventId(eventId);
    event.setApplicationId(applicationId);
    event.setUserId(userId);
    event.setAction(action);
    event.setOccurredAt(at);
    return event;
  }

  /** 只接受内部协议；时间超出保留期或时钟偏差过大的事件按异常隔离。 */
  public void validate(long now, long retentionMs, long maxSkewMs) {
    if (schemaVersion != SCHEMA_VERSION || action == null) {
      throw new AnalyticsMalformedException("分析事件版本或动作无效");
    }
    if (eventId == null || !eventId.matches("[A-Za-z0-9:._-]{1,64}")) {
      throw new AnalyticsMalformedException("分析事件号无效");
    }
    if (applicationId == null || applicationId <= 0
        || (userId != null && userId <= 0)) {
      throw new AnalyticsMalformedException("分析事件归属无效");
    }
    if (action.isOrderFact() && userId == null) {
      throw new AnalyticsMalformedException("订单分析事件缺少用户归属");
    }
    if (occurredAt <= 0 || occurredAt > now + maxSkewMs) {
      throw new AnalyticsMalformedException("分析事件时间超出允许范围");
    }
    if (occurredAt < now - retentionMs) {
      throw new AnalyticsMalformedException("分析事件已超出保留期，不能重放计数");
    }
  }

  public long bucketMs() {
    return occurredAt - Math.floorMod(occurredAt, MINUTE_MS);
  }
}
