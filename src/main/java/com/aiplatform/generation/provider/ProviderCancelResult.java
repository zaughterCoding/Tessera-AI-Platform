package com.aiplatform.generation.provider;

/** 取消契约只用于验证渠道能力；当前不向用户开放取消状态机。 */
public record ProviderCancelResult(CancelStatus status, String providerState) {

  public enum CancelStatus {
    CANCELLED,
    UNSUPPORTED,
    TERMINAL,
    NOT_FOUND
  }
}
