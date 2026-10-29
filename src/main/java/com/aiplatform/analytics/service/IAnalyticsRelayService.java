package com.aiplatform.analytics.service;

import com.aiplatform.analytics.entity.AnalyticsOutboxEvent;
import java.util.List;

/** 分析投递任务的人工复核与观测。 */
public interface IAnalyticsRelayService {
  void relayPending();

  List<AnalyticsOutboxEvent> listTasks(String state, int limit, int offset);

  AnalyticsOutboxEvent getTask(String eventId);

  boolean retryReview(String eventId);

  long countPending();
}
