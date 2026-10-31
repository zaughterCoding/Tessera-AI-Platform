package com.aiplatform.generation;

/** 取消请求的持久状态；渠道不支持或上游已成功时不得伪装成取消成功。 */
public final class GenerationCancelState {
  public static final String PENDING = "PENDING";
  public static final String RETRYING = "RETRYING";
  public static final String RESOLVED = "RESOLVED";
  public static final String REJECTED = "REJECTED";
  public static final String UNSUPPORTED = "UNSUPPORTED";

  private GenerationCancelState() {
  }

  public static boolean isOpen(String state) {
    return PENDING.equals(state) || RETRYING.equals(state);
  }
}
