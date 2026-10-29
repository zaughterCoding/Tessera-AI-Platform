package com.aiplatform.analytics.service;

import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.trade.entity.PurchaseOrder;

/** 分析事实登记入口；订单写入与浏览采集使用不同的可靠性边界。 */
public interface IAnalyticsJournalService {
  void recordVisit(long applicationId, Long userId);

  void appendOrderFact(PurchaseOrder order, BusinessOutboxTask task);
}
