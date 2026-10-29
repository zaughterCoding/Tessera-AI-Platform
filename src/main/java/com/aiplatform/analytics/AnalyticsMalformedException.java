package com.aiplatform.analytics;

/** 无法解析或超出允许范围的内部消息；按位点隔离，不进入计数。 */
public class AnalyticsMalformedException extends RuntimeException {
  public AnalyticsMalformedException(String message) {
    super(message);
  }
}
