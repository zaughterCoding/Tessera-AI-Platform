package com.aiplatform.entitlement;

/** 来源订单、支付事实或快照不可信；调用方决定隔离或记录异常。 */
public class EntitlementSourceException extends IllegalArgumentException {
  private final String anomalyType;

  public EntitlementSourceException(String anomalyType, String message) {
    super(message);
    this.anomalyType = anomalyType;
  }

  public String getAnomalyType() {
    return anomalyType;
  }
}
