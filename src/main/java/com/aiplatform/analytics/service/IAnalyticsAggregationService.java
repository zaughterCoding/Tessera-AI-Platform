package com.aiplatform.analytics.service;

import com.aiplatform.analytics.AnalyticsEvent;

/** 单条消息的幂等聚合；去重与计数在同一数据库事务内完成。 */
public interface IAnalyticsAggregationService {
  void accept(AnalyticsEvent event);
}
