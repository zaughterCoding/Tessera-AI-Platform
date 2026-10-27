package com.aiplatform.messaging.enums;

public enum OutboxState {
  PENDING,
  SENDING,
  SENT,
  DONE,
  REVIEW;

  public boolean matches(String state) {
    return name().equals(state);
  }
}
