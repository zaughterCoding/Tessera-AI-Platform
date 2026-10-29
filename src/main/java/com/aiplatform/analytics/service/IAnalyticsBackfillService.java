package com.aiplatform.analytics.service;

import com.aiplatform.analytics.entity.AnalyticsBackfillState;

/** 从订单事件历史补数；游标持久化，允许中断续跑与安全重跑。 */
public interface IAnalyticsBackfillService {
  void runBatch();

  void startOnBoot();

  AnalyticsBackfillState state();

  boolean restart();
}
