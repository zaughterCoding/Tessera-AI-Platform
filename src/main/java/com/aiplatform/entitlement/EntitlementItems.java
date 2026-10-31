package com.aiplatform.entitlement;

/** 当前订单快照只包含一个次数权益项；字段保留稳定身份，便于后续扩展其他项。 */
public final class EntitlementItems {
  public static final String CREDITS = "credits";

  private EntitlementItems() {}
}
