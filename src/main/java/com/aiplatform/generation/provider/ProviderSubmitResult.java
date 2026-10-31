package com.aiplatform.generation.provider;

/** 已确认受理时的上游身份与初始状态；拒绝通过异常表达。 */
public record ProviderSubmitResult(String state, String providerTaskId) {
}
