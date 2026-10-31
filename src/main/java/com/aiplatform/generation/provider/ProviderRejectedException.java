package com.aiplatform.generation.provider;

/** 上游明确拒收，未形成上游任务；这是可确认的失败事实而不是超时。 */
public class ProviderRejectedException extends RuntimeException {
  private final String code;

  public ProviderRejectedException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
