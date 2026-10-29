package com.aiplatform.analytics.transport;

import com.aiplatform.analytics.AnalyticsEvent;

/** 行为消息的发送边界；浏览尽力发送，订单事实复用持久任务等待确认。 */
public interface AnalyticsTransport {
  void submitVisit(AnalyticsEvent event);

  void publishAndAwait(AnalyticsEvent event);
}
