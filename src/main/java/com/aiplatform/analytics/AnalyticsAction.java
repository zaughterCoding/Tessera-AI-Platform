package com.aiplatform.analytics;

/** 分析事件动作；订单类动作只能由本地订单事实登记后产生。 */
public enum AnalyticsAction {
  VISIT,
  ORDER,
  PAID,
  CLAIM,
  CLOSED;

  /** 映射订单任务类型；非订单任务返回 null，不进入分析。 */
  public static AnalyticsAction fromOrderKind(String kind) {
    if ("ORDER_CREATED".equals(kind)) {
      return ORDER;
    }
    if ("ORDER_PAID".equals(kind)) {
      return PAID;
    }
    if ("ORDER_CLAIMED".equals(kind)) {
      return CLAIM;
    }
    if ("ORDER_CLOSED".equals(kind)) {
      return CLOSED;
    }
    return null;
  }

  public boolean isOrderFact() {
    return this != VISIT;
  }
}
