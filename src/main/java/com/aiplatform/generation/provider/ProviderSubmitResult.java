package com.aiplatform.generation.provider;

/** 已确认受理时的上游身份与初始状态；拒绝通过异常表达。同步渠道用 immediate 带回已完成的结果。 */
public record ProviderSubmitResult(String state, String providerTaskId,
    ProviderQueryResult immediate) {

  public ProviderSubmitResult(String state, String providerTaskId) {
    this(state, providerTaskId, null);
  }
}
