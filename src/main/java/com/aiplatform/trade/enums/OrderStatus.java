package com.aiplatform.trade.enums;

public enum OrderStatus {
  UNPAID,
  PAID,
  CANCELLED;

  public boolean matches(String value) {
    return name().equals(value);
  }
}
