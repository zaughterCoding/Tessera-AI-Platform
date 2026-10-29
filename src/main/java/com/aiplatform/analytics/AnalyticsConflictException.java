package com.aiplatform.analytics;

/** 同一事件号对应不同内容，或订单动作缺少本地事实登记。 */
public class AnalyticsConflictException extends RuntimeException {
  public AnalyticsConflictException(String message) {
    super(message);
  }
}
