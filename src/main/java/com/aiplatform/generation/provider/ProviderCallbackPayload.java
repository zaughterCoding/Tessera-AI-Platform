package com.aiplatform.generation.provider;

/** 回调只提交上游事件事实，不包含用户、余额、任务状态或额度字段。 */
public record ProviderCallbackPayload(
    String eventKey,
    String eventType,
    String attemptKey,
    String providerTaskId,
    String resultRef,
    String resultDigest,
    String failureCode,
    String failureDetail) {
}
