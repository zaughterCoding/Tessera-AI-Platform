package com.aiplatform.messaging;

/** 只保留异常类型和简短原因，不记录消息载荷、凭据或用户资料。 */
public final class FailureMessages {
  private static final int MAX_LENGTH = 500;
  private static final int MAX_PAYLOAD = 65535;

  private FailureMessages() {
  }

  public static String summarize(Throwable failure) {
    if (failure == null) {
      return "unknown";
    }
    String message = failure.getMessage();
    String text = failure.getClass().getSimpleName()
        + (message == null || message.isEmpty() ? "" : ": " + message);
    return truncate(text, MAX_LENGTH);
  }

  public static String truncate(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  public static String payload(String value) {
    return truncate(value, MAX_PAYLOAD);
  }
}
